package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図の**有効な設定**を解決する（**共通の設定を既定とし、モード別の設定で上書きする**）。
 *
 * <p>画面（設定ページの「図形管理」）には共通規則と A〜D の 4 組がある。モード別の項目が
 * 未設定・空のときは**共通をそのまま使う**ので、利用者は全部を書かなくてよい（設計 §6）。
 * ただし System Prompt だけは例外で、**共通＋モード別を連結**する（共通の禁止事項・出力の
 * 約束をモード側が消してしまわないため。{@link AiFigureConfig}）。</p>
 *
 * <p><strong>必須は共通の分だけ</strong>で、共用の User Prompt（`GEOMETRY_AI_INSTRUCTION_TEMPLATE`）は
 * 必須ではない（モード別に書いてあればそれを使うし、どちらも無ければテンプレート無しで実行する）。</p>
 *
 * <p>解決の順番（**要求ごとに固定する**）:</p>
 * <ol>
 *   <li>要求行の「設定スナップショット」があれば**それを使う**（受付時に固定した版。
 *       待ち行列に並んでいる間に設定を変えても、その要求の条件は変わらない）</li>
 *   <li>無ければ**いまの設定**から解決し、その結果を要求行へ書く（歴史的な要求・移行前の行）</li>
 * </ol>
 */
@Component
public class FigureProcessorSettings {

    private static final Logger log = LoggerFactory.getLogger(FigureProcessorSettings.class);

    /** 設定ページの区分。 */
    public static final String PAGE_CODE = AiFigureSettingKeys.PAGE;

    private final SettingsService settingsService;
    private final AiResponseSchemaService schemaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FigureProcessorSettings(SettingsService settingsService, AiResponseSchemaService schemaService) {
        this.settingsService = settingsService;
        this.schemaService = schemaService;
    }

    /**
     * このプロセッサが実行に必要とする設定（**共通の分だけを必須にする**）。
     *
     * <p>共用の User Prompt（`GEOMETRY_AI_INSTRUCTION_TEMPLATE`）は**入っていない**。
     * 共通の System Prompt とモデルの設定、検証の許可リスト・上限だけが必須。</p>
     */
    public static List<SettingRequirement> requiredSettings() {
        return List.of(
                requirement(AiFigureSettingKeys.ENABLED),
                requirement(AiFigureSettingKeys.PROVIDER),
                requirement(AiFigureSettingKeys.OUTPUT_FORMAT),
                requirement(AiFigureSettingKeys.SYSTEM_PROMPT),
                requirement(AiFigureSettingKeys.TEMPERATURE),
                requirement(AiFigureSettingKeys.MAX_COMPLETION_TOKENS),
                requirement(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS),
                requirement(AiFigureSettingKeys.RETRY_LIMIT),
                requirement(AiFigureSettingKeys.MAX_COMMANDS),
                requirement(AiFigureSettingKeys.ALLOWED_COMMANDS));
    }

    private static SettingRequirement requirement(String key) {
        return new SettingRequirement(PAGE_CODE, key);
    }

    /** モード別の項目キー（設定ページの案内にも使う）。 */
    public static List<String> modeSettingKeys(FigureProcessor processor) {
        return AiFigureSettingKeys.modeKeys(processor.mode().name());
    }

    /**
     * **いまの設定**から解決する。
     *
     * <p>要求行に固定した版を使うかどうかの判断は {@link AiFigureTaskConfigResolver}（1 か所）が行う。
     * ここは「いまの設定を読んで組み立てる」だけにして、**設定の入口を 2 つにしない**
     * （実行の入口が先に現在の設定を見て、あとからスナップショットを読む、という順番を作らない）。</p>
     */
    public AiFigureConfig resolve(FigureProcessor processor) {
        return liveResolve(processor);
    }

    /** いまの設定値を読んで解決する（足りない・不正なら理由の分かる例外）。 */
    public AiFigureConfig liveResolve(FigureProcessor processor) {
        Map<String, String> values = new LinkedHashMap<>(
                settingsService.requireSettings(processor.taskCode(), requiredSettings()));
        // モード別の項目は任意（未設定・空なら共通を継承する）
        for (String key : AiFigureSettingKeys.modeKeys(processor.mode().name())) {
            settingsService.findGlobal(PAGE_CODE, key).ifPresent(value -> values.put(key, value));
        }
        return AiFigureConfig.resolve(processor.mode().name(), processor.taskCode(), values,
                OffsetDateTime.now().toString());
    }

    /**
     * 要求行へ書く**設定スナップショット**を作る（設計 §6）。
     *
     * <p>{@code config} は受付時に固定した**有効な設定の本文**（プロンプトそのもの。秘密は入らない）、
     * {@code trace} は生成時に分かったこと（使ったモデル・確定した結果種別・版のハッシュ）。
     * 既に固定した {@code config} があるときは**上書きしない**（後から別の設定になって見えると
     * 「どの条件で作ったか」が分からなくなるため）。</p>
     */
    public String snapshot(FigureProcessor processor, AiFigureConfig config, String existingSnapshotJson,
                           FigureOutputType requested, FigureOutputType resolvedType, String modelName,
                           String configuredModel, boolean modelFromPinned) {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("requestedOutputType",
                requested == null ? FigureOutputType.defaultType().name() : requested.name());
        trace.put("resolvedOutputType", resolvedType == null ? null : resolvedType.name());
        trace.put("systemPromptFrom", config.systemFromMode() ? "COMMON+MODE" : "COMMON");
        trace.put("taskTemplateFrom", config.taskTemplateFrom() == null ? null : config.taskTemplateFrom().name());
        trace.put("systemPromptHash", hash(config.systemPromptCommon() + "\n\n" + config.systemPromptMode()));
        trace.put("taskTemplateHash", hash(config.taskTemplate()));
        trace.put("outputSchemaHash", hash(schemaService.schemaJsonOf(processor.dtoClass())));
        trace.put("allowedCommandsHash", hash(config.allowedCommands()));
        trace.put("maxCommands", config.maxCommands());
        trace.put("outputFormat", config.outputFormat());
        trace.put("provider", config.provider());
        trace.put("model", modelName);
        trace.put("configuredModel", configuredModel);
        trace.put("modelFromPinned", modelFromPinned);
        trace.put("configRevision", config.revision());
        trace.put("pinnedAt", config.capturedAt());
        trace.put("generatedAt", OffsetDateTime.now().toString());

        // 既に固定した config があればそのまま残す（無ければ今解決したものを固定する）
        String base = AiFigureConfig.fromSnapshotJson(existingSnapshotJson).isPresent()
                ? existingSnapshotJson : config.toSnapshotJson(null);
        return AiFigureConfig.withTrace(base, trace);
    }

    /** 設定の版を表す短いハッシュ（SHA-256 の先頭 16 桁）。空は null。 */
    static String hash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index += 1) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256 が使えません。", cause);
        }
    }

    /** 設定の不足を画面へ伝えるための案内（テストと診断用）。 */
    public Map<String, String> describe(AiFigureConfig config) {
        return config.describe();
    }
}
