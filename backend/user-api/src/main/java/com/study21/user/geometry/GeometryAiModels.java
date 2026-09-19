package com.study21.user.geometry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * AI 生図（図形管理）と AI 画図助手のモデル。
 *
 * <p>画面（`views/geometry/GeometryAiView.vue` / `GeometryDrawView.vue`）の語彙をそのまま使う:
 * `kind`（figure / function / mixed）、`subKind`（triangle / circle / quad / other）、
 * `crop`（x / y / w / h の正規化 0..1）。**変換表を作らせないため名前を変えない。**</p>
 *
 * <p>AI の成果物は**GeoGebra のコマンド列まで**。XML とサムネイルは作図画面の applet が作り、
 * 保存は既存の `GeometryService.create`（`POST /figures` と同じ処理）を通す。</p>
 */
public final class GeometryAiModels {

    private GeometryAiModels() {
    }

    // ------------------------------------------------------------------ 状態

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PREPROCESSING = "PREPROCESSING";
    public static final String STATUS_PREPROCESSED = "PREPROCESSED";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_VALIDATING = "VALIDATING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_REGISTERED = "REGISTERED";
    /** AI が質問を返した（利用者が答えて送り直す）。**失敗ではない。** */
    public static final String STATUS_NEEDS_INPUT = "NEEDS_INPUT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final List<String> STATUSES = List.of(STATUS_QUEUED, STATUS_PREPROCESSING, STATUS_PREPROCESSED,
            STATUS_GENERATING, STATUS_GENERATED, STATUS_VALIDATING, STATUS_READY, STATUS_REGISTERED,
            STATUS_NEEDS_INPUT, STATUS_FAILED, STATUS_CANCELLED);

    /**
     * 状態の日本語ラベル（画面にそのまま出す）。
     *
     * <p>表示は**実際の処理段階**（待機中 / 読み取り中 / 生成中 / 検証中 / 追加入力待ち / 失敗 / 完了）。
     * パーセントは出さない（段階だけを見せる）。</p>
     *
     * <p><strong>「保存」と書いてよいのは本当に保存できたときだけ</strong>:
     * `READY` は「生成済み・確認待ち」＝**まだ図形になっていない**、`REGISTERED` が
     * 「保存完了」。検証（`VALIDATING`）も**保存はしない**（コマンドの規則を確かめているだけ）ので
     * 「検証・保存中」とは書かない。</p>
     */
    public static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry(STATUS_QUEUED, "待機中（順番待ち）"),
            Map.entry(STATUS_PREPROCESSING, "画像を読み取り中"),
            Map.entry(STATUS_PREPROCESSED, "読み取り済み（AI の順番待ち）"),
            Map.entry(STATUS_GENERATING, "AI が生成中"),
            Map.entry(STATUS_GENERATED, "生成済み（検証待ち）"),
            Map.entry(STATUS_VALIDATING, "検証中（コマンドの規則を確認しています）"),
            Map.entry(STATUS_READY, "生成済み・確認待ち"),
            Map.entry(STATUS_REGISTERED, "保存完了（図形として保存済み）"),
            Map.entry(STATUS_NEEDS_INPUT, "追加入力待ち（AI からの質問）"),
            Map.entry(STATUS_FAILED, "失敗"),
            Map.entry(STATUS_CANCELLED, "取消"));

    /** カードの状態（画面の一覧で使う 8 種類）。 */
    public static final List<String> CARD_STATUSES = List.of(
            "WAITING", "READING", "GENERATING", "VALIDATING", "NEEDS_INPUT", "READY", "FAILED", "SAVED");

    /**
     * 状態コード → カードの状態（画面の一覧の絞り込みと見出しに使う）。
     *
     * <p>処理中の細かい段階はまとめるが、**「確認待ち（READY）」と「保存完了（REGISTERED）」は
     * 分ける**（利用者が「保存できたのか」を一覧で見分けられなければならない）。</p>
     */
    public static String cardStatusOf(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_QUEUED, STATUS_PREPROCESSED -> "WAITING";
            case STATUS_PREPROCESSING -> "READING";
            case STATUS_GENERATING, STATUS_GENERATED -> "GENERATING";
            case STATUS_VALIDATING -> "VALIDATING";
            case STATUS_NEEDS_INPUT -> "NEEDS_INPUT";
            case STATUS_READY -> "READY";
            case STATUS_REGISTERED -> "SAVED";
            case STATUS_FAILED -> "FAILED";
            default -> "FAILED";
        };
    }

    /** カードの状態の日本語ラベル。 */
    public static final Map<String, String> CARD_STATUS_LABELS = Map.of(
            "WAITING", "待機中",
            "READING", "画像を読み取り中",
            "GENERATING", "AI が生成中",
            "VALIDATING", "検証中",
            "NEEDS_INPUT", "追加入力待ち",
            "READY", "生成済み・確認待ち",
            "SAVED", "保存完了",
            "FAILED", "失敗");

    /** 処理中か（画面のポーリングを続けるか）。 */
    public static boolean isProcessing(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_QUEUED, STATUS_PREPROCESSING, STATUS_PREPROCESSED, STATUS_GENERATING,
                 STATUS_GENERATED, STATUS_VALIDATING -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------ モードと結果種別

    /** 作図モード（画面の A〜D）。 */
    public static final List<String> MODES = List.of("A", "B", "C", "D");
    /** 要求できる結果種別（AUTO が既定）。 */
    public static final List<String> OUTPUT_TYPES = List.of("AUTO", "GEOMETRY", "GRAPH", "MIXED");
    /** モードごとの結果種別の固定（B は GRAPH。他は利用者が選ぶ）。 */
    public static final Map<String, String> FIXED_OUTPUT_TYPE = Map.of("B", "GRAPH");
    /** 結果種別の日本語ラベル。 */
    public static final Map<String, String> OUTPUT_TYPE_LABELS = Map.of(
            "AUTO", "自動判定",
            "GEOMETRY", "幾何図形",
            "GRAPH", "関数・方程式のグラフ",
            "MIXED", "図形とグラフの組み合わせ");

    // ---------------------------------------------------------------- 分類

    /** 画面の kind（大分類）。 */
    public static final String KIND_FIGURE = "FIGURE";
    public static final String KIND_FUNCTION = "FUNCTION";
    public static final String KIND_MIXED = "MIXED";
    public static final List<String> USER_KINDS = List.of(KIND_FIGURE, KIND_FUNCTION, KIND_MIXED);

    /** 画面の subKind（図形の種類。利用者区分が FIGURE のときだけ入る）。 */
    public static final List<String> USER_SUB_KINDS = List.of("TRIANGLE", "CIRCLE", "QUAD", "OTHER");

    /** AI が判定した大分類（UNKNOWN を含む）。 */
    public static final List<String> AI_KINDS = List.of(KIND_FIGURE, KIND_FUNCTION, KIND_MIXED, "UNKNOWN");

    /** 失敗工程（設計 §2.3）。 */
    public static final String STAGE_PREPROCESS = "PREPROCESS";
    public static final String STAGE_GENERATE = "GENERATE";
    public static final String STAGE_VALIDATE = "VALIDATE";
    public static final Map<String, String> STAGE_LABELS = Map.of(
            STAGE_PREPROCESS, "画像の取込",
            STAGE_GENERATE, "AI の生成",
            STAGE_VALIDATE, "生成結果の検証");

    // ---------------------------------------------------------------- 制限

    /** 補足指示の上限（画面の NOTE_MAX と同じ）。 */
    public static final int NOTE_MAX = 300;
    /** 画像として受け付ける MIME（画面の IMAGE_ACCEPT と同じ）。 */
    public static final List<String> IMAGE_MIME_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    /** 画像として受け付ける拡張子。 */
    public static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    /** 切り抜きの最小の大きさ（画面の CROP_MIN と同じ）。 */
    public static final double CROP_MIN = 0.05;
    /** 1 ページの既定件数（履歴）。 */
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    /** 図形管理の一覧に出すタスクの既定件数・上限。 */
    public static final int DEFAULT_TASK_LIMIT = 20;
    public static final int MAX_TASK_LIMIT = 50;

    /** AI 画図助手の指示文の上限。 */
    public static final int INSTRUCTION_MAX = 500;

    public static final String IMAGE_KIND_ORIGINAL = "original";
    public static final String IMAGE_KIND_CROPPED = "cropped";

    /** 配信する画像（Content-Type を添えて返す）。 */
    public record ImageData(byte[] bytes, String mime, String fileName) {
    }

    /** 登録元コード（AI 生図から作った図形）。 */
    public static final String SOURCE_AI = "AI";
    /** 更新元コード（AI 生図のバッチ／画面）。 */
    public static final String SOURCE_BATCH = "BATCH";

    // ------------------------------------------------------------------ 画面

    /** 画面が使う設定（有効／無効・上限・既定値）。**API キーは返さない。** */
    public record OptionsResult(
            boolean enabled,
            boolean assistEnabled,
            int maxImageMb,
            int maxImagePixels,
            String defaultCrop,
            String defaultKind,
            String approval,
            /** 1 アカウント 1 日の生図回数（0 = 無制限） */
            int dailyLimit,
            /** 今日すでに作った件数 */
            long usedToday,
            /** 使えないときの理由（日本語。空なら使える） */
            String notice) {
    }

    // ------------------------------------------------------------ 画像アップロード

    /** アップロードした元画像の情報。`imageToken` を `/requests` に渡す。 */
    public record UploadResult(
            String imageToken,
            String fileName,
            String mime,
            long size,
            int width,
            int height) {
    }

    // ------------------------------------------------------------ 作成・状態

    /** 切り抜き範囲（元画像に対する正規化 0..1。画面の CropRect と同じ）。 */
    public record CropInput(Double x, Double y, Double w, Double h) {
    }

    /**
     * 補充パラメータ（画面がモードと結果種別に応じて出す**任意**の項目）。
     *
     * <p>当てはまらない項目は**保存しない**（{@link GeometryAiSupplements} がモードと種類で絞る）。
     * 画面は隠れている項目を送らないので、ここに値があっても適用外なら無視する。</p>
     */
    public record SupplementInput(
            /** A: 再現の重点（MATH_FIRST / APPEARANCE_FIRST） */
            String reproduceFocus,
            /** A・C: 情報が足りないとき（ASK_FIRST / ALLOW_APPROXIMATE。既定は ASK_FIRST） */
            String whenInsufficient,
            /** A: 既知の値（式・点・寸法・角など） */
            String knownValues,
            /** A・C・D（GRAPH / MIXED のとき）: 座標の範囲・目盛 */
            String coordinateRange,
            /** B: 式の訂正 */
            String formulaCorrection,
            /** B・C・D（GRAPH / MIXED のとき）: パラメータの値 */
            String parameters,
            /** B・C・D（GRAPH / MIXED のとき）: 定義域 */
            String domain,
            /** B・C・D（GRAPH / MIXED のとき）: 表示範囲 */
            String viewRange,
            /** B: 補助的な対象を足すか（既定は足さない） */
            Boolean showAuxiliary,
            /** C: 与えられた条件だけ（GIVEN_ONLY）/ 作図を完成（COMPLETE_CONSTRUCTION）。D: 再現（REPRODUCE）/ 変換（TRANSFORM） */
            String goal,
            /** C: 問題文の訂正 */
            String problemCorrection,
            /** D: 残す対象 */
            String keepObjects,
            /** D: 追加・変更する対象 */
            String changeObjects,
            /** D: 文字・式・ラベルの訂正 */
            String textCorrection,
            /** 公共: 元の名前とラベルを残すか（既定は true） */
            Boolean keepLabels) {
    }

    /** リクエスト作成（画面の送信前の確認と同じ内容）。 */
    public record CreateRequest(
            @NotBlank(message = "画像が選ばれていません。")
            String imageToken,
            CropInput crop,
            /** 作図モード（A / B / C / D）。小文字も受ける */
            String mode,
            /** 作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）。B は無視して GRAPH にする */
            String resultType,
            /** モードと種類に当てはまる補充パラメータ（任意） */
            SupplementInput supplements,
            /**
             * （歴史的な画面用）画面の kind（figure / function / mixed）。
             *
             * <p>モードが無い要求（古い画面・キャッシュされた bundle）は今までどおり受け付け、
             * モード A ＋ この分類から結果種別を読み替える。</p>
             */
            String kind,
            /** （歴史的な画面用）画面の subKind。kind が figure のときだけ */
            String subKind,
            @Size(max = NOTE_MAX, message = "補足要求は300文字以内で入力してください。")
            String note) {
    }

    /**
     * 追加入力待ち・失敗した要求を**条件を直して送り直す**（同じ要求行を使い回す）。
     *
     * <p>画像はもうサーバーにあるので、アップロードし直さない。読み取る範囲・作図方法・種類・
     * 補充・補足要求を更新して `QUEUED` に戻す（あとは働き手が実行する）。**行は増やさない**ので、
     * 一覧に古いタスクと新しいタスクが二重に並ぶことがなく、図形も 1 つしかできない。</p>
     */
    public record ResubmitRequest(
            CropInput crop,
            String mode,
            String resultType,
            SupplementInput supplements,
            @Size(max = NOTE_MAX, message = "補足要求は300文字以内で入力してください。")
            String note,
            @NotNull(message = "バージョンを指定してください。") Integer version) {
    }

    /** 作成・再試行・取消の結果（画面はこれを受けてポーリングを始める）。 */
    public record RequestStatus(
            long requestId,
            String requestNo,
            String status,
            String statusLabel,
            int version,
            /** AI を呼ぶ入口（admin-api）へ渡す URL（画面が 1 回だけ呼ぶ） */
            String runPath) {
    }

    /** 履歴の 1 行（生成コマンドと画像は返さない）。 */
    public record RequestRow(
            long requestId,
            String requestNo,
            String status,
            String statusLabel,
            /** 画面の一覧で使う 8 種類のカード状態（WAITING / READING / GENERATING / VALIDATING / NEEDS_INPUT / READY / FAILED / SAVED） */
            String cardStatus,
            String cardStatusLabel,
            /** 作図モード（A〜D。歴史的な要求は null） */
            String mode,
            String modeLabel,
            /** 利用者が指定した結果種別（AUTO / GEOMETRY / GRAPH / MIXED） */
            String requestedOutputType,
            /** 実際に作る種類（GEOMETRY / GRAPH / MIXED。決まらないときは null） */
            String resolvedOutputType,
            String userKind,
            String userSubKind,
            String figureType,
            String note,
            /** 失敗した工程（PREPROCESS / GENERATE / VALIDATE。成功なら null） */
            String failedStage,
            String errorCode,
            String errorMessage,
            Long figureId,
            int retryCount,
            String createdAt,
            String updatedAt) {
    }

    public record RequestListResult(
            List<RequestRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    /** AI からの質問（追加入力待ちのとき）。 */
    public record Question(
            String id,
            String question,
            /** 選択肢（空なら自由記述） */
            List<String> options,
            /** TEXT / NUMBER / CHOICE / CONFIRM */
            String answerKind) {
    }

    /** 図形管理の一覧に出す AI 生図のタスク 1 件（**図形として保存済は出さない**＝重複表示しない）。 */
    public record TaskRow(
            long requestId,
            String requestNo,
            String status,
            String statusLabel,
            String cardStatus,
            String cardStatusLabel,
            String mode,
            String modeLabel,
            String requestedOutputType,
            String resolvedOutputType,
            String title,
            String description,
            /** 追加入力待ちの質問の件数（カードに「質問 2 件」と出す） */
            int questionCount,
            /** 完了したタスクが作った図形（まだ無ければ null） */
            Long figureId,
            /** 失敗した工程（PREPROCESS / GENERATE / VALIDATE。成功なら null） */
            String failedStage,
            String errorCode,
            String errorMessage,
            boolean hasCroppedImage,
            int retryCount,
            /** 楽観的ロックの版数（【削除】などカードからの操作に使う） */
            int version,
            String createdAt,
            String updatedAt) {
    }

    public record TaskListResult(List<TaskRow> items, long totalElements, int limit) {
    }

    /** AI が提案した図形名・タグ・メモ・認識テキスト。 */
    public record Proposal(
            String title,
            List<String> tags,
            String memo,
            /** 画像から読み取った数式・文字（「三角形ABC」など） */
            String recognized) {
    }

    /** 失敗の内容（画面は工程別の案内を出す）。 */
    public record ErrorInfo(String stage, String stageLabel, String code, String message) {
    }

    /** どのバッチ実行がこの要求を処理したか（設計 §3.1 の 3 列）。 */
    public record ExecutionIds(Long preprocess, Long ai, Long validate) {
    }

    /** ポーリングの主対象（画面が必要とする情報を 1 回で返す）。 */
    public record RequestDetail(
            long requestId,
            String requestNo,
            String status,
            String statusLabel,
            /** 画面の一覧で使う 7 種類のカード状態 */
            String cardStatus,
            String cardStatusLabel,
            /** 作図モード（A〜D。歴史的な要求は null） */
            String mode,
            String modeLabel,
            /** 利用者が指定した結果種別（AUTO / GEOMETRY / GRAPH / MIXED） */
            String requestedOutputType,
            /** 実際に作る種類（GEOMETRY / GRAPH / MIXED。決められないときは null） */
            String resolvedOutputType,
            /** モードと種類に当てはまる補充パラメータ（項目名 → 値。**当てはまらない項目は入らない**） */
            Map<String, String> supplements,
            /** AI の判定（GENERATABLE / NEEDS_INPUT / UNSUPPORTED）。AI の出力であり実行結果ではない */
            String outcome,
            /** 追加入力待ちのときの質問（利用者が答えて送り直す） */
            List<Question> questions,
            String userKind,
            String userSubKind,
            String aiKind,
            /** geometry / function（appName を決める） */
            String figureType,
            String note,
            /** READY 以降だけ入る（1 行 1 コマンド） */
            List<String> commands,
            Proposal proposal,
            ErrorInfo error,
            /** 検証で落ちた理由（FAILED(VALIDATE) のとき。画面は参考として出す） */
            String validationError,
            ExecutionIds executionIds,
            Long figureId,
            int retryCount,
            int version,
            boolean hasOriginalImage,
            boolean hasCroppedImage,
            boolean retryable,
            boolean cancellable,
            String createdAt,
            String updatedAt) {
    }

    // ------------------------------------------------------------ 再試行・取消

    public record VersionRequest(@NotNull(message = "バージョンを指定してください。") Integer version) {
    }

    // ---------------------------------------------------------------- 確定

    /**
     * 図形として登録する（作図画面で確認・調整したあとの保存）。
     *
     * <p>中身は `GeometryModels.FigureSaveRequest` と同じ（画面が XML とサムネイルを作る）。
     * `登録元コード='AI'` を付けるため専用の入口にしてある。</p>
     */
    public record ConfirmRequest(
            @NotBlank(message = "図形名を入力してください。")
            @Size(max = GeometryModels.TITLE_MAX, message = "図形名は120文字以内で入力してください。")
            String title,
            @Size(max = 20, message = "図形の種類の指定が正しくありません。")
            String figureType,
            String memo,
            List<String> tags,
            String construction,
            String thumbnail,
            @NotNull(message = "バージョンを指定してください。") Integer version) {
    }

    public record ConfirmResult(RequestRow request, GeometryModels.FigureRow figure, String message) {
    }

    // ---------------------------------------------------------------- 助手

    /** AI 画図助手への指示（作図画面）。 */
    public record AssistRequest(
            /** 編集中の図形（新規作図中は null） */
            Long figureId,
            /** いまの作図データ（XML）。プロンプトの {objects} の予備（画面が一覧を送ればそちらを使う） */
            String construction,
            /**
             * いまの作図のオブジェクト一覧（画面が作る「名前 = 定義（型）」の改行区切り）。
             *
             * <p>XML には「コマンドで作った図形の定義」が残らないことがあり（実測: 円は type と
             * 行列だけで、中心・半径が読めない）、AI が円の中心などを推測できなかった。
             * 画面は applet から定義文字列を取れるので、それを渡す（古い画面は null＝XML の名前で代用）。</p>
             */
            String objects,
            @NotBlank(message = "AI への指示を入力してください。")
            @Size(max = INSTRUCTION_MAX, message = "AI への指示は500文字以内で入力してください。")
            String instruction,
            /**
             * 反映方法（古い画面との互換のために受け取るだけ。記録用）。
             *
             * <p><b>新しい依頼は常に APPEND（今の作図に追加）で記録する</b>（利用者の指示: 作り直したい
             * ときは利用者が自分で【全消去】する）。キャッシュされた古い画面が REPLACE を送ってきても
             * 作り直しにはならない。値は記録用で、作図を消すかどうかは画面が決める。</p>
             */
            String mode,
            /**
             * 前の案が実行できなかった内容（実行できなかった行と理由）。
             *
             * <p>AI の案が GeoGebra に拒否されたとき、画面は**同じ指示をもう一度**この項目つきで送る
             * （＝自動で 1 回だけ直させる）。AI は自分の失敗を見て修正できる。</p>
             */
            String failure) {
    }

    /** 助手の結果（画面は【反映】で作図に適用し、【破棄】で捨てる）。 */
    public record AssistResult(
            long assistId,
            List<String> commands,
            /** AI が返した日本語の説明 */
            String description,
            Long callId) {
    }

    /** 助手への依頼を作った結果（AI はまだ呼ばない。batC52 のキューに入っただけ）。 */
    public record AssistStatus(
            long assistId,
            String status,
            String statusLabel,
            /** admin-api の薄い入口（batC52 を 1 回だけ叩く URL） */
            String runPath) {
    }

    /** 助手の依頼の現在の状態（ポーリングの主対象）。 */
    public record AssistDetail(
            long assistId,
            String status,
            String statusLabel,
            /** READY のときだけ入る（1 行 1 コマンド） */
            List<String> commands,
            /** AI が返した日本語の説明（READY のとき） */
            String description,
            String errorCode,
            String errorMessage,
            Integer commandCount,
            /**
             * 指示を出す**前**の作図データ（XML）。無ければ null。
             *
             * <p>画面の【戻す】はこれへ戻す。1 件ずつ取るので一覧には載せない（重いため）。</p>
             */
            String beforeXml) {
    }

    /**
     * 助手の履歴の 1 件（画面の会話ログ 1 ステップ）。
     *
     * <p>会話ログを**端末をまたいで**見せるためのもの。`指示前XML`（【戻す】の戻り先）は
     * 重いので含めず、`beforeXmlAvailable` で有無だけ伝える（必要なら `GET /assist/{id}`）。</p>
     */
    public record AssistHistoryItem(
            long assistId,
            String instruction,
            /** APPEND（今の作図に追加）/ REPLACE（作図全体を作り直す） */
            String mode,
            /** PENDING / GENERATING / READY / FAILED（AI の生成状態） */
            String status,
            String statusLabel,
            /** SUGGESTED / APPLIED / REJECTED / FAILED（作図に反映したか） */
            String applyKind,
            /** READY のときだけ入る（1 行 1 コマンド） */
            List<String> commands,
            String description,
            String errorCode,
            String errorMessage,
            Integer commandCount,
            /** 【戻す】で使う「指示前XML」を持っているか */
            boolean beforeXmlAvailable,
            String createdAt,
            String updatedAt) {
    }

    /** 図形ごとの履歴（古い順。画面はそのまま会話ログとして描く）。 */
    public record AssistHistoryResult(
            List<AssistHistoryItem> items,
            /** この図形の履歴の件数（返した件数。上限で切られている場合は limit と同じ） */
            int count,
            int limit) {
    }

    /** 助手の依頼を実行する admin-api の薄い入口の URL。 */
    public static String assistRunPath(long assistId) {
        return "/api/admin/batch/geometry-assist/" + assistId + "/run";
    }
}
