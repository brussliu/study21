package com.study21.user.classroom;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 授業録音 / AI 授業記録のモデル（画面 `views/classroom/*` と対になる契約）。
 *
 * <p>言語モードの値は画面（`features/classroom/classroom.ts` の {@code LanguageMode}）と
 * 1 対 1: {@code zh / ja / en / zh-en / ja-en / auto}。状態は CR_授業記録情報.状態、
 * ノートの種別/生成状態は CR_授業ノート情報 と同じ。</p>
 */
public final class ClassroomModels {

    private ClassroomModels() {
    }

    // ------------------------------------------------------------------ 言語

    /**
     * 言語モード（画面の LanguageMode と同じ値）。
     *
     * <p>利用者の指示で「自動」を廃止した（言語コードはコード側で固定するため、
     * 自動判定は持たない）。既存レコードに `auto` が残っていても表示はできる。</p>
     */
    public static final List<String> LANGUAGE_MODES = List.of("zh", "ja", "en", "zh-en", "ja-en");

    // ------------------------------------------------------------------ 状態

    public static final String STATUS_RECORDING = "RECORDING";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_TRANSCRIBING = "TRANSCRIBING";
    public static final String STATUS_ANALYZING = "ANALYZING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final Map<String, String> STATUS_LABELS = Map.of(
            STATUS_RECORDING, "録音中",
            STATUS_STOPPED, "停止（まとめ作成待ち）",
            STATUS_TRANSCRIBING, "書き起こし中",
            STATUS_ANALYZING, "AI が分析中",
            STATUS_COMPLETED, "完了",
            STATUS_FAILED, "失敗",
            STATUS_CANCELLED, "取消");

    // ------------------------------------------------------------------ ノート

    public static final String NOTE_PHASE = "PHASE";
    public static final String NOTE_FINAL = "FINAL";
    public static final String NOTE_PENDING = "PENDING";
    public static final String NOTE_GENERATING = "GENERATING";
    public static final String NOTE_READY = "READY";
    public static final String NOTE_FAILED = "FAILED";

    public static final Map<String, String> NOTE_STATUS_LABELS = Map.of(
            NOTE_PENDING, "分析待ち",
            NOTE_GENERATING, "AI が生成中",
            NOTE_READY, "できました",
            NOTE_FAILED, "失敗");

    // ------------------------------------------------------------------ 制限

    /** 科目の上限（画面と同じ）。 */
    public static final int SUBJECT_MAX = 40;
    /** 授業名が未入力のときにサーバーが入れる名前。 */
    public static final String UNTITLED = "授業名未設定";

    /** 授業名の上限（画面と同じ）。 */
    public static final int TITLE_MAX = 200;
    /** ブラウザ認識の 1 件あたりの最大文字数。 */
    public static final int TRANSCRIPT_MAX = 2000;
    /** 1 ページの既定件数。 */
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    // ------------------------------------------------------------------ 画面

    /** 画面が使う設定（有効／無効・分塊長・録音最大時間・保存期間・日次上限）。API Key は返さない。 */
    public record OptionsResult(
            boolean enabled,
            int chunkSeconds,
            int maxRecordingMinutes,
            int retentionDays,
            /** 1 アカウント 1 日の録音回数（0 = 無制限） */
            int dailyLimit,
            /** 今日すでに作った件数 */
            long usedToday,
            /** 使えないときの理由（日本語。空なら使える） */
            String notice,
            /**
             * 書き起こしを誰が行うか。
             *
             * <ul>
             *   <li>`BROWSER` … 画面（Chrome / Edge の Web Speech API）。サーバーは STT を呼ばない</li>
             *   <li>`SERVER` … サーバー（OpenAI 互換 / Google Cloud など。API Key が必要）</li>
             * </ul>
             */
            String sttMode,
            /** 言語モード（ja / zh / en / zh-en / ja-en）→ BCP-47（例 ja-JP）。 */
            Map<String, String> sttLanguageCodes,
            /**
             * 授業の AI 解析（フェーズノート batC61 / 最終まとめ batC62）を使うか。
             * false のときはノートを作らない（書き起こしだけ）。
             */
            boolean noteEnabled,
            /**
             * **ストリーミング書き起こし**（話しながら文字が出る）が使えるか。
             *
             * <p>阿里巴巴（DashScope のリアルタイム認識）のときだけ true。画面はこれを見て
             * 「小刻みに PCM を送る」経路に切り替える（使えないときは今までどおり分塊ごと）。</p>
             */
            boolean streamStt) {
    }

    /** 書き起こしをブラウザ（Web Speech API）で行う。 */
    public static final String STT_MODE_BROWSER = "BROWSER";
    /** 書き起こしをサーバー（STT プロバイダー）で行う。 */
    public static final String STT_MODE_SERVER = "SERVER";

    /**
     * ブラウザ（Web Speech API）の認識結果 1 件。
     *
     * <p>画面が final になった発話ごとに送る。連番はサーバーが振る（ブラウザの切句は
     * 分塊と一致しないため）。時間は「録音開始からの経過秒」で受ける。</p>
     */
    public record TranscriptRequest(
            @NotBlank(message = "書き起こしのテキストを指定してください。")
            @Size(max = TRANSCRIPT_MAX, message = "書き起こしは1件2000文字以内です。")
            String text,
            /** この発話が終わった時刻（録音開始からの秒）。省略時は前のセグメントの終わり */
            @DecimalMin(value = "0", message = "経過秒は 0 以上で指定してください。")
            BigDecimal offsetSeconds) {
    }

    // ------------------------------------------------------------------ 作成

    public record CreateRequest(
            @Size(max = TITLE_MAX, message = "授業名は200文字以内で入力してください。")
            String title,
            /** 科目（数学・英語など。任意）。一覧で授業名とは別に出す。 */
            @Size(max = SUBJECT_MAX, message = "科目は40文字以内で入力してください。")
            String subject,
            /** 言語モード（zh / ja / en / zh-en / ja-en / auto）。省略時 auto */
            String languageMode,
            /** 前置詞プリセットの ID（任意）。選択時のテキストをスナップショットで残す */
            Long presetId) {
    }

    /** 作成・開始の結果。 */
    public record RecordStatus(
            long recordId,
            String recordNo,
            String status,
            String statusLabel,
            int version) {
    }

    /** ノート（フェーズ / 最終まとめ）を生成する admin-api の薄い入口の URL。 */
    public static String noteRunPath(long noteId) {
        return "/api/admin/batch/classroom/notes/" + noteId + "/run";
    }

    // ------------------------------------------------------------------ 転写

    public record SegmentView(
            long segmentId,
            int seq,
            Double startOffsetSeconds,
            Double endOffsetSeconds,
            String speaker,
            String text,
            String language,
            String createdAt) {
    }

    public record SegmentListResult(List<SegmentView> items, int nextSeq) {
    }

    // ------------------------------------------------------------------ ノート

    public record NoteView(
            long noteId,
            String kind,
            Integer phaseNo,
            Integer startSeq,
            Integer endSeq,
            String status,
            String statusLabel,
            /** AI が返したノート本文（JSON 文字列。画面が描く） */
            String noteJson,
            String errorCode,
            String errorMessage,
            String createdAt,
            String updatedAt) {
    }

    // ------------------------------------------------------------------ 前置詞

    public record PresetView(
            long presetId,
            String scope,
            String name,
            String text,
            int displayOrder) {
    }

    // ------------------------------------------------------------------ 一覧

    public record RecordRow(
            long recordId,
            String recordNo,
            String title,
            String subject,
            String languageMode,
            String status,
            String statusLabel,
            Integer durationSeconds,
            Integer transcribedChars,
            boolean hasAudio,
            String createdAt,
            String updatedAt) {
    }

    public record RecordListResult(
            List<RecordRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    /** 詳細（状態・転写全文・ノート一覧・前置詞・最終まとめ）。 */
    public record RecordDetail(
            long recordId,
            String recordNo,
            String title,
            String subject,
            String languageMode,
            Long presetId,
            String presetName,
            String presetText,
            String status,
            String statusLabel,
            String startTime,
            String endTime,
            Integer durationSeconds,
            Integer transcribedChars,
            String summaryJson,
            boolean hasAudio,
            String audioMime,
            List<SegmentView> segments,
            List<NoteView> notes,
            int version,
            String createdAt,
            String updatedAt,
            /**
             * 結合（再生用の 1 本）の状態。**「音声は保存されている」と「再生できる」を
             * 分けて出す**ために返す（失敗しても分塊は残っている＝再試行できる）。
             */
            AssemblyView assembly) {
    }

    // ------------------------------------------------------------------ 分塊

    /**
     * 分塊の処理状態（CR_授業録音分塊情報.処理状態）。
     *
     * <p>音声はどの状態でも保存されている（書き起こしだけを諦めた回がある）。
     * 録音の最大時間の判定や「次に送る連番」は**この表の連番**で見る
     * （転写セグメントの連番は文の数なので、分塊の数とは一致しない）。</p>
     */
    public static final String CHUNK_STORED = "STORED";
    public static final String CHUNK_TRANSCRIBED = "TRANSCRIBED";
    public static final String CHUNK_SKIPPED = "SKIPPED";

    /** 分塊 1 つの状態（画面が「次に送る連番」と録音の位置を知るために読む）。 */
    public record ChunkView(
            int seq,
            long byteSize,
            Double startOffsetSeconds,
            Double endOffsetSeconds,
            String mime,
            /** この分塊が新しいコンテナ（ヘッダ）から始まるか。 */
            boolean containerHead,
            String processingStatus,
            int segmentCount,
            String createdAt) {
    }

    /**
     * その記録に保存済みの分塊（連番順）。
     *
     * <p>`nextSeq` は**次に送る分塊の連番**（画面はこれをそのまま使う。
     * 転写の連番から作らない）。`recordedSeconds` は保存済みの分塊が示す録音の位置で、
     * 画面を開き直したときの続きの時間に使う。</p>
     */
    public record ChunkListResult(
            List<ChunkView> items,
            int chunkCount,
            int maxSeq,
            int nextSeq,
            long totalBytes,
            Double recordedSeconds,
            /**
             * 終了してよいかの下見（画面の【授業を終了】の状態表示に使う）。
             *
             * <p>画面を開き直してもサーバーから同じ判断が取れるように返す（画面のメモリだけに
             * 頼らない）。</p>
             */
            ChunkChecklist finalizeCheck) {
    }

    /**
     * 終了前の確認の結果（分塊が**1 から連続しているか**）。
     *
     * <p>「少なくとも 1 つある」では足りない: 途中が欠けていても気づけず、最後の分塊が
     * 届いていなくても終われてしまう（音は後から作り直せない）。欠けている連番を返して
     * 画面が**その分塊だけ送り直せる**ようにする。</p>
     */
    public record ChunkChecklist(
            /** 分塊が 1 から連続していて、全部そろっているか（＝終了できる）。 */
            boolean complete,
            /** 足りない連番（連続していないところ・実体が無いところ）。 */
            List<Integer> missingSeqs,
            /** 保存できている分塊の数。 */
            int storedChunks,
            /** 画面が宣言した（送れた）最後の連番。分からなければ 0。 */
            int expectedChunks,
            /** 画面に出す理由（日本語。終了できるときは null）。 */
            String reason,
            /** **実体が無い／壊れている**連番（行はあるが音が無い）。 */
            List<Integer> brokenSeqs,
            /** **宣言していないのに保存されている**連番（画面の一覧と食い違い）。 */
            List<Integer> extraSeqs) {

        /** 終了できるときの形。 */
        public static ChunkChecklist ready(int stored) {
            return new ChunkChecklist(true, List.of(), stored, stored, null, List.of(), List.of());
        }
    }

    /**
     * 画面が停止のあとに送る「**実際に送れた**分塊の一覧」（終了前の確認に使う）。
     *
     * <p><b>3 つの欄の意味を固定する</b>（食い違う一覧は受け付けない）:</p>
     * <ul>
     *   <li>`uploadedSeqs` … **送信が成功した**連番（1 から連続しているはず）。</li>
     *   <li>`lastSeq` … `uploadedSeqs` の最大（送れた最後の連番）。</li>
     *   <li>`totalCount` … `uploadedSeqs` の**件数**（分塊を作った数ではない）。</li>
     *   <li>`endSample` … 最後に送れた分塊が終わる**録音回放の時間軸**（16kHz。任意）。</li>
     * </ul>
     *
     * <p>3 つが食い違う一覧（例: `lastSeq=3` なのに `uploadedSeqs.size()=1`）は
     * **矛盾した一覧**として断る。画面が「作った数」を送ってしまうと、最後の分塊が
     * 届いていないのに「そろっている」と見てしまう。</p>
     *
     * @param lastSeq      送れた最後の連番（1 から連続）
     * @param totalCount   送れた分塊の件数
     * @param endSample    最後に送れた分塊の終わりの位置（16kHz のサンプル数。任意）
     * @param uploadedSeqs **送れた連番そのもの**（任意。渡されれば対応表の正解として使う）
     */
    public record ChunkManifest(int lastSeq, int totalCount, Long endSample, List<Integer> uploadedSeqs) {

        /** 送れた連番の一覧（渡されていなければ `1..lastSeq` とみなす＝旧い画面）。 */
        public List<Integer> uploaded() {
            if (uploadedSeqs != null && !uploadedSeqs.isEmpty()) {
                return uploadedSeqs;
            }
            List<Integer> derived = new java.util.ArrayList<>();
            for (int seq = 1; seq <= Math.max(0, lastSeq); seq += 1) {
                derived.add(seq);
            }
            return derived;
        }

        /**
         * 一覧そのものが矛盾していないか（`lastSeq`・`totalCount`・`uploadedSeqs` の整合）。
         *
         * <p>矛盾していれば**その理由**（日本語）を返す。問題なければ null。</p>
         */
        public String inconsistency() {
            if (lastSeq < 0 || totalCount < 0) {
                return "一覧の数が負の値になっています。";
            }
            if (uploadedSeqs == null || uploadedSeqs.isEmpty()) {
                // 旧い画面（連番だけ）: 数は 1..lastSeq とみなす
                return totalCount == lastSeq ? null
                        : "送った数（" + totalCount + " 件）と最後の連番（" + lastSeq + "）が合いません。";
            }
            java.util.TreeSet<Integer> unique = new java.util.TreeSet<>(uploadedSeqs);
            if (unique.size() != uploadedSeqs.size()) {
                return "送れた連番に重複があります。";
            }
            if (!unique.isEmpty() && (unique.first() < 1 || unique.last() != lastSeq)) {
                return "送れた連番の範囲（" + unique.first() + "〜" + unique.last()
                        + "）と最後の連番（" + lastSeq + "）が合いません。";
            }
            if (uploadedSeqs.size() != totalCount) {
                return "送れた連番の数（" + uploadedSeqs.size() + " 件）と一覧の数（"
                        + totalCount + " 件）が合いません。";
            }
            return null;
        }
    }

    /**
     * 結合（分塊 → 再生用の 1 本）の状態（**画面が読む形**）。
     *
     * <p>「音声が保存できているか」と「再生用の 1 本ができているか」は**別**のこと。
     * 保存できていれば音は残っている（結合はあとからやり直せる）。</p>
     *
     * @param state          NONE（まだ作っていない）/ READY（できた）/ INCOMPLETE（欠落がある）/
     *                       FAILED（作れなかった・もう一度試せる）
     * @param complete       いまある分塊の**全部**を含んだ 1 本があるか
     * @param storedChunks   保存できている分塊の数（**音は残っている**ことの根拠）
     * @param durationSeconds できた 1 本の長さ（秒。分からなければ null）
     * @param missingSeqs    欠けている連番（あれば）
     * @param reason         画面に出す理由（日本語。無ければ null）
     */
    public record AssemblyView(
            String state,
            boolean complete,
            int storedChunks,
            Double durationSeconds,
            List<Integer> missingSeqs,
            String reason) {

        /** まだ何もしていないときの形。 */
        public static AssemblyView of(String state, boolean complete, int storedChunks,
                                      Double durationSeconds, List<Integer> missingSeqs, String reason) {
            return new AssemblyView(state, complete, storedChunks, durationSeconds,
                    missingSeqs == null ? List.of() : missingSeqs, reason);
        }
    }

    /** 終了の要求（不完全なまま終える明示と、送った分塊の一覧）。 */
    public record EndRequest(boolean force, ChunkManifest manifest) {
    }

    /** 分塊アップロードの結果。トリガーが成立したら pendingNoteId と runPath を返す（画面が admin-api を呼ぶ）。 */
    public record ChunkUploadResult(
            long recordId,
            int seq,
            /** 次に取りに行く**書き起こし**の連番（セグメント表） */
            int nextSeq,
            /**
             * 次に送る**分塊**の連番（分塊表。max(分塊連番) + 1）。
             *
             * <p>画面はこれをそのまま次の分塊の連番にする（転写の連番から作らない。文の数と
             * 分塊の数は違う）。</p>
             */
            int nextChunkSeq,
            List<SegmentView> appendedSegments,
            Long pendingNoteId,
            boolean triggered,
            String status,
            /** pendingNoteId のノートを生成する入口（admin-api）。トリガー成立時だけ入る */
            String runPath) {
    }

    // ------------------------------------------------------------------ 終了

    public record EndResult(
            long recordId,
            String status,
            String statusLabel,
            /** 最終まとめのノートID。書き起こしが 1 件も無かったときは null（まとめを作らない） */
            Long finalNoteId,
            /** 最終まとめ（batC62）を起動する入口（admin-api）へ渡す URL。finalNoteId が null のときも null */
            String runPath,
            /** 画面に出す補足（日本語）。通常は null、最終まとめを作らなかったときだけ入る */
            String notice,
            /** 音声が**全部そろっているか**（「音声は保存されています」と言ってよいのは true のときだけ）。 */
            boolean complete,
            /** 足りない分塊の連番（あれば。画面はこの連番を送り直す）。 */
            List<Integer> missingSeqs,
            /** 明示の「不完全なまま終了」で終えたか。 */
            boolean forced) {
    }

    // ------------------------------------------------------------------ 削除

    public record DeleteResult(long recordId, boolean deletedAudio) {
    }
}
