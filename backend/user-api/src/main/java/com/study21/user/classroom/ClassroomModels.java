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
            String updatedAt) {
    }

    // ------------------------------------------------------------------ 分塊

    /** 分塊アップロードの結果。トリガーが成立したら pendingNoteId と runPath を返す（画面が admin-api を呼ぶ）。 */
    public record ChunkUploadResult(
            long recordId,
            int seq,
            int nextSeq,
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
            String notice) {
    }

    // ------------------------------------------------------------------ 削除

    public record DeleteResult(long recordId, boolean deletedAudio) {
    }
}
