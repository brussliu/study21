package com.study21.user.classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存した分塊から**再生用の 1 本**を組立てる。
 *
 * <p>録音は「1 本の音声として再生できる」必要がある（画面は {@code <audio>} で 1 本を読む）。
 * 一方で保存は分塊ごとのファイルにしてある（追記だと、追記は成功したのに DB の
 * トランザクションが失敗した再送で**同じ音が二重に入る**）。</p>
 *
 * <p><b>2026-09-19 改修（続録の結合）</b>。以前は「2 本目以降のコンテナヘッダを落として
 * 繋ぐ」だけで 1 本を作っていた。これは<b>同じ `MediaRecorder` の続き</b>には成り立つが、
 * 一時停止・画面の開き直しで作り直した**新しいセッション**には成り立たない:
 * 新しいセッションのタイムスタンプは 0 から始まるので、そのまま繋ぐと**2 本目以降の時刻が
 * 最初のセッションへ重なる**（再生位置と書き起こしの時刻が食い違う）。いまは</p>
 * <ol>
 *   <li>{@link ClassroomRecordingSessionPlanner} で**セッションに分け**（DB の行だけを正体にする）、</li>
 *   <li>欠落・実体なし・大きさの食い違いがあれば**結合しない**（{@link ClassroomAssembly.State#INCOMPLETE}）、</li>
 *   <li>セッションが 1 つならそのまま（再多重化は不要）、</li>
 *   <li>2 つ以上なら {@link ClassroomRecordingFfmpeg} で**時刻を積み直してから**繋ぐ。</li>
 * </ol>
 *
 * <p>結合に失敗しても**分塊は 1 つも失われない**（作業ファイルを消し、いまある 1 本は
 * そのまま配信する＝途中までの音。状態は {@link ClassroomAssembly.State#FAILED} で
 * 画面に出し、もう一度試せる）。</p>
 */
@Component
public class ClassroomRecordingAssembler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingAssembler.class);

    /** 同時に組立てないための鍵の数（記録ごとに 1 本。記録 ID で振り分ける）。 */
    private static final int LOCK_STRIPES = 64;
    /** 結合の状態を覚えておく件数（記録ごとに 1 つ。古いものから捨てる）。 */
    private static final int STATUS_RETAIN = 512;
    /** 結合した長さと、書き起こしの時間軸との許容差（秒。符号化の端数のぶんだけ許す）。 */
    static final double DURATION_TOLERANCE_SECONDS = 1.5;

    private final ClassroomRecordingStorage storage;
    private final ClassroomRecordingFfmpeg media;
    private final Object[] locks = new Object[LOCK_STRIPES];
    /** 直近の結合の状態（記録ごと。画面と終了時の判断に使う）。 */
    private final Map<Long, ClassroomAssembly> statuses = new ConcurrentHashMap<>();
    /**
     * **このプロセスが**組立てを始めた記録（再起動後に残った {@code PROCESSING} と区別する）。
     */
    private final java.util.Set<Long> claimedHere = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 本番の生成（Spring が使う口。外部コマンドは設定から作る）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ClassroomRecordingAssembler(ClassroomRecordingStorage storage, ClassroomRecordingFfmpeg media) {
        this.storage = storage;
        this.media = media;
        for (int index = 0; index < locks.length; index += 1) {
            locks[index] = new Object();
        }
    }

    /** 結合の道具を指定しない口（既定の ffmpeg / ffprobe を使う）。 */
    public ClassroomRecordingAssembler(ClassroomRecordingStorage storage) {
        this(storage, new ClassroomRecordingFfmpeg("ffmpeg", "ffprobe"));
    }

    /** 直近の結合の状態（まだ何もしていなければ {@link ClassroomAssembly#none()}）。 */
    public ClassroomAssembly statusOf(long recordId) {
        return statuses.getOrDefault(recordId, ClassroomAssembly.none());
    }

    /** 記録を消したときに状態も捨てる。 */
    public void forget(long recordId) {
        statuses.remove(recordId);
        claimedHere.remove(recordId);
    }

    /**
     * いまある分塊から見て、**この状態のままでよいか**（読むだけ。組立てはしない）。
     *
     * <p>user-api を起動し直すと、メモリには何も無い。「実は既にできている」回を
     * {@code NONE}＝「作成中」と見せないために、DB の状態を復帰してよいかをここで判断する。</p>
     *
     * @return この状態のままでよいなら true（組立て直す必要は無い）
     */
    public boolean statusIsCurrent(ClassroomRecordEntity record,
                                   List<ClassroomRecordingChunkEntity> chunks) {
        if (record == null || record.getAudioPath() == null || record.getAudioName() == null) {
            return true;
        }
        if (chunks == null || chunks.isEmpty()) {
            return true;
        }
        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, record.getRecordId(),
                        record.getAudioPath(), chunks);
        if (!plan.complete()) {
            // 欠落している: 組立て直しても作れない（状態は INCOMPLETE が正しい）
            return false;
        }
        List<Path> files = plan.sessions().stream()
                .map(session -> session.chunks().get(session.chunks().size() - 1).path())
                .toList();
        return isUpToDate(storage.resolve(record.getAudioPath(), record.getAudioName()), files);
    }

    /**
     * DB から読んだ状態を覚え直す（**再起動後の復帰**。組立てはしない）。
     *
     * <p>止めているあいだに「できていた」回は、メモリには何も無い。DB の状態をそのまま
     * 覚え直し、画面が「作成中」を出し続けないようにする。</p>
     */
    public void restore(long recordId, ClassroomAssembly assembly) {
        remember(recordId, assembly);
    }

    /**
     * このプロセスが**本当に始めた**組立てか。
     *
     * <p>{@code PROCESSING} が DB に残っていても、それがこのプロセスのものとは限らない
     * （前回の起動が途中で落ちた回）。区別できないと「作成中」のまま永久に待たされる。</p>
     */
    public boolean isClaimedHere(long recordId) {
        return claimedHere.contains(recordId);
    }

    /**
     * 分塊があれば再生用の 1 本を（必要なら）作り直す。
     *
     * @param chunks その記録の分塊の**行**（DB が引用しているものだけを渡す）
     * @return **配信する 1 本を差し替えた**とき true（分塊が無い・既に新しい・欠落・失敗は false）
     *   ——欠落や失敗の理由は {@link #statusOf(long)} が返す（呼び側は状態で判断する）
     */
    public boolean assembleIfNeeded(ClassroomRecordEntity record, List<ClassroomRecordingChunkEntity> chunks) {
        if (record == null || record.getAudioPath() == null || record.getAudioName() == null) {
            // 置き場が決まっていない（分塊が 1 つも届いていない・取り込み音声）: 何もしない
            return false;
        }
        String dir = record.getAudioPath();
        if (chunks == null || chunks.isEmpty()) {
            // 分塊が 1 つも無い（改修前の録音・取り込み）: 今までどおり 1 本をそのまま配信する
            return false;
        }
        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, record.getRecordId(), dir, chunks);
        if (!plan.complete()) {
            /*
             * 欠落があるので**結合しない**: 途中までを 1 本にすると、利用者には「録れている」ように
             * 見える（黙って失ったことにしない）。状態を残して画面へ出す。
             */
            ClassroomAssembly incomplete = ClassroomAssembly.of(ClassroomAssembly.State.INCOMPLETE,
                    false, plan.sessions().size(), null, plan.missingSeqs(), plan.reason());
            remember(record.getRecordId(), incomplete);
            log.warn("授業録音を結合しません（欠落があります）。recordId={} missing={} issues={}",
                    record.getRecordId(), plan.missingSeqs(), plan.issues());
            return false;
        }
        Path target = storage.resolve(dir, record.getAudioName());
        if (target == null) {
            return false;
        }
        /*
         * 「作り直す必要があるか」は**各セッションの最後の分塊**と比べる。
         *
         * <p>セッションの分塊は連番順に届くので、増えたぶんは**そのセッションの最後の分塊**
         * として現れる（先頭と比べると、分塊が増えても「変わっていない」と見てしまう）。</p>
         */
        List<Path> files = plan.sessions().stream()
                .map(session -> session.chunks().get(session.chunks().size() - 1).path())
                .toList();
        if (isUpToDate(target, files)) {
            return false;
        }
        synchronized (lockOf(record.getRecordId())) {
            // ここから先は**このプロセスが**担当する（DB に PROCESSING を残す根拠）
            claimedHere.add(record.getRecordId());
            // 待っている間に別の要求が組立て終えているかもしれない
            if (isUpToDate(target, files)) {
                return false;
            }
            return assemble(record, dir, plan, target);
        }
    }

    /**
     * 互換の口（分塊の行を渡さない呼び出し）。
     *
     * <p>**直接ファイルを数えて結合する**ので、孤立ファイルも混ざり得る。新しい呼び出しは
     * {@link #assembleIfNeeded(ClassroomRecordEntity, List)} を使う。</p>
     */
    public boolean assembleIfNeeded(ClassroomRecordEntity record) {
        if (record == null || record.getAudioPath() == null) {
            return false;
        }
        List<ClassroomRecordingChunkEntity> chunks = new ArrayList<>();
        for (ClassroomRecordingStorage.StoredChunk chunk
                : storage.listChunks(record.getAudioPath(), record.getRecordId())) {
            ClassroomRecordingChunkEntity entity = new ClassroomRecordingChunkEntity();
            entity.setRecordId(record.getRecordId());
            entity.setSeq(chunk.seq());
            entity.setStorageDir(record.getAudioPath());
            entity.setFileName(chunk.fileName());
            entity.setByteSize(chunk.byteSize());
            // 名前からは分からないので、先頭を見てヘッダの有無を決める
            entity.setContainerHead(startsWithContainerHeader(record.getAudioPath(), chunk.fileName()));
            chunks.add(entity);
        }
        return assembleIfNeeded(record, chunks);
    }

    /** ファイルの先頭がコンテナのヘッダか（互換の口で使う）。 */
    private boolean startsWithContainerHeader(String dir, String fileName) {
        Path path = storage.resolve(dir, fileName);
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (var in = Files.newInputStream(path)) {
            byte[] head = new byte[8];
            int read = in.read(head);
            return RecordingContainerCutter.isContainerHead(head, Math.max(read, 0));
        } catch (IOException cause) {
            return false;
        }
    }

    /** 再生用の 1 本が、いまある分塊より新しいか（＝組立て直す必要が無いか）。 */
    private boolean isUpToDate(Path target, List<Path> files) {
        long newest = 0;
        for (Path file : files) {
            try {
                newest = Math.max(newest, Files.getLastModifiedTime(file).toMillis());
            } catch (IOException cause) {
                return false;
            }
        }
        try {
            if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                return false;
            }
            // 「以上」ではなく「より新しい」で見る: 同じミリ秒に書かれた分塊を取りこぼさない
            return Files.getLastModifiedTime(target).toMillis() > newest;
        } catch (IOException cause) {
            return false;
        }
    }

    /**
     * 実際に 1 本を作る（**作業ファイルに書いてから名前を付ける**）。
     *
     * <p>セッションごとに作業ファイルを作り、2 つ以上あるときは ffmpeg で時刻を積み直して
     * 繋ぐ。失敗したら作業ファイルを全部消し、**いまある 1 本は触らない**。</p>
     */
    private boolean assemble(ClassroomRecordEntity record, String dir,
                             ClassroomRecordingSessionPlanner.Plan plan, Path target) {
        long recordId = record.getRecordId();
        List<Path> works = new ArrayList<>();
        Path temp = null;
        try {
            List<ClassroomRecordingSessionPlanner.Session> sessions = plan.sessions();
            for (int index = 0; index < sessions.size(); index += 1) {
                Path work = storage.createWorkFile(dir, "session-" + recordId + "-" + index);
                works.add(work);
                concatChunks(sessions.get(index), work);
            }
            // ffmpeg に書かせる出力は**存在しない新しいパス**（前の結合結果を上書きさせない）
            temp = storage.newWorkFilePath(dir, "assemble-" + recordId);
            if (works.size() == 1) {
                // セッションが 1 つ＝同じコンテナの続き。再多重化は要らない（そのまま 1 本）
                Files.copy(works.get(0), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (!media.available()) {
                    /*
                     * ffmpeg が無い環境: **結合しない**（タイムスタンプが重なった壊れた 1 本を
                     * 公開しない）。分塊は残っているので、あとから結合できる。
                     */
                    ClassroomAssembly failed = ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false,
                            sessions.size(), null, List.of(),
                            "続録の音声を 1 本に結合できませんでした（この環境では結合の道具が使えません）。"
                                    + "録音そのものは分塊として残っています。");
                    remember(recordId, failed);
                    log.error("授業録音を結合できません（ffmpeg が使えません）。recordId={} sessions={}",
                            recordId, sessions.size());
                    cleanup(works, temp);
                    return false;
                }
                media.remuxSessions(works, sessionStarts(sessions), temp);
            }
            storage.moveIntoPlace(temp, target);
            // 結合の検証（長さが書き起こしの時間軸と大きく食い違っていないか）
            Double duration = media.probeDurationSeconds(target);
            List<Integer> missing = List.of();
            ClassroomAssembly ready = ClassroomAssembly.of(ClassroomAssembly.State.READY, true,
                    sessions.size(), duration, missing, null);
            remember(recordId, ready);
            log.info("授業録音を組立てました。recordId={} sessions={} bytes={} durationSeconds={}",
                    recordId, sessions.size(), sizeOf(target), duration);
            cleanup(works, null);
            return true;
        } catch (Exception cause) {
            log.error("授業録音を組立てられませんでした（分塊は残っています）。recordId={}", recordId, cause);
            cleanup(works, temp);
            remember(recordId, ClassroomAssembly.of(ClassroomAssembly.State.FAILED, false,
                    plan.sessions().size(), null, List.of(),
                    "続録の音声を 1 本に結合できませんでした。"
                            + "録音そのものは分塊として残っています。【再試行】で作り直せます。"));
            return false;
        }
    }

    /**
     * セッションの開始位置（秒。**録音回放の時間軸**での位置）。
     *
     * <p>画面が測った分塊の経過秒（`開始オフセット秒`）を使う。これで結合した 1 本の時刻が
     * **書き起こしの時間軸と一致**する（タイムスタンプを 0 から積み直すだけだと、2 本目以降が
     * 前半に重なる）。測っていない旧クライアントは順に積む（重なりは作らない）。</p>
     */
    private List<Double> sessionStarts(List<ClassroomRecordingSessionPlanner.Session> sessions) {
        List<Double> starts = new ArrayList<>();
        for (ClassroomRecordingSessionPlanner.Session session : sessions) {
            starts.add(ClassroomRecordingSessionPlanner.startSecondsOf(session));
        }
        return starts;
    }

    /** セッションの中の分塊を 1 本の作業ファイルへ繋ぐ（同じコンテナの続きなので単純連結）。 */
    private void concatChunks(ClassroomRecordingSessionPlanner.Session session, Path work) throws IOException {
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(work))) {
            for (ClassroomRecordingSessionPlanner.SessionChunk chunk : session.chunks()) {
                Files.copy(chunk.path(), out);
            }
        }
    }

    private void cleanup(List<Path> works, Path temp) {
        for (Path work : works) {
            storage.deleteQuietly(work);
        }
        if (temp != null) {
            storage.deleteQuietly(temp);
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException cause) {
            return 0;
        }
    }

    /** 状態を覚えておく（古いものから捨てる）。 */
    private void remember(long recordId, ClassroomAssembly assembly) {
        statuses.put(recordId, assembly);
        if (statuses.size() > STATUS_RETAIN) {
            statuses.keySet().stream().limit(statuses.size() - STATUS_RETAIN).forEach(statuses::remove);
        }
    }

    private Object lockOf(Long recordId) {
        int index = recordId == null ? 0 : (int) Math.floorMod(recordId, (long) LOCK_STRIPES);
        return locks[index];
    }
}
