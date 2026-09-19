package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.geometryai.processor.FigureProcessorA;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 設定の入口（{@link AiFigureTaskConfigResolver}）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>固定した設定があればそれを使い、**いまの設定は読まない**</li>
 *   <li>固定が無ければいまの設定から作る（固定が必要だと知らせる）</li>
 *   <li>壊れている・モードが食い違うときは**失敗**（いまの設定へ切り替えない）</li>
 *   <li>モデル名を固定できていないときは、解決したモデル名を固定する</li>
 *   <li>検証の工程用（{@code resolveConfig}）は**接続を解決しない**（AI を呼ばないので）</li>
 * </ol>
 */
class AiFigureTaskConfigResolverTest {

    private SettingsService settingsService;
    private GeometryAiConnectionResolver connectionResolver;
    private AiFigureTaskConfigResolver resolver;

    private final FigureProcessorA processor = new FigureProcessorA();

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        connectionResolver = mock(GeometryAiConnectionResolver.class);
        FigureProcessorSettings processorSettings = new FigureProcessorSettings(settingsService,
                new AiResponseSchemaService(new ObjectMapper()));
        resolver = new AiFigureTaskConfigResolver(processorSettings, connectionResolver);

        Map<String, String> current = new LinkedHashMap<>();
        current.put(AiFigureSettingKeys.ENABLED, "true");
        current.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        current.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        current.put(AiFigureSettingKeys.SYSTEM_PROMPT, "いまの共通ルール。");
        current.put(AiFigureSettingKeys.TEMPERATURE, "0.2");
        current.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "4096");
        current.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        current.put(AiFigureSettingKeys.RETRY_LIMIT, "0");
        current.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        current.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point");
        when(settingsService.requireSettings(anyString(), any())).thenReturn(current);
        when(settingsService.findGlobal(eq(AiFigureSettingKeys.PAGE), anyString())).thenReturn(Optional.empty());
        when(connectionResolver.resolve(anyString(), anyString(), any())).thenReturn(
                new GeometryAiConnectionResolver.AiConnection("qwen", "qwen-vl-max",
                        "https://example.com", "secret"));
    }

    private GeometryAiRequestEntity request(String snapshotJson) {
        GeometryAiRequestEntity entity = new GeometryAiRequestEntity();
        entity.setRequestId(1L);
        entity.setRequestNo("AIG202609191200001234");
        entity.setStatusCode("PREPROCESSED");
        entity.setMode("A");
        entity.setSettingsSnapshotJson(snapshotJson);
        entity.setVersion(2);
        return entity;
    }

    private static String snapshot(Map<String, String> values, String mode, String model, int revision) {
        return AiFigureConfig.resolve(mode, "batC51-" + mode, values, "2026-09-19T00:00:00", model, revision)
                .toSnapshotJson(null);
    }

    private static Map<String, String> submitted() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "提出時のルール。");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "40");
        values.put(AiFigureSettingKeys.TEMPERATURE, "0.9");
        values.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        return values;
    }

    @Test
    @DisplayName("固定した設定があればそれを使い、いまの設定は読まない")
    void usesTheSnapshotWithoutReadingLiveSettings() {
        AiFigureTaskConfigResolver.AiFigureTaskConfig resolved = resolver.resolve(processor,
                request(snapshot(submitted(), "A", "qwen-vl-max", 2)));

        assertThat(resolved.fromSnapshot()).isTrue();
        assertThat(resolved.needsPin()).isFalse();
        assertThat(resolved.config().systemPromptCommon()).isEqualTo("提出時のルール。");
        assertThat(resolved.config().temperature()).isEqualTo(0.9);
        assertThat(resolved.config().revision()).isEqualTo(2);
        assertThat(resolved.config().taskCode()).isEqualTo("batC51-A");
        verify(settingsService, never()).requireSettings(anyString(), any());
    }

    @Test
    @DisplayName("固定が無ければいまの設定から作り、固定が必要だと知らせる")
    void resolvesLiveSettingsWhenNothingIsPinned() {
        AiFigureTaskConfigResolver.AiFigureTaskConfig resolved = resolver.resolve(processor, request(null));

        assertThat(resolved.fromSnapshot()).isFalse();
        assertThat(resolved.needsPin()).isTrue();
        assertThat(resolved.config().systemPromptCommon()).isEqualTo("いまの共通ルール。");
        assertThat(resolved.config().revision()).isEqualTo(1);
        // モデルは解決できたものを固定する
        assertThat(resolved.config().model()).isEqualTo("qwen-vl-max");
    }

    @Test
    @DisplayName("壊れている・モードが食い違うときは失敗する（いまの設定へ切り替えない）")
    void failsOnBrokenOrMismatchedSnapshot() {
        assertThatThrownBy(() -> resolver.resolve(processor, request("{\"version\":1,\"config\":{}}")))
                .isInstanceOf(AiFigureConfigException.class)
                .hasMessageContaining("固定した設定を使えません");

        assertThatThrownBy(() -> resolver.resolve(processor, request(snapshot(submitted(), "C", "m", 1))))
                .isInstanceOf(AiFigureConfigException.class)
                .hasMessageContaining("作図モード");

        verify(settingsService, never()).requireSettings(anyString(), any());
    }

    @Test
    @DisplayName("モデル名を固定できていなければ、解決したモデル名を固定する")
    void pinsTheModelWhenItIsMissing() {
        AiFigureTaskConfigResolver.AiFigureTaskConfig resolved = resolver.resolve(processor,
                request(snapshot(submitted(), "A", null, 1)));

        assertThat(resolved.needsPin()).isTrue();
        assertThat(resolved.config().model()).isEqualTo("qwen-vl-max");
        assertThat(resolved.config().revision()).isEqualTo(1);
        assertThat(resolved.config().systemPromptCommon()).isEqualTo("提出時のルール。");
    }

    @Test
    @DisplayName("検証の工程用は接続を解決しない（AI を呼ばないのでモデル設定が無くても止まらない）")
    void configOnlyResolutionDoesNotTouchTheConnection() {
        AiFigureTaskConfigResolver.AiFigureTaskConfig resolved = resolver.resolveConfig(processor,
                request(snapshot(submitted(), "A", "qwen-vl-max", 1)));

        assertThat(resolved.connection()).isNull();
        assertThat(resolved.config().allowedCommands()).isEqualTo("Point,Segment");
        verify(connectionResolver, never()).resolve(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("固定した設定のモデルはそのまま接続解決へ渡す（いまの設定で上書きしない）")
    void passesThePinnedModelToTheConnectionResolver() {
        resolver.resolve(processor, request(snapshot(submitted(), "A", "qwen-vl-max", 1)));

        verify(connectionResolver).resolve(eq("batC51-A"), eq("qwen:4"), eq("qwen-vl-max"));
    }
}
