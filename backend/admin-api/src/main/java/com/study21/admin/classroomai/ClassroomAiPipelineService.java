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
 * **2 つ目のタスクは作らない**。前に走らせたまま落ちた `GENERATING`（一定時間より古い）は
 * もう一度受理してやり直せる（永久に「生成中」で止めない）。</p>
 */
@Service
public class ClassroomAiPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiPipelineService.class);

    private static final String OPERATOR_FALLBACK = "classroom-ai";
    /** 前回の開始からこれだけたった `GENERATING` は「落ちた」とみなしてやり直す。 */
    private static final Duration STALE_GENERATION = Duration.ofMinutes(30);

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

    private final BatchService batchService;
    private final ClassroomNoteMapper noteMapper;

    public ClassroomAiPipelineService(BatchService batchService, ClassroomNoteMapper noteMapper) {
        this.batchService = batchService;
        this.noteMapper = noteMapper;
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
        /*
         * **受理**する。0 行なら「別の要求が先に始めた」または「すでに終わっている」。
         * どちらも**新しいタスクを作らない**（画面は状態を読み直せばよい）。
         */
        if (noteMapper.claimGeneration(noteId) == 1) {
            runInBackground(noteId, operator);
            return new Acceptance(noteId, true, "GENERATING", "最終まとめの作成を始めました。");
        }
        ClassroomNoteEntity current = noteMapper.findById(noteId);
        String status = current == null ? null : current.getStatus();
        if ("GENERATING".equals(status) && isStale(current)) {
            /*
             * **前に走らせたまま落ちた**回。もう一度受理してやり直す（永久に「生成中」で止めない）。
             * 前回の実行は既に死んでいるので、二重に走る心配は無い。
             */
            if (noteMapper.reclaimStaleGeneration(noteId, staleBefore()) == 1) {
                log.warn("stale classroom note generation reclaimed. noteId={} startedAt={}",
                        noteId, current.getGenerationStartedAt());
                runInBackground(noteId, operator);
                return new Acceptance(noteId, true, "GENERATING",
                        "前回の作成が途中で止まっていたため、もう一度始めました。");
            }
        }
        if ("GENERATING".equals(status)) {
            // **既に走っている**（この受理では何も作らない）。画面はポーリングで結果を読む
            return new Acceptance(noteId, false, "GENERATING", "この最終まとめは作成中です。");
        }
        if ("READY".equals(status)) {
            return new Acceptance(noteId, false, "READY", "この最終まとめはすでに作成済みです。");
        }
        return new Acceptance(noteId, false, status == null ? "UNKNOWN" : status,
                "この最終まとめは今は作成できません（状態: " + status + "）。");
    }

    /**
     * 背景で 1 件を処理する（**応答を待たせない**）。
     *
     * <p>失敗してもノート行に `FAILED` と理由が残る（画面はそれを読んで【再試行】を出す）。
     * 例外はここで握る（背景のスレッドから投げても誰も受け取れない）。</p>
     */
    private void runInBackground(long noteId, String operator) {
        Runnable task = () -> {
            try {
                Map<String, Object> result = runNow(noteId, operator);
                log.info("classroom ai pipeline accepted and finished. noteId={} status={}",
                        noteId, result.get("status"));
            } catch (RuntimeException cause) {
                log.error("classroom ai pipeline failed in background. noteId={}", noteId, cause);
            }
        };
        try {
            Thread.startVirtualThread(task);
        } catch (RuntimeException cause) {
            // 仮想スレッドが使えない環境: 仕組みが無いので**その場で**走らせる（結果は同じ）
            log.debug("仮想スレッドを使えないため、その場で生成します。noteId={}", noteId);
            task.run();
        }
    }

    /** 1 件のノートをその種別のバッチで生成する（**同期**。背景から呼ぶ）。 */
    public Map<String, Object> runNow(long noteId, String operator) {
        ClassroomNoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new NotFoundException("授業ノートが見つかりません: " + noteId);
        }
        String batchCode = "FINAL".equals(note.getKind()) ? "batC62" : "batC61";
        String operatorCode = operator == null || operator.isBlank() ? OPERATOR_FALLBACK : operator.trim();

        Map<String, Object> stepResult = batchService.rerunStep(batchCode, operatorCode, payloadOf(noteId));
        ClassroomNoteEntity saved = noteMapper.findById(noteId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noteId", noteId);
        result.put("kind", note.getKind());
        result.put("batchCode", batchCode);
        result.put("status", saved == null ? null : saved.getStatus());
        result.put("step", stepResult);
        log.info("classroom ai pipeline finished. noteId={} kind={} status={}",
                noteId, note.getKind(), saved == null ? null : saved.getStatus());
        return result;
    }

    /** 「落ちた」とみなす境目（`GENERATING` の開始から一定時間より前）。 */
    private static Timestamp staleBefore() {
        return Timestamp.from(Instant.now().minus(STALE_GENERATION));
    }

    private static boolean isStale(ClassroomNoteEntity note) {
        Timestamp startedAt = note.getGenerationStartedAt();
        // 開始時刻が無い（改修前の行）は「落ちた」とみなす（永久に待たせない）
        return startedAt == null || startedAt.toInstant().isBefore(staleBefore().toInstant());
    }

    private static String payloadOf(long noteId) {
        return "{\"noteId\":" + noteId + "}";
    }
}
