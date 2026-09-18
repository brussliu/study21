package com.study21.common.core.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.common.core.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AI 生図の**有効な設定**（共通 → モード別の継承を適用した結果）。
 *
 * <p>要求行の「設定スナップショット」にこの形で保存する。**秘密（API Key・URL）は入れない**。
 * プロンプトの本文は**入れる**（ハッシュだけにしない。あとから「どの文面で作ったか」を
 * そのまま読み返せるようにする）。</p>
 *
 * <p>解決（継承）の規則をここに 1 回だけ書く。admin-api（バッチ）と user-api（要求の受付）が
 * **同じ結果**を出すので、Worker が使う版と画面が見せた条件が食い違わない。</p>
 *
 * <p>継承の規則（設計 §6）:</p>
 * <ul>
 *   <li>System Prompt … **共通（必須）＋モード別（任意）を連結**する。モード別で上書きしない
 *       （共通の禁止事項・出力の約束をモード側が消してしまわないため）</li>
 *   <li>タスクテンプレート（User Prompt）… モード別があればそれ、無ければ共通、どちらも無ければ「無し」
 *       （**共通の User Prompt は必須ではない**）</li>
 *   <li>モデルパラメータ … モード別 → 共通の順に採用し、どちらも無ければ既定値</li>
 * </ul>
 */
public record AiFigureConfig(
        /** 作図モード（A〜D）。 */
        String mode,
        /** バッチコード（例 batC51-A）。履歴用。 */
        String taskCode,
        /** 使用するモデルのスロット。 */
        String provider,
        double temperature,
        int maxCompletionTokens,
        int requestTimeoutSeconds,
        int retryLimit,
        int maxCommands,
        String allowedCommands,
        String outputFormat,
        /** 共通の System Prompt（必須）。 */
        String systemPromptCommon,
        /** モード別の System Prompt（空なら共通だけを使う）。 */
        String systemPromptMode,
        /** タスクテンプレート（User Prompt。空なら「無し」）。 */
        String taskTemplate,
        /** タスクテンプレートをどこから採ったか。 */
        TaskTemplateFrom taskTemplateFrom,
        /** この版を固定した時刻（ISO-8601。画面の確認用）。 */
        String capturedAt) {

    /** スナップショットの形式の版（形を変えたら上げる）。 */
    public static final int VERSION = 1;

    /** タスクテンプレートをどこから採ったか。 */
    public enum TaskTemplateFrom {
        /** モード別のタスクテンプレート。 */
        MODE,
        /** 共通のタスクテンプレート。 */
        COMMON,
        /** どちらも無い（テンプレート無しで実行する）。 */
        NONE
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 既定値（`TBL_COM_設定情報_init.sql` の seed と同じ）。 */
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_COMPLETION_TOKENS = 4096;
    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 120;
    public static final int DEFAULT_RETRY_LIMIT = 0;
    public static final int DEFAULT_MAX_COMMANDS = 80;
    public static final String DEFAULT_OUTPUT_FORMAT = "JSON";

    /** モード別の System Prompt を使うか。 */
    public boolean systemFromMode() {
        return systemPromptMode != null && !systemPromptMode.isBlank();
    }

    /** モード別のタスクテンプレートを使うか。 */
    public boolean taskFromMode() {
        return taskTemplateFrom == TaskTemplateFrom.MODE;
    }

    /** タスクテンプレートがあるか（無ければテンプレート無しで実行する）。 */
    public boolean hasTaskTemplate() {
        return taskTemplate != null && !taskTemplate.isBlank();
    }

    /** バッチコードだけを差し替える（admin-api が要求行のモードから決める）。 */
    public AiFigureConfig withTaskCode(String taskCode) {
        return new AiFigureConfig(mode, taskCode, provider, temperature, maxCompletionTokens,
                requestTimeoutSeconds, retryLimit, maxCommands, allowedCommands, outputFormat,
                systemPromptCommon, systemPromptMode, taskTemplate, taskTemplateFrom, capturedAt);
    }

    /**
     * 設定値（`COM_設定情報` の生の値）から有効な設定を組み立てる。
     *
     * @param mode      作図モード（A〜D。null は共通だけを使う）
     * @param taskCode  バッチコード（履歴用。null 可）
     * @param values    設定キー → 値（{@link AiFigureSettingKeys#keysFor(String)} の分）
     * @param capturedAt この版を固定した時刻（ISO-8601。null 可）
     * @throws ValidationException 共通の System Prompt が未設定のとき
     */
    public static AiFigureConfig resolve(String mode, String taskCode, Map<String, String> values,
                                         String capturedAt) {
        Map<String, String> raw = values == null ? Map.of() : values;
        String normalizedMode = AiFigureSettingKeys.normalizeMode(mode);

        String common = text(raw.get(AiFigureSettingKeys.SYSTEM_PROMPT));
        if (common == null) {
            throw new ValidationException("AI の共通システムプロンプトが設定されていません"
                    + "（" + AiFigureSettingKeys.SYSTEM_PROMPT + "）。"
                    + "システム設定の「図形管理」で入力してください。");
        }
        String modeSystem = normalizedMode.isEmpty() ? null
                : text(raw.get(AiFigureSettingKeys.systemPromptKey(normalizedMode)));

        String commonTask = text(raw.get(AiFigureSettingKeys.INSTRUCTION_TEMPLATE));
        String modeTask = normalizedMode.isEmpty() ? null
                : text(raw.get(AiFigureSettingKeys.taskTemplateKey(normalizedMode)));
        TaskTemplateFrom from = modeTask != null ? TaskTemplateFrom.MODE
                : (commonTask != null ? TaskTemplateFrom.COMMON : TaskTemplateFrom.NONE);

        return new AiFigureConfig(
                normalizedMode.isEmpty() ? null : normalizedMode,
                taskCode,
                providerOf(raw, normalizedMode),
                number(raw, normalizedMode, AiFigureSettingKeys.SUFFIX_TEMPERATURE,
                        AiFigureSettingKeys.TEMPERATURE, DEFAULT_TEMPERATURE),
                (int) number(raw, normalizedMode, AiFigureSettingKeys.SUFFIX_MAX_COMPLETION_TOKENS,
                        AiFigureSettingKeys.MAX_COMPLETION_TOKENS, DEFAULT_MAX_COMPLETION_TOKENS),
                (int) number(raw, normalizedMode, AiFigureSettingKeys.SUFFIX_REQUEST_TIMEOUT_SECONDS,
                        AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, DEFAULT_REQUEST_TIMEOUT_SECONDS),
                (int) number(raw, normalizedMode, AiFigureSettingKeys.SUFFIX_RETRY_LIMIT,
                        AiFigureSettingKeys.RETRY_LIMIT, DEFAULT_RETRY_LIMIT),
                (int) number(raw, normalizedMode, null,
                        AiFigureSettingKeys.MAX_COMMANDS, DEFAULT_MAX_COMMANDS),
                textOr(raw.get(AiFigureSettingKeys.ALLOWED_COMMANDS), ""),
                textOr(raw.get(AiFigureSettingKeys.OUTPUT_FORMAT), DEFAULT_OUTPUT_FORMAT),
                common,
                modeSystem == null ? "" : modeSystem,
                from == TaskTemplateFrom.NONE ? "" : (from == TaskTemplateFrom.MODE ? modeTask : commonTask),
                from,
                capturedAt);
    }

    /** 使用モデル（モード別 → 共通）。 */
    private static String providerOf(Map<String, String> values, String mode) {
        return textOr(modeText(values, mode, AiFigureSettingKeys.SUFFIX_PROVIDER,
                AiFigureSettingKeys.PROVIDER), "");
    }

    /** モード別が優先、無ければ共通、どちらも無ければ既定値（{@code suffix} が null なら共通だけ）。 */
    private static double number(Map<String, String> values, String mode, String suffix,
                                 String commonKey, double fallback) {
        Double modeValue = suffix == null ? null : parseDoubleOrNull(modeRaw(values, mode, suffix));
        if (modeValue != null) {
            return modeValue;
        }
        return parseDouble(values.get(commonKey), fallback);
    }

    /** モード別の生の値（モードが無ければ null）。 */
    private static String modeRaw(Map<String, String> values, String mode, String suffix) {
        if (mode == null || mode.isEmpty()) {
            return null;
        }
        return values.get(AiFigureSettingKeys.modeKey(mode, suffix));
    }

    /** モード別 → 共通の順に採ったテキスト。 */
    private static String modeText(Map<String, String> values, String mode, String suffix, String commonKey) {
        String modeValue = text(modeRaw(values, mode, suffix));
        return modeValue != null ? modeValue : text(values.get(commonKey));
    }

    private static Double parseDoubleOrNull(String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException cause) {
            return null;
        }
    }

    private static double parseDouble(String value, double fallback) {
        Double parsed = parseDoubleOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String textOr(String value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    // ------------------------------------------------------------ スナップショット

    /**
     * **追跡用のハッシュ**を足した JSON を作る（秘密は入れない）。
     *
     * <p>要求行の「設定スナップショット」は 1 列なので、有効な設定（{@code config}）と
     * 実行時の確定結果（{@code trace}）を 1 つの入れ物に入れる。{@code config} は**要求の受付時に
     * 固定**したもの、{@code trace} は生成時に分かったこと（使ったモデル・確定した結果種別・版のハッシュ）。
     * 後から {@code trace} を書いても {@code config} は書き換えない。</p>
     */
    public String toSnapshotJson(ObjectNode trace) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        root.set("config", toJsonNode());
        if (trace != null) {
            root.set("trace", trace);
        }
        return write(root);
    }

    /** この設定だけの JSON（入れ物無し）。 */
    public ObjectNode toJsonNode() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("version", VERSION);
        node.put("mode", mode);
        node.put("taskCode", taskCode);
        node.put("provider", provider);
        node.put("temperature", temperature);
        node.put("maxCompletionTokens", maxCompletionTokens);
        node.put("requestTimeoutSeconds", requestTimeoutSeconds);
        node.put("retryLimit", retryLimit);
        node.put("maxCommands", maxCommands);
        node.put("allowedCommands", allowedCommands);
        node.put("outputFormat", outputFormat);
        node.put("systemPromptCommon", systemPromptCommon);
        node.put("systemPromptMode", systemPromptMode);
        node.put("taskTemplate", taskTemplate);
        node.put("taskTemplateFrom", taskTemplateFrom == null ? null : taskTemplateFrom.name());
        node.put("capturedAt", capturedAt);
        return node;
    }

    /** 要求行の「設定スナップショット」（JSON 文字列）から読む。読めなければ空。 */
    public static Optional<AiFigureConfig> fromSnapshotJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode config = root.path("config");
            if (!config.isObject()) {
                // 形が違う（古い版・手で書いた行）ときは読まない＝呼び出し側が今の設定で解決する
                return Optional.empty();
            }
            String common = text(config.path("systemPromptCommon").asText(null));
            if (common == null) {
                return Optional.empty();
            }
            return Optional.of(new AiFigureConfig(
                    text(config.path("mode").asText(null)),
                    text(config.path("taskCode").asText(null)),
                    textOr(config.path("provider").asText(null), ""),
                    config.path("temperature").asDouble(DEFAULT_TEMPERATURE),
                    config.path("maxCompletionTokens").asInt(DEFAULT_MAX_COMPLETION_TOKENS),
                    config.path("requestTimeoutSeconds").asInt(DEFAULT_REQUEST_TIMEOUT_SECONDS),
                    config.path("retryLimit").asInt(DEFAULT_RETRY_LIMIT),
                    config.path("maxCommands").asInt(DEFAULT_MAX_COMMANDS),
                    textOr(config.path("allowedCommands").asText(null), ""),
                    textOr(config.path("outputFormat").asText(null), DEFAULT_OUTPUT_FORMAT),
                    common,
                    textOr(config.path("systemPromptMode").asText(null), ""),
                    textOr(config.path("taskTemplate").asText(null), ""),
                    taskTemplateFromOf(config.path("taskTemplateFrom").asText(null)),
                    text(config.path("capturedAt").asText(null))));
        } catch (Exception cause) {
            return Optional.empty();
        }
    }

    /** 既にあるスナップショットへ追跡用の情報だけを足す（{@code config} はそのまま残す）。 */
    public static String withTrace(String snapshotJson, ObjectNode trace) {
        ObjectNode root;
        try {
            JsonNode parsed = snapshotJson == null || snapshotJson.isBlank()
                    ? null : MAPPER.readTree(snapshotJson);
            root = parsed != null && parsed.isObject() ? (ObjectNode) parsed : MAPPER.createObjectNode();
        } catch (Exception cause) {
            root = MAPPER.createObjectNode();
        }
        root.put("version", VERSION);
        if (trace != null) {
            root.set("trace", trace);
        }
        return write(root);
    }

    /** 画面・履歴に出す「どの版で作ったか」の要約（読みやすい形）。 */
    public Map<String, String> describe() {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("作図モード", mode == null ? "（歴史的な要求＝ A）" : mode);
        rows.put("使用モデル", provider);
        rows.put("System Prompt", systemFromMode() ? "共通＋モード別" : "共通のみ");
        rows.put("User Prompt", switch (taskTemplateFrom == null ? TaskTemplateFrom.NONE : taskTemplateFrom) {
            case MODE -> "モード別";
            case COMMON -> "共通";
            case NONE -> "テンプレート無し";
        });
        rows.put("コマンド数の上限", String.valueOf(maxCommands));
        rows.put("固定した時刻", capturedAt == null ? "" : capturedAt);
        return rows;
    }

    private static TaskTemplateFrom taskTemplateFromOf(String value) {
        if (value == null) {
            return TaskTemplateFrom.NONE;
        }
        try {
            return TaskTemplateFrom.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            return TaskTemplateFrom.NONE;
        }
    }

    private static String write(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception cause) {
            throw new IllegalStateException("設定スナップショットを作れませんでした。", cause);
        }
    }
}
