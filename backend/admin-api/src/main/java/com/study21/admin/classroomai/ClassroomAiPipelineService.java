package com.study21.admin.classroomai;

import com.study21.admin.batch.BatchService;
import com.study21.common.core.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授業ノート生成の**起動の薄い入口**（画面は 1 回だけ呼ぶ）。
 *
 * <p>ノート行（`CR_授業ノート情報`）の生成状態=PENDING を、その種別に応じたバッチ
 * （PHASE→batC61 / FINAL→batC62）で処理する。user-api が作った PENDING 行を
 * `BatchService.rerunStep` で 1 件だけ処理する（`GeometryAiBatchController` 相当）。</p>
 *
 * <p><b>2026-09-19 改修（第 5 段）: 同期実行から「受理 + 背景実行」へ</b>。以前はここで AI を
 * 最後まで走らせていた（画面は数百秒待ち、待ち切れないと**起動できたかどうか**も分からなかった）。
 * いまは</p>
 * <ol>
 *   <li>**受理**（条件つき更新で `PENDING`/`FAILED` → `GENERATING`。1 文なので同時に 2 本来ても
 *       勝つのは 1 本）、</li>
 *   <li>**背景で実行**（結果は `READY`/`FAILED` として行に残る）、</li>
 *   <li>画面は `GET /classroom/{id}` のポーリングで**本当の状態**を読む、</li>
 * </ol>
 * <p>という形にする。受理の応答を失っても、もう一度同じ入口を叩けば「既に走っている」が返り、
 * **2 つ目のタスクは作らない**。実行を始められなかった回は**やり直せる失敗**として記録に残し
 * （`GENERATING` のまま放置しない）、失われた実行は**実行記録で確かめたうえで**引き取る
 * （経過時間だけで「死んだ」と決めない）。</p>
 */
@Service
public class ClassroomAiPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiPipelineService.class);

    private static final String OPERATOR_FALLBACK = "classroom-ai";

    /** 受理の結果（画面が「始まったか・既に走っているか・済んでいるか」を判断できる形）。 */
    public record Acceptance(
            long noteId,
            /** この受理で背景の実行を始めたか（false なら既に走っている・既に済んでいる）。 */
            boolean accepted,
            /** 受理のあとの生成状態（`GENERATING` / `READY` / `FAILED`）。 */
            String status,
            /** 画面に出す短い説明（日本語）。 */
            String message) {
    }

    /**
     * 受理したが**実行を始められなかった**ときに、記録へ残すエラーコード。
     *
     * <p>画面はこのコードを見て「もう一度押せばやり直せる」と出す。`GENERATING` のまま
     * 放置しない（利用者には「作成中」に見え続けて、終わらない）。</p>
     */
    public static final String CODE_BUSY = "NOTE_ENGINE_BUSY";
    public static final String CODE_FAILED = "NOTE_ENGINE_FAILED";

    /** 「まだ新しい」とみなす猶予。これより古い `GENERATING` は**実行記録を見て**判断する。 */
    private static final Duration STALE_GENERATION = Duration.ofMinutes(30);

    private final BatchService batchService;
    private final ClassroomNoteMapper noteMapper;
    /*
     * 実行の生存は `ClassroomNoteMapper.findGenerationExecution`（**そのノートに紐づいた実行**だけを
     * 引く 1 文）で確かめる。`BatchExecutionMapper` の「バッチコードで走っている実行」を使うと
     * **別の授業の実行**を自分の実行と取り違えるので使わない。
     */
    /** このプロセスの起動識別子（**別プロセスの実行**を遺留と判定するのに使う）。 */
    private final com.study21.admin.batch.ProcessRunId processRunId;

    public ClassroomAiPipelineService(BatchService batchService, ClassroomNoteMapper noteMapper,
                                      com.study21.admin.batch.ProcessRunId processRunId) {
        this.batchService = batchService;
        this.noteMapper = noteMapper;
        this.processRunId = processRunId;
    }

    /**
     * 生成の起動を**受理する**（AI の完了は待たない）。
     *
     * @param noteId   対象のノート行（user-api が作った PENDING の行）
     * @param operator 操作者（呼出履歴に残す）
     */
    public Acceptance accept(long noteId, String operator) {
        ClassroomNoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new NotFoundException("授業ノートが見つかりません: " + noteId);
        }
        if ("READY".equals(note.getStatus())) {
            // **すでにできている**: AI をもう一度呼ばない（従量課金の無駄・結果を書き換えない）
            return new Acceptance(noteId, false, "READY", "この最終まとめはすでに作成済みです。");
        }
        // この受理の**試行の識別子**（遅れて返った古い試行の書き込みを捨てる照合に使う）
        String token = tokenOf(noteId);
        if (noteMapper.claimGeneration(noteId, token) == 1) {
            return start(noteId, operator, token, "最終まとめの作成を始めました。");
        }
        ClassroomNoteEntity current = noteMapper.findById(noteId);
        String status = current == null ? null : current.getStatus();
        if ("GENERATING".equals(status) && livenessOf(current) == Liveness.LOST) {
            /*
             * **実行が本当に生きているかを実行記録で確かめた**うえで、失われていれば引き取る。
             * 経過時間だけで「死んだ」と決めない（長い AI 呼び出しを二重に走らせない）。
             */
            if (noteMapper.reclaimStaleGeneration(noteId, staleBefore(), token) == 1) {
                log.warn("lost classroom note generation reclaimed. noteId={} startedAt={}",
                        noteId, current.getGenerationStartedAt());
                return start(noteId, operator, token,
                        "前回の作成が途中で止まっていたため、もう一度始めました。");
            }
        }
        if ("GENERATING".equals(status)) {
            return new Acceptance(noteId, false, "GENERATING", "この最終まとめは作成中です。");
        }
        if ("READY".equals(status)) {
            return new Acceptance(noteId, false, "READY", "この最終まとめはすでに作成済みです。");
        }
        return new Acceptance(noteId, false, status == null ? "UNKNOWN" : status,
                "この最終まとめは今は作成できません（状態: " + status + "）。");
    }

    /**
     * 受理したあとの**実行**（背景）。受理の応答は既に返している（AI の完了は待たせない）。
     *
     * <p>どの失敗位置でも**記録にけりを付ける**（`GENERATING` のまま放置しない）:</p>
     * <ol>
     *   <li>バッチが忙しくて断られた（`ConflictException`）→ **やり直せる失敗**（`NOTE_ENGINE_BUSY`）、</li>
     *   <li>設定不足・未実装など（`ValidationException` / `NotFoundException`）→ 失敗（`NOTE_ENGINE_FAILED`）、</li>
     *   <li>その他の例外 → 失敗（`NOTE_ENGINE_FAILED`）、</li>
     *   <li>実行が失敗・スキップで終わった（`success=false` / `skipped`）→ 失敗（理由つき）。</li>
     * </ol>
     * <p>いずれも**この試行のトークンのときだけ**書く（古い試行の遅い書き込みで新しい試行を壊さない）。</p>
     */
    private Acceptance start(long noteId, String operator, String token, String message) {
        /*
         * **受理を返してから**背景で走らせる（ここで長い AI を待たない）。
         * 実行器の起動そのものに失敗したら、その場で失敗として記録する（受理の応答は既に返している）。
         */
        runInBackground(noteId, operator, token);
        return new Acceptance(noteId, true, "GENERATING", message);
    }

    /**
     * 背景で 1 件を処理する（**応答を待たせない**）。
     *
     * <p>例外はここで握り、**記録にやり直せる失敗として残す**（catch してログだけ、にしない）。</p>
     */
    private void runInBackground(long noteId, String operator, String token) {
        Runnable task = () -> {
            try {
                Map<String, Object> result = runNow(noteId, operator, token);
                if (!isSuccessful(result)) {
                    // 実行はされたが**失敗・スキップ**で終わった（状態は既に note 側にも書かれている）
                    log.warn("classroom ai pipeline did not finish. noteId={} status={} message={}",
                            noteId, result.get("status"), result.get("message"));
                    markGenerationFailed(noteId, token,
                            Boolean.TRUE.equals(result.get("skipped")) ? CODE_BUSY : CODE_FAILED,
                            String.valueOf(result.get("message")));
                }
            } catch (com.study21.common.core.exception.ConflictException busy) {
                // 同じバッチが実行中（別の授業が走っている）: **やり直せる失敗**として残す
                log.warn("classroom note generation refused because the batch is busy. noteId={} reason={}",
                        noteId, busy.getMessage());
                markGenerationFailed(noteId, token, CODE_BUSY, busy.getMessage());
            } catch (RuntimeException cause) {
                log.error("classroom ai pipeline failed in background. noteId={}", noteId, cause);
                markGenerationFailed(noteId, token, CODE_FAILED,
                        cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
        };
        try {
            Thread.startVirtualThread(task);
        } catch (RuntimeException cause) {
            /*
             * **実行器の起動そのものに失敗**した（仮想スレッドが使えない等）。
             * ここで黙ると「受理したのに何も走らない」＝永久に `GENERATING`。失敗として残して
             * やり直せるようにする（**長い AI をその場で走らせては戻らない**）。
             */
            log.error("classroom note generation could not be submitted. noteId={}", noteId, cause);
            markGenerationFailed(noteId, token, CODE_FAILED,
                    "生成の実行を開始できませんでした（" + cause.getClass().getSimpleName() + "）。");
        }
    }

    /** 実行の生存の判定（**分からない**を「死んだ」と決めない）。 */
    public enum Liveness {
        /** **このまとめの実行が生きている**（実行記録が `QUEUED`/`RUNNING` で、このプロセスのもの）。 */
        RUNNING,
        /**
         * 受理したが**実行記録がまだ無い**（実行の準備中）。
         *
         * <p>正常な短い窓なので**回収しない**。ただし長く続くなら失联とみなす。</p>
         */
        BINDING,
        /** 実行記録が終わっている・消えている（**失联**）。 */
        LOST,
        /** 実行記録を**確かめられなかった**（DB エラーなど）。**回収しない**。 */
        UNKNOWN
    }

    /** 1 つのまとめの回復の資格（画面に出す形）。 */
    public record RecoveryView(
            long noteId,
            /** いまの生成状態。 */
            String status,
            /** 実行の生存の判定。 */
            String liveness,
            /** 回復（やり直し）の入口を出してよいか。 */
            boolean recoverable,
            /** 実行を再開したか（この呼び出しで回復したとき true）。 */
            boolean recovered,
            /** 画面に出す理由（日本語）。 */
            String reason) {
    }

    /**
     * **このまとめの実行が生きているか**を、**その実行記録**で確かめる。
     *
     * <p>見る順:</p>
     * <ol>
     *   <li>ノートに紐づいた実行記録（`生成実行ID`）を引く。**別の授業の実行は見ない**。</li>
     *   <li>実行記録が `QUEUED`/`RUNNING` で、**このプロセスの起動識別子**なら生きている。
     *       別プロセスの実行なら**失联**（そのプロセスはもう居ない）。</li>
     *   <li>実行記録が終わっている（SUCCESS/FAILED/SKIPPED）なら、ノートの状態だけが
     *       取り残されている＝**失联**。</li>
     *   <li>`生成実行ID` がまだ無い（受理直後の短い窓）… 開始から猶予内なら**準備中**として
     *       回収しない。猶予を過ぎていれば失联。</li>
     *   <li>確かめられなかった（例外）… **分からない**として回収しない。</li>
     * </ol>
     */
    public Liveness livenessOf(ClassroomNoteEntity note) {
        if (note == null || !"GENERATING".equals(note.getStatus())) {
            return Liveness.RUNNING;
        }
        Long executionId = note.getGenerationExecutionId();
        if (executionId == null) {
            // 実行記録がまだ無い（受理直後の短い窓）。猶予を過ぎていれば失联
            return isWithinBindingGrace(note) ? Liveness.BINDING : Liveness.LOST;
        }
        try {
            com.study21.admin.batch.BatchExecutionEntity execution =
                    noteMapper.findGenerationExecution(note.getNoteId(), executionId);
            if (execution == null) {
                // 実行記録が消えている（掃除・ロールバック）。**失联**
                return Liveness.LOST;
            }
            String status = execution.getStatus();
            boolean active = "QUEUED".equals(status) || "RUNNING".equals(status);
            if (!active) {
                // 実行は終わっているのにノートが GENERATING のまま＝**取り残し**
                return Liveness.LOST;
            }
            String runId = processRunId.value();
            if (execution.getRunId() != null && runId != null && !runId.equals(execution.getRunId())) {
                // **別のプロセスの実行**（サーバーが再起動した）。そのプロセスはもう居ない
                return Liveness.LOST;
            }
            return Liveness.RUNNING;
        } catch (RuntimeException cause) {
            log.warn("could not check the generation execution. noteId={} executionId={}",
                    note.getNoteId(), executionId, cause);
            // **確かめられない**を「死んだ」と決めない（生きている実行を二重に走らせない）
            return Liveness.UNKNOWN;
        }
    }

    /** 受理直後の「実行記録がまだ無い」窓の猶予（この間は回収しない）。 */
    private static boolean isWithinBindingGrace(ClassroomNoteEntity note) {
        Timestamp startedAt = note.getGenerationStartedAt();
        if (startedAt == null) {
            return false;
        }
        return startedAt.toInstant().isAfter(Instant.now().minus(BINDING_GRACE));
    }

    /**
     * **1 つのまとめの回復の資格**を返し、失联していればやり直せる失敗に戻す。
     *
     * <p>画面はこれを呼んで「状態を確認／復旧」を出す（**前端が勝手に「失联」と断言しない**）。</p>
     */
    public RecoveryView recoverOne(long noteId) {
        ClassroomNoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new NotFoundException("授業ノートが見つかりません: " + noteId);
        }
        String status = note.getStatus() == null ? "PENDING" : note.getStatus();
        if (!"GENERATING".equals(status)) {
            return new RecoveryView(noteId, status, Liveness.RUNNING.name(), false, false,
                    "この最終まとめは実行中ではありません（状態: " + status + "）。");
        }
        Liveness liveness = livenessOf(note);
        switch (liveness) {
            case RUNNING -> {
                return new RecoveryView(noteId, status, liveness.name(), false, false,
                        "この最終まとめは実行中です（このままお待ちください）。");
            }
            case BINDING -> {
                return new RecoveryView(noteId, status, liveness.name(), false, false,
                        "この最終まとめの実行を準備しています（少し待ってからもう一度お試しください）。");
            }
            case UNKNOWN -> {
                return new RecoveryView(noteId, status, liveness.name(), false, false,
                        "実行の状態を確認できませんでした（少し待ってからもう一度お試しください）。");
            }
            case LOST -> {
                // **失联を確認**した: やり直せる失敗に戻す（**旧試行のトークンで条件つき**）
                int updated = noteMapper.markGenerationFailed(noteId, CODE_BUSY,
                        "生成の実行が失われていました（サーバーの再起動など）。もう一度実行してください。",
                        note.getGenerationToken());
                if (updated == 1) {
                    log.warn("lost classroom note generation recovered. noteId={} executionId={} token={}",
                            noteId, note.getGenerationExecutionId(), note.getGenerationToken());
                    return new RecoveryView(noteId, "FAILED", liveness.name(), true, true,
                            "実行が失われていたため、やり直せる状態に戻しました。");
                }
                // 既に別の試行が状態を進めている（触らない）
                ClassroomNoteEntity fresh = noteMapper.findById(noteId);
                String freshStatus = fresh == null ? status : fresh.getStatus();
                return new RecoveryView(noteId, freshStatus, liveness.name(), false, false,
                        "この最終まとめは既に別の操作で進んでいます（状態: " + freshStatus + "）。");
            }
            default -> {
                return new RecoveryView(noteId, status, liveness.name(), false, false,
                        "この最終まとめの状態を確認できませんでした。");
            }
        }
    }

    /**
     * **失われた実行を一括で回復する**（管理者向け。既存の実行記録で生存を確かめてから）。
     *
     * <p>実行記録が生きているもの・**確かめられなかったもの**は触らない（一括リセットしない）。</p>
     */
    public java.util.List<Long> recoverLostGenerations(int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 500);
        java.util.List<Long> recovered = new java.util.ArrayList<>();
        for (ClassroomNoteEntity candidate : noteMapper.findStaleGenerating(staleBefore(), max)) {
            if (livenessOf(candidate) != Liveness.LOST) {
                // 生きている・準備中・**分からない**は触らない
                continue;
            }
            if (noteMapper.markGenerationFailed(candidate.getNoteId(), CODE_BUSY,
                    "生成の実行が失われていました（サーバーの再起動など）。もう一度実行してください。",
                    candidate.getGenerationToken()) == 1) {
                recovered.add(candidate.getNoteId());
                log.warn("lost classroom note generation recovered. noteId={} startedAt={} executionId={}",
                        candidate.getNoteId(), candidate.getGenerationStartedAt(),
                        candidate.getGenerationExecutionId());
            }
        }
        return recovered;
    }

    /** 「失われた」とみなす猶予（分）。実行記録が無いままこれを過ぎたら回復する。 */
    public static final int STALE_GENERATION_MINUTES = 30;

    /** 受理直後の「実行記録がまだ無い」窓の猶予（この間は回収しない）。 */
    private static final Duration BINDING_GRACE = Duration.ofMinutes(STALE_GENERATION_MINUTES);

    /** 受理したのに実行できなかったことを**やり直せる失敗**として書く。 */
    private void markGenerationFailed(long noteId, String token, String code, String message) {
        try {
            String reason = message == null || message.isBlank() ? "生成を実行できませんでした。" : message;
            int updated = noteMapper.markGenerationFailed(noteId, code,
                    reason.substring(0, Math.min(500, reason.length())), token);
            if (updated == 0) {
                // 既に別の試行が状態を進めている（READY を含む）。**触らない**
                log.info("classroom note generation failure was not recorded (state already moved)."
                        + " noteId={} code={}", noteId, code);
            }
        } catch (RuntimeException cause) {
            log.error("could not record the classroom note generation failure. noteId={}", noteId, cause);
        }
    }

    /** 1 件のノートをその種別のバッチで生成する（**同期**。背景から呼ぶ）。 */
    public Map<String, Object> runNow(long noteId, String operator, String token) {
        ClassroomNoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new NotFoundException("授業ノートが見つかりません: " + noteId);
        }
        String batchCode = "FINAL".equals(note.getKind()) ? "batC62" : "batC61";
        String operatorCode = operator == null || operator.isBlank() ? OPERATOR_FALLBACK : operator.trim();

        Map<String, Object> stepResult = batchService.rerunStep(batchCode, operatorCode,
                payloadOf(noteId, token));
        ClassroomNoteEntity saved = noteMapper.findById(noteId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noteId", noteId);
        result.put("kind", note.getKind());
        result.put("batchCode", batchCode);
        result.put("status", saved == null ? null : saved.getStatus());
        result.put("step", stepResult);
        // 実行の結果を**この階層にも写す**（呼び側が「失敗・スキップ」を見分けられるように）
        if (stepResult != null) {
            result.put("success", stepResult.get("success"));
            result.put("skipped", Boolean.TRUE.equals(stepResult.get("skipped")));
            result.put("message", stepResult.get("message"));
        }
        log.info("classroom ai pipeline finished. noteId={} kind={} status={}",
                noteId, note.getKind(), saved == null ? null : saved.getStatus());
        return result;
    }

    /** 実行が成功として終わったか（失敗・スキップは false）。 */
    private static boolean isSuccessful(Map<String, Object> result) {
        if (result == null) {
            return false;
        }
        if (Boolean.TRUE.equals(result.get("skipped"))) {
            return false;
        }
        Object step = result.get("step");
        if (step instanceof Map<?, ?> map && Boolean.FALSE.equals(map.get("success"))) {
            return false;
        }
        return !Boolean.FALSE.equals(result.get("success"));
    }

    /** 「落ちた」とみなす境目（`GENERATING` の開始から一定時間より前）。 */
    private static Timestamp staleBefore() {
        return Timestamp.from(Instant.now().minus(STALE_GENERATION));
    }

    /** この受理の試行の識別子（ノートごとに一意）。 */
    private static String tokenOf(long noteId) {
        return noteId + "-" + Long.toString(System.nanoTime(), 36);
    }

    private static String payloadOf(long noteId, String token) {
        // **試行のトークン**も渡す（工程はこれを照合して、古い試行の書き込みを捨てる）
        return "{\"noteId\":" + noteId + ",\"token\":\"" + token + "\"}";
    }
}
