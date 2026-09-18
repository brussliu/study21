package com.study21.admin.geometryai.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.FigureProcessorSettings;
import com.study21.admin.geometryai.FigurePromptBuilder;
import com.study21.admin.geometryai.FigurePromptTemplate;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.geometryai.dto.BatC51AResultDto;
import com.study21.admin.geometryai.dto.BatC51BResultDto;
import com.study21.admin.geometryai.dto.BatC51CResultDto;
import com.study21.admin.geometryai.dto.BatC51DResultDto;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A〜D のプロセッサ登録・モード別設定の継承・プロンプトの組み立て。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>4 つが独立して登録され、バッチコードと出力 DTO がモードごとに違う（重複は起動時に落とす）</li>
 *   <li>歴史的な {@code batC51} は A として解決する</li>
 *   <li>**共通の System Prompt は必須、共用の User Prompt は必須ではない**</li>
 *   <li>System Prompt は**共通＋モード別を連結**する（モード側で上書きしない）</li>
 *   <li>モード別の設定は「未設定・空なら共通を継承」する（モデルパラメータ）</li>
 *   <li>モードと結果種別がプロンプトに入り、**未展開の変数をモデルへ送らない**</li>
 *   <li>スナップショットは**本文を持ち**（ハッシュだけにしない）、鍵は入らない</li>
 *   <li>要求行に固定した版があれば、いまの設定を変えても**その版を使う**</li>
 * </ol>
 */
class FigureProcessorRegistryTest {

    private SettingsService settingsService;
    private FigureProcessorSettings processorSettings;
    private FigurePromptBuilder promptBuilder;

    private final FigureProcessorA processorA = new FigureProcessorA();
    private final FigureProcessorB processorB = new FigureProcessorB();
    private final FigureProcessorC processorC = new FigureProcessorC();
    private final FigureProcessorD processorD = new FigureProcessorD();

    private FigureProcessorRegistry registry;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        processorSettings = new FigureProcessorSettings(settingsService,
                new AiResponseSchemaService(new ObjectMapper()));
        promptBuilder = new FigurePromptBuilder();
        registry = new FigureProcessorRegistry(List.of(processorA, processorB, processorC, processorD));

        Map<String, String> common = new LinkedHashMap<>();
        common.put(AiFigureSettingKeys.ENABLED, "true");
        common.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        common.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        common.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のシステムプロンプト。");
        common.put(AiFigureSettingKeys.TEMPERATURE, "0.2");
        common.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "4096");
        common.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        common.put(AiFigureSettingKeys.RETRY_LIMIT, "1");
        common.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        common.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(common);
        when(settingsService.findGlobal(eq(AiFigureSettingKeys.PAGE), anyString())).thenReturn(Optional.empty());
    }

    /** モード別の設定を 1 つだけ有効にする。 */
    private void modeSetting(String key, String value) {
        when(settingsService.findGlobal(AiFigureSettingKeys.PAGE, key)).thenReturn(Optional.of(value));
    }

    private FigurePromptBuilder.PromptInput emptyInput(FigureMode mode, FigureOutputType type) {
        return new FigurePromptBuilder.PromptInput(mode, type, null, List.of(), null,
                null, null, null, 80, "", "JSON");
    }

    // ------------------------------------------------------------------ 登録

    @Test
    @DisplayName("A〜D が独立して登録され、バッチコードと出力 DTO がモードごとに違う")
    void registersFourProcessors() {
        assertThat(registry.modes()).containsExactly(FigureMode.A, FigureMode.B, FigureMode.C, FigureMode.D);
        assertThat(registry.taskCodes())
                .containsExactly("batC51-A", "batC51-B", "batC51-C", "batC51-D");
        assertThat(registry.of(FigureMode.A).dtoClass()).isEqualTo(BatC51AResultDto.class);
        assertThat(registry.of(FigureMode.B).dtoClass()).isEqualTo(BatC51BResultDto.class);
        assertThat(registry.of(FigureMode.C).dtoClass()).isEqualTo(BatC51CResultDto.class);
        assertThat(registry.of(FigureMode.D).dtoClass()).isEqualTo(BatC51DResultDto.class);
        // 設定キーもモードごとに違う（名前の定義は common-core の 1 か所）
        assertThat(registry.of(FigureMode.A).systemPromptKey()).isEqualTo("GEOMETRY_AI_A_SYSTEM_PROMPT");
        assertThat(registry.of(FigureMode.D).taskTemplateKey()).isEqualTo("GEOMETRY_AI_D_TASK_TEMPLATE");
        assertThat(FigureProcessorSettings.modeSettingKeys(processorA))
                .containsExactly("GEOMETRY_AI_A_SYSTEM_PROMPT", "GEOMETRY_AI_A_TASK_TEMPLATE",
                        "GEOMETRY_AI_A_PROVIDER", "GEOMETRY_AI_A_TEMPERATURE",
                        "GEOMETRY_AI_A_MAX_COMPLETION_TOKENS", "GEOMETRY_AI_A_REQUEST_TIMEOUT_SECONDS",
                        "GEOMETRY_AI_A_RETRY_LIMIT");
        // B は結果種別を選ばせない（GRAPH 固定）。A・C・D は選ばせる
        assertThat(registry.of(FigureMode.B).mode().asksOutputType()).isFalse();
        assertThat(registry.of(FigureMode.A).mode().asksOutputType()).isTrue();
    }

    @Test
    @DisplayName("同じモード・同じバッチコードの二重登録は起動時に落とす")
    void rejectsDuplicateRegistration() {
        assertThatThrownBy(() -> new FigureProcessorRegistry(List.of(processorA, new FigureProcessorA())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 つ登録");
    }

    @Test
    @DisplayName("モード別バッチのコードから解決する。裸の batC51 は解決しない")
    void resolvesLegacyTaskCode() {
        assertThat(registry.ofTaskCode("batC51-A")).contains(processorA);
        assertThat(registry.ofTaskCode("batC51-C")).contains(processorC);
        // モードが無い時代の裸の batC51 はもう無い（バッチも削除済み。利用者の指示）
        assertThat(registry.ofTaskCode("batC51")).isEmpty();
        assertThat(registry.ofTaskCode("batC52")).isEmpty();
        assertThat(FigureProcessorSettings.requiredSettings()).isNotEmpty();
    }

    // ------------------------------------------------------------------ 必須設定

    @Test
    @DisplayName("必須は共通の System Prompt まで。共用の User Prompt は必須にしない")
    void requiresCommonSystemPromptButNotCommonUserPrompt() {
        processorSettings.resolve(processorC);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettingRequirement>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(settingsService).requireSettings(eq("batC51-C"), captor.capture());
        List<String> keys = captor.getValue().stream().map(SettingRequirement::settingKey).toList();
        assertThat(keys).contains(AiFigureSettingKeys.SYSTEM_PROMPT, AiFigureSettingKeys.PROVIDER,
                AiFigureSettingKeys.TEMPERATURE, AiFigureSettingKeys.MAX_COMMANDS,
                AiFigureSettingKeys.ALLOWED_COMMANDS);
        // 共用の User Prompt（タスクテンプレート）は必須ではない
        assertThat(keys).doesNotContain(AiFigureSettingKeys.INSTRUCTION_TEMPLATE);
        // モード別の項目も必須ではない（未設定なら共通を継承する）
        assertThat(keys).noneMatch(key -> key.startsWith("GEOMETRY_AI_C_"));
    }

    // ------------------------------------------------------------------ 設定の継承

    @Test
    @DisplayName("モード別の System Prompt は共通を上書きせず、連結される")
    void appendsModeSystemPromptToCommon() {
        AiFigureConfig commonOnly = processorSettings.resolve(processorA);
        assertThat(commonOnly.systemFromMode()).isFalse();
        assertThat(commonOnly.systemPromptCommon()).isEqualTo("共通のシステムプロンプト。");

        modeSetting("GEOMETRY_AI_A_SYSTEM_PROMPT", "A 用のシステムプロンプト。");
        AiFigureConfig withMode = processorSettings.resolve(processorA);
        assertThat(withMode.systemFromMode()).isTrue();
        assertThat(withMode.systemPromptCommon()).isEqualTo("共通のシステムプロンプト。");
        assertThat(withMode.systemPromptMode()).isEqualTo("A 用のシステムプロンプト。");

        String prompt = promptBuilder.buildSystemPrompt(processorA, withMode, FigureOutputType.AUTO, null);
        assertThat(prompt).startsWith("共通のシステムプロンプト。");
        assertThat(prompt).contains("A 用のシステムプロンプト。");
        assertThat(prompt.indexOf("共通のシステムプロンプト。"))
                .isLessThan(prompt.indexOf("A 用のシステムプロンプト。"));
        // タスクテンプレートは書いていないので「テンプレート無し」
        assertThat(withMode.taskFromMode()).isFalse();
        assertThat(withMode.taskTemplateFrom()).isEqualTo(AiFigureConfig.TaskTemplateFrom.NONE);
    }

    @Test
    @DisplayName("モード別の User Prompt があれば、共用の User Prompt が空でもそれを使う")
    void usesModeTaskTemplateWhenCommonIsEmpty() {
        modeSetting("GEOMETRY_AI_A_TASK_TEMPLATE", "{modeLabel} として作図してください。最大 {maxCommands} 個。");

        AiFigureConfig config = processorSettings.resolve(processorA);

        assertThat(config.taskFromMode()).isTrue();
        assertThat(config.taskTemplate()).contains("{modeLabel}");
        String prompt = promptBuilder.buildUserPrompt(processorA, config, emptyInput(FigureMode.A,
                FigureOutputType.AUTO));
        assertThat(prompt).contains("画像をもとに再現 として作図してください。最大 80 個。");
        assertThat(prompt).doesNotContain("{");
    }

    @Test
    @DisplayName("モデルパラメータはモード別が優先で、未設定なら共通を継承する")
    void inheritsModelParameters() {
        AiFigureConfig inherited = processorSettings.resolve(processorB);
        assertThat(inherited.provider()).isEqualTo("qwen:4");
        assertThat(inherited.temperature()).isEqualTo(0.2);
        assertThat(inherited.maxCompletionTokens()).isEqualTo(4096);
        assertThat(inherited.requestTimeoutSeconds()).isEqualTo(120);
        assertThat(inherited.retryLimit()).isEqualTo(1);
        assertThat(inherited.maxCommands()).isEqualTo(80);

        modeSetting("GEOMETRY_AI_B_PROVIDER", "qwen:2");
        modeSetting("GEOMETRY_AI_B_TEMPERATURE", "0.9");
        modeSetting("GEOMETRY_AI_B_MAX_COMPLETION_TOKENS", "8192");
        modeSetting("GEOMETRY_AI_B_REQUEST_TIMEOUT_SECONDS", "300");
        modeSetting("GEOMETRY_AI_B_RETRY_LIMIT", "0");
        AiFigureConfig overridden = processorSettings.resolve(processorB);
        assertThat(overridden.provider()).isEqualTo("qwen:2");
        assertThat(overridden.temperature()).isEqualTo(0.9);
        assertThat(overridden.maxCompletionTokens()).isEqualTo(8192);
        assertThat(overridden.requestTimeoutSeconds()).isEqualTo(300);
        assertThat(overridden.retryLimit()).isZero();
        // A は触っていないので共通のまま
        assertThat(processorSettings.resolve(processorA).provider()).isEqualTo("qwen:4");
    }

    // ------------------------------------------------------------------ プロンプト

    @Test
    @DisplayName("system プロンプトにモードの役割と結果種別の守り方が入る（設定が共通でも効く）")
    void systemPromptCarriesModeAndResultType() {
        AiFigureConfig config = processorSettings.resolve(processorB);
        String prompt = promptBuilder.buildSystemPrompt(processorB, config, FigureOutputType.AUTO, null);

        assertThat(prompt).contains("共通のシステムプロンプト。");
        assertThat(prompt).contains("B 数式からグラフを作成");
        // B は GRAPH 固定（利用者が AUTO を選んでいても GRAPH として扱う）
        assertThat(prompt).contains("GRAPH");
        assertThat(prompt).contains("黙って変えず");
        // A の役割は入らない
        assertThat(prompt).doesNotContain("A 画像をもとに再現");
    }

    @Test
    @DisplayName("利用者が指定した結果種別の規則がプロンプトに入る（幾何だけに閉じない）")
    void systemPromptFollowsRequestedOutputType() {
        AiFigureConfig config = processorSettings.resolve(processorC);
        String geometry = promptBuilder.buildSystemPrompt(processorC, config, FigureOutputType.GEOMETRY, "");
        assertThat(geometry).contains("図形だけ");

        String mixed = promptBuilder.buildSystemPrompt(processorC, config, FigureOutputType.MIXED, "");
        assertThat(mixed).contains("図形とグラフの両方");

        String auto = promptBuilder.buildSystemPrompt(processorC, config, FigureOutputType.AUTO, "");
        assertThat(auto).contains("確定図種");
    }

    @Test
    @DisplayName("User Prompt は変数を展開し、空の任意項目には既定値を入れる")
    void rendersUserPromptWithDefaults() {
        modeSetting("GEOMETRY_AI_D_TASK_TEMPLATE", "{modeLabel} として作図（最大 {maxCommands} 個）");
        AiFigureConfig config = processorSettings.resolve(processorD);
        String prompt = promptBuilder.buildUserPrompt(processorD, config, new FigurePromptBuilder.PromptInput(
                FigureMode.D, FigureOutputType.MIXED, "  ", List.of(), null, null, null, null, 80,
                config.allowedCommands(), "JSON"));

        assertThat(prompt).contains("文章と図を合わせて作図").contains("最大 80 個");
        // 未展開の変数を残さない
        assertThat(prompt).doesNotContain("{");
    }

    @Test
    @DisplayName("User Prompt が共通にもモードにも無くても、プロンプトを組み立てられる")
    void rendersUserPromptWithoutAnyTemplate() {
        AiFigureConfig config = processorSettings.resolve(processorA);
        assertThat(config.hasTaskTemplate()).isFalse();

        String prompt = promptBuilder.buildUserPrompt(processorA, config, new FigurePromptBuilder.PromptInput(
                FigureMode.A, FigureOutputType.GEOMETRY, "垂線も入れて", List.of("再現の重点: 数学的な関係を優先"),
                null, null, null, null, 80, config.allowedCommands(), "JSON"));

        // テンプレートが無くても、利用者の指定は必ずモデルへ届く
        assertThat(prompt).contains("作成する図の種類: GEOMETRY（幾何図形）");
        assertThat(prompt).contains("再現の重点: 数学的な関係を優先");
        assertThat(prompt).contains("補足要求: 垂線も入れて");
        assertThat(prompt).contains("名前とラベル: はい（");
        assertThat(prompt).doesNotContain("{");
    }

    @Test
    @DisplayName("補充項目は項目ごとの変数でも入る（画面の項目がそのままプロンプトの変数になる）")
    void rendersSupplementVariables() {
        modeSetting("GEOMETRY_AI_A_TASK_TEMPLATE",
                "重点: {reproduceFocus}\n不足時: {whenInsufficient}\n既知: {knownValues}\n範囲: {coordinateRange}");
        AiFigureConfig config = processorSettings.resolve(processorA);

        String prompt = promptBuilder.buildUserPrompt(processorA, config, new FigurePromptBuilder.PromptInput(
                FigureMode.A, FigureOutputType.GRAPH, null,
                List.of("再現の重点: 数学的な関係を優先", "情報が足りないとき: 確認してから進める",
                        "既知の値（式・点・寸法・角）: AB = 5"),
                null, null, null, null, 80, config.allowedCommands(), "JSON"));

        assertThat(prompt).contains("重点: 数学的な関係を優先");
        assertThat(prompt).contains("不足時: 確認してから進める");
        assertThat(prompt).contains("既知: AB = 5");
        // 指定していない項目は既定値（「指定なし」）が入る
        assertThat(prompt).contains("範囲: 指定なし");
        assertThat(prompt).doesNotContain("{");
    }

    @Test
    @DisplayName("補充項目と「元の名前を残すか」がプロンプトに入る")
    void rendersSupplementsAndLabelPolicy() {
        AiFigureConfig config = processorSettings.resolve(processorA);
        String prompt = promptBuilder.buildUserPrompt(processorA, config, new FigurePromptBuilder.PromptInput(
                FigureMode.A, FigureOutputType.GRAPH, "補足あり",
                List.of("表示範囲: x = -5..5", "再現の重点: 数学的な関係を優先"), Boolean.FALSE,
                null, null, null, 80, "", "JSON"));

        assertThat(prompt).contains("補足あり");
        assertThat(prompt).contains("表示範囲: x = -5..5");
        assertThat(prompt).contains("数学的な関係を優先");
        assertThat(prompt).contains("いいえ（元の名前・ラベルにこだわらず");
    }

    @Test
    @DisplayName("知らない変数は展開せずに例外にする（未展開のままモデルへ送らない）")
    void rejectsUnknownVariable() {
        Map<String, String> values = new LinkedHashMap<>();
        assertThatThrownBy(() -> FigurePromptTemplate.render("作図: {unknownVar}", values, "GEOMETRY_AI_TEST"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("{unknownVar}")
                .hasMessageContaining("{maxCommands}");
    }

    @Test
    @DisplayName("テンプレートに書かれた変数はすべて登録済み（設定ページの説明と一致する）")
    void everyRegisteredVariableIsUsable() {
        // 登録された変数はどれも展開できる（説明だけあって使えない変数を作らない）
        for (String name : FigurePromptTemplate.variables().keySet()) {
            String template = "前 {" + name + "} 後";
            assertThat(FigurePromptTemplate.render(template, Map.of(name, "値"), "TEST"))
                    .isEqualTo("前 値 後");
        }
        // 画面の補充項目（common-core の定義）もすべて変数として登録されている
        for (com.study21.common.core.geometryai.AiFigureSupplements.Item item
                : com.study21.common.core.geometryai.AiFigureSupplements.all()) {
            assertThat(FigurePromptTemplate.variables()).containsKey(item.variable());
        }
        assertThat(FigurePromptTemplate.unknownVariables("作図: {unknownVar}")).containsExactly("unknownVar");
    }

    @Test
    @DisplayName("歴史的なテンプレートの変数（kind / figureType）も展開できる")
    void rendersLegacyVariables() {
        modeSetting("GEOMETRY_AI_A_TASK_TEMPLATE",
                "{kind} として作図（タイプ {figureType}、補足 {note}、最大 {maxCommands}）");
        AiFigureConfig config = processorSettings.resolve(processorA);
        String prompt = promptBuilder.buildUserPrompt(processorA, config, new FigurePromptBuilder.PromptInput(
                FigureMode.A, FigureOutputType.AUTO, null, List.of(), null,
                "FIGURE", "TRIANGLE", "geometry", 80, "", "JSON"));

        assertThat(prompt).startsWith("図形（三角形） として作図（タイプ 幾何図形、補足 なし、最大 80）");
        // テンプレートが触れていない結果種別は後ろに足される（利用者の指定を落とさない）
        assertThat(prompt).contains("作成する図の種類: AUTO（自動判定）");
        assertThat(prompt).doesNotContain("{");
    }

    // ------------------------------------------------------------------ スナップショット

    @Test
    @DisplayName("スナップショットは本文を持ち（ハッシュだけにしない）、鍵は入らない")
    void snapshotKeepsPromptBodiesWithoutSecrets() {
        modeSetting("GEOMETRY_AI_A_SYSTEM_PROMPT", "A 用のシステムプロンプト。");
        modeSetting("GEOMETRY_AI_A_TASK_TEMPLATE", "{note} を反映してください。");
        AiFigureConfig config = processorSettings.resolve(processorA);

        String snapshot = processorSettings.snapshot(processorA, config, null,
                FigureOutputType.AUTO, FigureOutputType.GRAPH, "qwen-vl-max");

        assertThat(snapshot)
                .contains("\"mode\":\"A\"")
                .contains("\"taskCode\":\"batC51-A\"")
                .contains("\"systemPromptCommon\":\"共通のシステムプロンプト。\"")
                .contains("\"systemPromptMode\":\"A 用のシステムプロンプト。\"")
                .contains("\"taskTemplate\":\"{note} を反映してください。\"")
                .contains("\"systemPromptFrom\":\"COMMON+MODE\"")
                .contains("\"requestedOutputType\":\"AUTO\"")
                .contains("\"resolvedOutputType\":\"GRAPH\"")
                .contains("\"model\":\"qwen-vl-max\"")
                .contains("\"outputSchemaHash\"");
        // 秘密（API キー・URL）は入れない
        assertThat(snapshot).doesNotContain("secret").doesNotContain("apiKey").doesNotContain("http");
        // どの文面で作ったかが読み返せる（ハッシュだけにしない）
        assertThat(AiFigureConfig.fromSnapshotJson(snapshot)).isPresent();
    }

    @Test
    @DisplayName("要求行に固定した版があれば、いまの設定を変えてもその版を使う")
    void usesPinnedConfigWhenPresent() {
        AiFigureConfig pinned = processorSettings.resolve(processorA);

        // 待ち行列に並んでいる間に設定を変える（別のプロンプト・別の上限）
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put(AiFigureSettingKeys.ENABLED, "true");
        changed.put(AiFigureSettingKeys.PROVIDER, "qwen:9");
        changed.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        changed.put(AiFigureSettingKeys.SYSTEM_PROMPT, "あとから書き換えたプロンプト。");
        changed.put(AiFigureSettingKeys.MAX_COMMANDS, "5");
        changed.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(changed);

        AiFigureConfig restored = processorSettings.resolve(processorA, pinned.toSnapshotJson(null));

        assertThat(restored.systemPromptCommon()).isEqualTo("共通のシステムプロンプト。");
        assertThat(restored.provider()).isEqualTo("qwen:4");
        assertThat(restored.maxCommands()).isEqualTo(80);
        assertThat(restored.allowedCommands()).isEqualTo("Point,Segment,Polygon");
        assertThat(restored.taskCode()).isEqualTo("batC51-A");
    }

    @Test
    @DisplayName("固定した版が無い（歴史的な）行は、いまの設定から解決する")
    void resolvesLiveWhenNothingIsPinned() {
        assertThat(processorSettings.resolve(processorA, null).systemPromptCommon())
                .isEqualTo("共通のシステムプロンプト。");
        assertThat(processorSettings.resolve(processorA, "壊れた JSON").provider()).isEqualTo("qwen:4");
        assertThat(processorSettings.resolve(processorA, "{}").maxCommands()).isEqualTo(80);
    }

    @Test
    @DisplayName("固定した版を書いても、既にある本文は上書きしない")
    void keepsPinnedBodyWhenWritingTrace() {
        AiFigureConfig pinned = processorSettings.resolve(processorA);
        String first = processorSettings.snapshot(processorA, pinned, null,
                FigureOutputType.AUTO, FigureOutputType.GEOMETRY, "model-1");

        // あとから設定を変えて、別の版で trace を書く（本文は受付時のまま）
        Map<String, String> changed = new LinkedHashMap<>();
        changed.put(AiFigureSettingKeys.ENABLED, "true");
        changed.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        changed.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        changed.put(AiFigureSettingKeys.SYSTEM_PROMPT, "書き換えたプロンプト。");
        changed.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        changed.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(changed);

        String second = processorSettings.snapshot(processorA, processorSettings.resolve(processorA), first,
                FigureOutputType.AUTO, FigureOutputType.GRAPH, "model-2");

        assertThat(second).contains("\"systemPromptCommon\":\"共通のシステムプロンプト。\"");
        assertThat(second).doesNotContain("書き換えたプロンプト。");
        assertThat(second).contains("\"model\":\"model-2\"");
        assertThat(second).contains("\"resolvedOutputType\":\"GRAPH\"");
    }
}
