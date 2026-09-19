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

    /**
     * 1 つの記録が持ち得る分塊の数の上限。
     *
     * <p>録音の最大時間（既定 120 分）と分塊の長さ（既定 20 秒）から 360 個ほど。上限を
     * 大きく超える一覧が届いたら、**調べる前に**断る（数だけを申告して大量の行を作らせない）。</p>
     */
    public static final int MAX_CHUNKS = 7_200;

    /**
     * 終了前の確認で断った**理由の種類**（画面は文面ではなくこの値で分岐する）。
     *
     * <p>`MISSING`（まだ送っていない・行が無い）と `BROKEN`（行はあるが実体が無い）は
     * **利用者ができることが違う**: 前者は送り直せば直る。後者は送り直しても直らないので、
     * 「失うことを確認して終える」しかない。</p>
     */
    public static final String CHECK_OK = "OK";
    public static final String CHECK_MISSING = "MISSING";
    public static final String CHECK_BROKEN = "BROKEN";
    public static final String CHECK_EXTRA = "EXTRA";
    public static final String CHECK_MANIFEST_CONTRADICTION = "MANIFEST_CONTRADICTION";
    public static final String CHECK_END_SAMPLE_MISMATCH = "END_SAMPLE_MISMATCH";
    public static final String CHECK_NO_CHUNKS = "NO_CHUNKS";
    public static final String CHECK_FINALIZE_LOCKED = "FINALIZE_LOCKED";

    /**
     * 統一時間軸のサンプル率（Hz）。
     *
     * <p>画面（`frontend/pc-web/src/features/classroom/pcm.ts` の `TIMELINE_SAMPLE_RATE`）と
     * **同じ値**。終了時の確認で「録音の終わりの位置」を比べるときの単位。</p>
     */
    public static final int TIMELINE_SAMPLE_RATE = 16_000;

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
            List<Integer> extraSeqs,
            /**
             * **断った理由の種類**（{@link ClassroomModels#CHECK_MISSING} など）。
             *
             * <p>文面（日本語）で判断させない: 画面は種類で「送り直せるのか」「失うしかないのか」を
             * 決める。文言を変えた瞬間に画面の分岐が壊れるのを防ぐ。</p>
             */
            String reasonCode,
            /** **実際に録れた**最後の連番（画面が宣言した値。分からなければ 0）。 */
            int expectedLastSeq,
            /** **後端に保存できている**連番（画面が照合できるように返す）。 */
            List<Integer> savedSeqs,
            /** 画面が申告した「録音の終わりの位置」（16kHz のサンプル数。無ければ null）。 */
            Long endSample) {

        /** 終了できるときの形。 */
        public static ChunkChecklist ready(int stored) {
            return new ChunkChecklist(true, List.of(), stored, stored, null, List.of(), List.of(),
                    ClassroomModels.CHECK_OK, 0, List.of(), null);
        }
    }

    /**
     * 画面が停止のあとに送る「**実際に録れた分塊**と、そのうち送信の応答を受け取れた分塊」。
     *
     * <p><b>欄の意味を固定する</b>（食い違う一覧は受け付けない）:</p>
     * <ul>
     *   <li>`expectedLastSeq` … **実際に録れた**最後の連番（分塊を作った時点で決まる。
     *       **送れたかどうかとは別**）。</li>
     *   <li>`expectedCount` … **実際に録れた**分塊の数（`1..expectedLastSeq` の件数）。</li>
     *   <li>`uploadedSeqs` … **送信の応答を受け取れた**連番（**補助情報**。保存の事実ではない）。</li>
     *   <li>`expectedEndSample` … 最後に**録れた**分塊が終わる**録音回放の時間軸**の位置
     *       （16kHz のサンプル数）。</li>
     *   <li>`unrecoverableSeqs` … 送り直しても直らないと画面が判断した連番
     *       （4xx の内容エラーなど。**黙って捨てない**ために申告する）。</li>
     * </ul>
     *
     * <p>`lastSeq` / `totalCount` は**旧い画面**（「送れた範囲」しか送らない）との互換のために
     * 残す。意味は「送れた最後の連番」「送れた件数」で、**「録れた範囲」ではない**。</p>
     *
     * <p><b>どちらを正とするか</b>: 保存の事実は後端（DB の行と実体のファイル）が正しい。
     * `uploadedSeqs` は「応答を取りこぼした」ときにだけ欠けるので、**欠落の判断には使わない**
     * （応答だけ失われた分塊を「欠けている」と誤判定すると、実際には残っている音を
     * 利用者に諦めさせることになる）。</p>
     *
     * @param expectedLastSeq  実際に録れた最後の連番（旧い画面は 0）
     * @param expectedCount    実際に録れた分塊の数（旧い画面は 0）
     * @param uploadedSeqs     送信の応答を受け取れた連番（旧い画面は null/空）
     * @param expectedEndSample 録音の終わりの位置（16kHz のサンプル数。任意）
     * @param unrecoverableSeqs 送り直しても直らないと画面が判断した連番（任意）
     */
    public record ChunkManifest(int expectedLastSeq, int expectedCount, List<Integer> uploadedSeqs,
                                Long expectedEndSample, List<Integer> unrecoverableSeqs,
                                /** 旧い画面が申告した「送れた最後の連番」（新しい画面は 0）。 */
                                int lastSeq,
                                /** 旧い画面が申告した「送れた件数」（新しい画面は 0）。 */
                                int totalCount) {

        /** 新しい画面の形（録れた範囲と、送信の応答を受け取れた連番を分けて送る）。 */
        public ChunkManifest(int expectedLastSeq, int expectedCount, List<Integer> uploadedSeqs,
                             Long expectedEndSample, List<Integer> unrecoverableSeqs) {
            this(expectedLastSeq, expectedCount, uploadedSeqs, expectedEndSample, unrecoverableSeqs,
                    0, 0);
        }

        /**
         * 旧い画面の形（「送れた範囲」だけ）。
         *
         * <p>「録れた範囲」は**分からない**（0 のまま）。後端は保存済みの範囲でしか調べられず、
         * 最後の分塊まで届いたことは**証明できない**。</p>
         *
         * @param lastSeq      送れた最後の連番
         * @param totalCount   送れた件数
         * @param endSample    送れた最後の分塊の終わりの位置（16kHz のサンプル数。任意）
         * @param uploadedSeqs 送れた連番そのもの（任意）
         */
        public ChunkManifest(int lastSeq, int totalCount, Long endSample, List<Integer> uploadedSeqs) {
            this(0, 0, uploadedSeqs, endSample, List.of(), lastSeq, totalCount);
        }

        /** 送信の応答を受け取れた連番（**必須の欄**。無ければ空）。 */
        public List<Integer> uploadedSeqs() {
            return uploadedSeqs == null ? List.of() : uploadedSeqs;
        }

        /**
         * 送信の応答を受け取れた連番。
         *
         * <p>旧い画面（{@link #uploadedSeqs()} が空）では `1..lastSeq` とみなす。</p>
         */
        public List<Integer> uploaded() {
            if (uploadedSeqs != null && !uploadedSeqs.isEmpty()) {
                return uploadedSeqs;
            }
            if (lastSeq > 0) {
                List<Integer> derived = new java.util.ArrayList<>();
                for (int seq = 1; seq <= lastSeq; seq += 1) {
                    derived.add(seq);
                }
                return derived;
            }
            return List.of();
        }

        /** 送り直しても直らないと画面が判断した連番（無ければ空）。 */
        public List<Integer> unrecoverable() {
            return unrecoverableSeqs == null ? List.of() : unrecoverableSeqs;
        }

        /** 「実際に録れた範囲」を送ってきた新しい画面か（旧い画面は false）。 */
        public boolean declaresRecordedRange() {
            return expectedLastSeq > 0;
        }

        /**
         * 一覧そのものが矛盾していないか（**調べる前に**断るべき形か）。
         *
         * <p>矛盾していれば**その理由**を返す。問題なければ null（＝{@link #mismatch}）。</p>
         */
        public String inconsistency() {
            return mismatch();
        }

        /**
         * 一覧の矛盾を返す（無ければ null）。
         *
         * <p>見るもの: 負の値・**現実にあり得ない件数**・録れた数と最後の連番の不一致・
         * 送れた連番の重複・録れた範囲の外を「送れた」と言っていないか。</p>
         */
        public String mismatch() {
            if (expectedLastSeq < 0 || expectedCount < 0 || lastSeq < 0 || totalCount < 0) {
                return "録れた数が負の値になっています。";
            }
            if (expectedLastSeq > MAX_CHUNKS) {
                return "録れた分塊の数が上限（" + MAX_CHUNKS + " 件）を超えています。"
                        + "録音を分けてください。";
            }
            if (expectedLastSeq > 0 && expectedCount != expectedLastSeq) {
                // 連番は 1 から連続するので、録れた数 = 最後の連番
                return "録れた数（" + expectedCount + " 件）と最後の連番（" + expectedLastSeq
                        + "）が合いません。";
            }
            if (expectedLastSeq == 0 && lastSeq > 0 && totalCount > 0 && totalCount != lastSeq) {
                // 旧い画面: 「送れた最後の連番」と「送れた数」は一致しているはず
                return "送れた数（" + totalCount + " 件）と最後の連番（" + lastSeq
                        + "）が合いません。";
            }
            List<Integer> counted = uploaded();
            if (counted.isEmpty()) {
                return null;
            }
            java.util.TreeSet<Integer> unique = new java.util.TreeSet<>(counted);
            if (unique.size() != counted.size()) {
                return "送れた連番に重複があります。";
            }
            if (unique.first() < 1) {
                return "送れた連番に 1 未満の値があります。";
            }
            int recordedLast = declaresRecordedRange() ? expectedLastSeq
                    : Math.max(lastSeq, totalCount);
            if (recordedLast > 0 && unique.last() > recordedLast) {
                return "録れた範囲（1〜" + recordedLast + "）の外の連番（" + unique.last()
                        + "）を送れたと申告しています。";
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
            boolean forced,
            /**
             * 画面が申告した**実際に録れた**最後の連番（旧い画面は 0）。
             *
             * <p>0 は「最後の分塊まで届いたことを証明していない」の意味。画面はこれを
             * 「保証された完了」と読み替えない。</p>
             */
            int expectedLastSeq,
            /**
             * 明示の不完全終了で**失った**連番（音声が残っていない範囲）。
             *
             * <p>詳細画面に出し続けるための値（一度きりの通知にしない）。</p>
             */
            List<Integer> lossSeqs,
            /** 失った範囲の理由（種類）。{@link ClassroomModels#CHECK_MISSING} など。 */
            String lossReasonCode) {
    }

    // ------------------------------------------------------------------ 削除

    public record DeleteResult(long recordId, boolean deletedAudio) {
    }
}
