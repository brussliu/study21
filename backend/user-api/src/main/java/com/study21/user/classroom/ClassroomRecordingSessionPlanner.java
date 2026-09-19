package com.study21.user.classroom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 録音の分塊を**再生用の 1 本**にするための計画（どのセッションをどう繋ぐか）。
 *
 * <p><b>なぜセッションに分けるか</b>: 画面は一時停止・画面の開き直しで `MediaRecorder` を
 * 作り直す。新しい `MediaRecorder` の分塊は**別のコンテナ**で、**タイムスタンプが 0 から
 * 始まる**。2 本目以降のコンテナのヘッダを落として繋ぐだけでは、その回の時刻が最初の
 * セッションへ重なり、**再生の位置と書き起こしの時刻が食い違う**（利用者から見ると
 * 「後半の音が前半に重なっている」）。そこで
 * 「同じ `MediaRecorder` の続き（ヘッダ無し）」と「新しいセッション（ヘッダ有り）」を分け、
 * セッションごとに**時刻を 0 から積み直して**から繋ぐ（{@link ClassroomRecordingAssembler}）。</p>
 *
 * <p><b>正体は DB の行だけ</b>: 置き場のディレクトリを数えて繋ぐと、**トランザクションが
 * 失敗して残った孤立ファイル**まで音として混ざる。引用（`CR_授業録音分塊情報` の行）が
 * 無いファイルは使わない。</p>
 */
public final class ClassroomRecordingSessionPlanner {

    private ClassroomRecordingSessionPlanner() {
    }

    /** 1 つのセッション（同じ `MediaRecorder` から出た分塊の並び）。 */
    public record Session(List<SessionChunk> chunks) {

        /** このセッションの連番の範囲（人が読む用）。 */
        public String seqRange() {
            return chunks.get(0).seq() + "-" + chunks.get(chunks.size() - 1).seq();
        }

        /** 連番だけの並び。 */
        public List<Integer> seqs() {
            return chunks.stream().map(SessionChunk::seq).toList();
        }
    }

    /** セッションの中の 1 分塊（実体の場所つき）。 */
    public record SessionChunk(int seq, Path path, String fileName, long byteSize, boolean containerHead,
                               /** 録音回放の時間軸での開始（秒。分からないときは null）。 */
                               Double startSeconds) {
    }

    /** セッションの開始位置（録音回放の時間軸での秒）。分からなければ 0。 */
    public static double startSecondsOf(Session session) {
        for (SessionChunk chunk : session.chunks()) {
            if (chunk.startSeconds() != null && chunk.startSeconds() >= 0) {
                return chunk.startSeconds();
            }
        }
        return 0;
    }

    /**
     * 計画（セッション・欠落・人が読む理由）。
     *
     * <p><b>完全でないときは {@link #sessions()} を空にする</b>: 欠けた分塊を飛ばして
     * 「それらしい 1 本」を作ると、利用者には録れているように見える（黙って音を失わない）。</p>
     */
    public record Plan(List<Session> sessions, List<Integer> missingSeqs, List<String> issues) {

        /** この分塊だけで**完全な 1 本**を作れるか（欠落が無く、先頭から連続している）。 */
        public boolean complete() {
            return issues.isEmpty() && missingSeqs.isEmpty() && startsAtFirstSeq();
        }

        private boolean startsAtFirstSeq() {
            if (sessions.isEmpty()) {
                return true;
            }
            return sessions.get(0).chunks().get(0).seq() == 1;
        }

        /** 人が読む理由（日本語。無ければ null）。 */
        public String reason() {
            if (complete()) {
                return null;
            }
            List<String> parts = new ArrayList<>(issues);
            if (!missingSeqs.isEmpty()) {
                parts.add("分塊が欠けています（連番 " + missingSeqs + "）");
            }
            if (!startsAtFirstSeq() && sessions.isEmpty()) {
                parts.add("録音の先頭（連番 1）がありません");
            }
            return parts.isEmpty() ? null : String.join(" / ", parts);
        }
    }

    /**
     * 分塊の行から計画を作る（**ファイルには触らない**。実体の有無と大きさだけ確かめる）。
     *
     * @param chunks その記録の分塊の行（連番順でなくてよい）
     */
    public static Plan plan(ClassroomRecordingStorage storage, long recordId, String relativeDir,
                            List<ClassroomRecordingChunkEntity> chunks) {
        List<ClassroomRecordingChunkEntity> rows = chunks == null ? List.of() : new ArrayList<>(chunks);
        rows.sort(java.util.Comparator.comparingInt(ClassroomRecordingChunkEntity::getSeq));

        List<String> issues = new ArrayList<>();
        List<Integer> present = new ArrayList<>();
        List<Integer> declared = new ArrayList<>();
        List<Session> sessions = new ArrayList<>();
        List<SessionChunk> current = new ArrayList<>();

        for (ClassroomRecordingChunkEntity row : rows) {
            declared.add(row.getSeq());
            Path path = storage.resolve(row.getStorageDir() == null ? relativeDir : row.getStorageDir(),
                    row.getFileName());
            if (row.getFileName() == null || path == null) {
                issues.add("連番 " + row.getSeq() + " の保存先が記録されていません");
                continue;
            }
            if (!Files.isRegularFile(path)) {
                issues.add("連番 " + row.getSeq() + " の音声の実体がありません");
                continue;
            }
            long size;
            try {
                size = Files.size(path);
            } catch (IOException cause) {
                issues.add("連番 " + row.getSeq() + " の音声の大きさを読めません");
                continue;
            }
            if (row.getByteSize() != null && row.getByteSize() > 0 && size != row.getByteSize()) {
                // 上書き・破損・差し替え。**壊れた実体を 1 本に混ぜない**
                issues.add("連番 " + row.getSeq() + " の音声の大きさが記録と違います（記録 "
                        + row.getByteSize() + " バイト・実体 " + size + " バイト）");
                continue;
            }
            present.add(row.getSeq());
            boolean head = Boolean.TRUE.equals(row.getContainerHead());
            // 新しいコンテナ（ヘッダ有り）は**新しいセッション**の先頭（1 塊目は必ず先頭）
            if (head && !current.isEmpty()) {
                sessions.add(new Session(List.copyOf(current)));
                current.clear();
            }
            Double startSeconds = row.getStartOffsetSeconds() == null
                    ? null : row.getStartOffsetSeconds().doubleValue();
            current.add(new SessionChunk(row.getSeq(), path, row.getFileName(), size, head, startSeconds));
        }
        if (!current.isEmpty()) {
            sessions.add(new Session(List.copyOf(current)));
        }

        /*
         * 欠落は「**宣言されている連番の範囲**（行がある 1〜最大）」で数える。
         *
         * <p>行はあるのに実体が無い・大きさが違う分塊も**その連番を使えない**ので、欠落として
         * 数える（数えないと、画面は「欠けている連番」を受け取れずに何を送り直せばよいか
         * 分からない）。</p>
         */
        List<Integer> missing = new ArrayList<>();
        if (!declared.isEmpty()) {
            int max = declared.stream().mapToInt(Integer::intValue).max().orElse(0);
            for (int seq = 1; seq <= max; seq += 1) {
                if (!present.contains(seq)) {
                    missing.add(seq);
                }
            }
        }
        Plan plan = new Plan(sessions, missing, issues);
        // 完全でなければ**セッションを渡さない**（欠けたまま 1 本を作らない）
        return plan.complete() ? plan : new Plan(List.of(), missing, issues);
    }
}
