package com.study21.common.core.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.common.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><strong>固定する範囲</strong>: プロンプトの本文とモデルパラメータに加えて、
 * **モデルのスロット（{@code provider}）とそのときのモデル名（{@code model}）**も固定する。
 * スロットだけでは「同じ ID の下でモデル名を変えた」ときに、並んでいる要求が黙って別のモデルへ
 * 移ってしまう（{@code AI_QWEN_MODEL_4} を書き換えるだけで起きる）。秘密（API Key・URL）は
 * **入れない**（実行時に {@link AiModelSlot} から安全に読む）。</p>
 */
public record AiFigureConfig(
        /** 作図モード（A〜D）。 */
        String mode,
        /** バッチコード（例 batC51-A）。履歴用。 */
        String taskCode,
        /** 使用するモデルのスロット（例 qwen:4）。 */
        String provider,
        /**
         * 受付時に固定した**モデル名**（例 qwen3-vl-plus）。
         *
         * <p>null・空は「受付時にモデルを固定できなかった」（`AI_MODEL` が未設定のまま提出された）。
         * その場合は実行時に解決して固定し直す（互換の規則。{@link #parseSnapshot}).</p>
         */
        String model,
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
        String capturedAt,
        /**
         * この要求の**実行版**（受付＝1。送り直し・もう一度生成のたびに +1）。
         *
         * <p>技術的な再試行（働き手の拾い直し・AI の再呼び出し）では増やさない。
         * 「利用者が入力を変えて出し直した」ときだけ増えるので、どの版で作ったかが追える。</p>
         */
        int revision) {

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
        return new AiFigureConfig(mode, taskCode, provider, model, temperature, maxCompletionTokens,
                requestTimeoutSeconds, retryLimit, maxCommands, allowedCommands, outputFormat,
                systemPromptCommon, systemPromptMode, taskTemplate, taskTemplateFrom, capturedAt, revision);
    }

    /** モデル名を固定する（受付時に `AI_MODEL` から読めたとき、実行時に読めたとき）。 */
    public AiFigureConfig withModel(String model) {
        return new AiFigureConfig(mode, taskCode, provider, text(model), temperature, maxCompletionTokens,
                requestTimeoutSeconds, retryLimit, maxCommands, allowedCommands, outputFormat,
                systemPromptCommon, systemPromptMode, taskTemplate, taskTemplateFrom, capturedAt, revision);
    }

    /** 実行版（受付＝1。利用者が入力を作り直したら +1）を差し替える。 */
    public AiFigureConfig withRevision(int revision) {
        return new AiFigureConfig(mode, taskCode, provider, model, temperature, maxCompletionTokens,
                requestTimeoutSeconds, retryLimit, maxCommands, allowedCommands, outputFormat,
                systemPromptCommon, systemPromptMode, taskTemplate, taskTemplateFrom, capturedAt, revision);
    }

    /** モデル名を固定できているか（できていなければ実行時に固定する）。 */
    public boolean hasPinnedModel() {
        return model != null && !model.isBlank();
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
        return resolve(mode, taskCode, values, capturedAt, null, 1);
    }

    /**
     * 設定値から有効な設定を組み立てる（モデル名と実行版も入れる）。
     *
     * @param model    受付時に固定するモデル名（`AI_MODEL` から読めたとき。読めなければ null）
     * @param revision 実行版（受付＝1。利用者が作り直したら +1）
     */
    public static AiFigureConfig resolve(String mode, String taskCode, Map<String, String> values,
                                         String capturedAt, String model, int revision) {
        Map<String, String> raw = values == null ? Map.of() : values;
        String normalizedMode = AiFigureSettingKeys.normalizeMode(mode);

        String common = text(raw.get(AiFigureSettingKeys.SYSTEM_PROMPT));
        if (common == null) {
            throw new ValidationException("AI の共通システムプロンプトが設定されていません"
                    + "（" + AiFigureSettingKeys.SYSTEM_PROMPT + "）。"
                    + "システム設定の「図形管理」で入力してください。");
        }
        String provider = providerOf(raw, normalizedMode);
        if (provider.isEmpty()) {
            // 空のまま固定すると「provider が無い」壊れたスナップショットになる。
            // 受付の時点で理由を返す（あとから CONFIG_SNAPSHOT で失敗させない）
            throw new ValidationException("AI のモデルが設定されていません"
                    + "（" + AiFigureSettingKeys.PROVIDER + "）。"
                    + "システム設定の「図形管理」で選んでください。");
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
                provider,
                text(model),
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
                capturedAt,
                revision < 1 ? 1 : revision);
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
        node.put("model", model);
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
        node.put("revision", revision);
        return node;
    }

    /**
     * スナップショットの読み取り結果。
     *
     * <p><strong>「無い」と「壊れている」を分ける</strong>のがこの型の役目。無い（歴史的な要求）なら
     * いまの設定から作り直してよいが、**あるのに読めない**ときは黙って別の設定で走らせてはならない
     * （利用者が固定した条件と違う条件で AI を呼ぶことになる）。</p>
     */
    public record SnapshotState(Kind kind, AiFigureConfig config, String problem) {

        public enum Kind {
            /** まだ固定していない（列が空・未設定）。いまの設定から作って固定してよい。 */
            ABSENT,
            /** 使える。 */
            VALID,
            /** あるが使えない（壊れている・足りない・版が違う）。**作り直さずに失敗させる**。 */
            BROKEN
        }

        public static SnapshotState absent() {
            return new SnapshotState(Kind.ABSENT, null, null);
        }

        public static SnapshotState valid(AiFigureConfig config) {
            return new SnapshotState(Kind.VALID, config, null);
        }

        public static SnapshotState broken(String problem) {
            return new SnapshotState(Kind.BROKEN, null, problem);
        }

        public boolean isAbsent() {
            return kind == Kind.ABSENT;
        }

        public boolean isBroken() {
            return kind == Kind.BROKEN;
        }

        public boolean isValid() {
            return kind == Kind.VALID;
        }
    }

    /**
     * 要求行の「設定スナップショット」を読む（**無い / 使える / 壊れている**を区別する）。
     *
     * <p>版の扱い（{@link #VERSION}）:</p>
     * <ul>
     *   <li>版 1 が現在の形。{@code config} の本文（プロンプト・パラメータ）が入っている。</li>
     *   <li>版 1 でも {@code model}／{@code revision} が無いものは**前の版が書いたもの**として読む
     *       （{@code model} は「受付時に固定できなかった」、{@code revision} は 1 とみなす）。
     *       プロンプトの本文は入っているので使える。</li>
     *   <li>追跡用のハッシュだけの古い形（{@code config} が無い）は**本文が復元できない**ので
     *       {@link SnapshotState.Kind#BROKEN} にする（黙っていまの設定へは切り替えない）。</li>
     * </ul>
     *
     * <p>プロンプトの本文（{@code systemPromptCommon}）と {@code mode}、
     * {@code taskTemplate} 以外のパラメータが欠けているものも**壊れている**として扱う
     * （欠けたまま既定値で走らせると、固定したはずの条件が変わる）。</p>
     */
    public static SnapshotState parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return SnapshotState.absent();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception cause) {
            return SnapshotState.broken("設定スナップショットを読めません（JSON が壊れています）。");
        }
        if (root == null || !root.isObject()) {
            return SnapshotState.broken("設定スナップショットの形が違います（JSON オブジェクトではありません）。");
        }
        int rootVersion = root.path("version").asInt(0);
        JsonNode config = root.path("config");
        if (!config.isObject()) {
            if (root.has("taskCode") || root.has("systemPromptHash") || root.has("taskTemplateHash")) {
                return SnapshotState.broken("設定スナップショットが古い形式です（プロンプトの本文がありません。"
                        + "版=" + rootVersion + "）。この要求は作り直してください。");
            }
            return SnapshotState.broken("設定スナップショットに本文（config）がありません（版=" + rootVersion + "）。");
        }
        int version = config.path("version").asInt(rootVersion);
        if (version != VERSION) {
            return SnapshotState.broken("設定スナップショットの版が対応していません（版=" + version
                    + "、対応=" + VERSION + "）。この要求は作り直してください。");
        }

        List<String> missing = new ArrayList<>();
        String mode = text(config.path("mode").asText(null));
        require(missing, mode != null, "mode");
        String common = text(config.path("systemPromptCommon").asText(null));
        require(missing, common != null, "systemPromptCommon");
        String provider = text(config.path("provider").asText(null));
        require(missing, provider != null, "provider");
        require(missing, config.hasNonNull("temperature"), "temperature");
        require(missing, config.hasNonNull("maxCompletionTokens"), "maxCompletionTokens");
        require(missing, config.hasNonNull("requestTimeoutSeconds"), "requestTimeoutSeconds");
        require(missing, config.hasNonNull("retryLimit"), "retryLimit");
        require(missing, config.hasNonNull("maxCommands"), "maxCommands");
        require(missing, config.hasNonNull("allowedCommands"), "allowedCommands");
        require(missing, config.hasNonNull("outputFormat"), "outputFormat");
        if (!missing.isEmpty()) {
            return SnapshotState.broken("設定スナップショットに足りない項目があります: "
                    + String.join(", ", missing) + "。");
        }

        return SnapshotState.valid(new AiFigureConfig(
                mode,
                text(config.path("taskCode").asText(null)),
                provider,
                // model は「固定できなかった」を許す（前の版が書いたスナップショット・AI_MODEL 未設定）
                text(config.path("model").asText(null)),
                config.path("temperature").asDouble(DEFAULT_TEMPERATURE),
                config.path("maxCompletionTokens").asInt(DEFAULT_MAX_COMPLETION_TOKENS),
                config.path("requestTimeoutSeconds").asInt(DEFAULT_REQUEST_TIMEOUT_SECONDS),
                config.path("retryLimit").asInt(DEFAULT_RETRY_LIMIT),
                config.path("maxCommands").asInt(DEFAULT_MAX_COMMANDS),
                config.path("allowedCommands").asText(""),
                config.path("outputFormat").asText(DEFAULT_OUTPUT_FORMAT),
                common,
                textOr(config.path("systemPromptMode").asText(null), ""),
                textOr(config.path("taskTemplate").asText(null), ""),
                taskTemplateFromOf(config.path("taskTemplateFrom").asText(null)),
                text(config.path("capturedAt").asText(null)),
                Math.max(1, config.path("revision").asInt(1))));
    }

    private static void require(List<String> missing, boolean present, String name) {
        if (!present) {
            missing.add(name);
        }
    }

    /** 使える形のときだけ読む（読めなければ空。壊れていることを呼び出し側が知る必要があるときは {@link #parseSnapshot}）。 */
    public static Optional<AiFigureConfig> fromSnapshotJson(String json) {
        SnapshotState state = parseSnapshot(json);
        return state.isValid() ? Optional.of(state.config()) : Optional.empty();
    }

    /**
     * 固定した設定でスナップショットを作り直す（**既にある trace は残す**）。
     *
     * <p>「スナップショットが無い歴史的な要求」と「モデル名を固定できていなかった要求」を、
     * **AI を呼ぶ前に**固定するために使う。プロンプトの本文は入れ替わるが、
     * 実行の記録（{@code trace}）は消さない。</p>
     */
    public static String repin(String existingSnapshotJson, AiFigureConfig config) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        root.set("config", config.toJsonNode());
        try {
            JsonNode parsed = existingSnapshotJson == null || existingSnapshotJson.isBlank()
                    ? null : MAPPER.readTree(existingSnapshotJson);
            JsonNode trace = parsed == null ? null : parsed.path("trace");
            if (trace != null && trace.isObject()) {
                root.set("trace", trace);
            }
        } catch (Exception cause) {
            // 壊れた既存の値は捨てる（どうせ読めない。config は作り直す）
            root.remove("trace");
        }
        return write(root);
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
