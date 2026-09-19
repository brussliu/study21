package com.study21.user.classroom;

import java.util.List;
import java.util.Map;

/**
 * 録音の結合（分塊 → 再生用の 1 本）の**結果と状態**（画面へ出す）。
 *
 * <p>「結合できたか」だけでは足りない: 結合に失敗しても**分塊は 1 つも失われない**ので、
 * 画面は「音声は残っている・もう一度試せる」と言える必要がある。また、結合していない間に
 * 配信している 1 本は**途中までの音**なので、嘘の「完了」を出さない。</p>
 */
public record ClassroomAssembly(
        /** 結合の状態。 */
        State state,
        /** 結合したファイルが**いまある分塊の全部**を含むか（false は途中までの音）。 */
        boolean complete,
        /** 結合した本数（セッションの数）。 */
        int sessionCount,
        /** 結合後の長さ（秒。分からなければ null）。 */
        Double durationSeconds,
        /** 欠けている連番（あれば）。 */
        List<Integer> missingSeqs,
        /** 人が読む理由（日本語。無ければ null）。 */
        String reason) {

    /** 結合の状態。 */
    public enum State {
        /**
         * まだ結合していない（分塊が無い・必要が無い）。
         *
         * <p>画面はこれを「作成中」と読まない（{@link #QUEUED}／{@link #PROCESSING} と区別する）。
         * 作る必要があるのに {@code NONE} のままなら、画面は【再試行】を出す。</p>
         */
        NONE,
        /** 作成を**受け付けた**（背景の順番待ち。まだ始まっていない）。 */
        QUEUED,
        /** **このプロセスが**作成中（背景で走っている）。 */
        PROCESSING,
        /** 結合が済んだ。 */
        READY,
        /** 結合を試みて失敗した（分塊は残っている。もう一度試せる）。 */
        FAILED,
        /** 欠落があるので結合しない（分塊は残っている）。 */
        INCOMPLETE
    }

    /** 何もしていない状態。 */
    public static ClassroomAssembly none() {
        return new ClassroomAssembly(State.NONE, false, 0, null, List.of(), null);
    }

    /** 保存（`ClassroomRecordingAssembler`）へ渡す形から作る。 */
    public static ClassroomAssembly of(State state, boolean complete, int sessionCount,
                                       Double durationSeconds, List<Integer> missingSeqs, String reason) {
        return new ClassroomAssembly(state, complete, sessionCount, durationSeconds,
                missingSeqs == null ? List.of() : List.copyOf(missingSeqs), reason);
    }

    /** DB へ保存するときの状態コード（`CR_授業記録情報` の列が無い環境でも安全に使える）。 */
    public String stateCode() {
        return switch (state) {
            case READY -> "READY";
            case QUEUED -> "QUEUED";
            case PROCESSING -> "PROCESSING";
            case FAILED -> "FAILED";
            case INCOMPLETE -> "INCOMPLETE";
            case NONE -> "NOT_STARTED";
        };
    }

    /** 画面へ出す短い説明（状態と、次にできること）。 */
    public Map<String, Object> toView() {
        return Map.of(
                "state", stateCode(),
                "complete", complete,
                "sessionCount", sessionCount,
                "durationSeconds", durationSeconds,
                "missingSeqs", missingSeqs,
                "reason", reason == null ? "" : reason);
    }
}
