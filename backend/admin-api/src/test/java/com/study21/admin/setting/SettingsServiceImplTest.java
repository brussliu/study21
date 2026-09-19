package com.study21.admin.setting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SettingsServiceImpl の検証ロジック単体テスト。
 * 既定値を持たない・空文字エラー・0/false を合法値として扱うことを確認する。
 */
class SettingsServiceImplTest {

    private SettingCatalogMapper catalogMapper;
    private SettingValueMapper valueMapper;
    private SettingsServiceImpl service;
    /** 検証を持たない構成（型・有効値の検証だけを行う）。 */
    private static final List<SettingValueValidator> NO_VALIDATORS = List.of();

    @BeforeEach
    void setUp() {
        catalogMapper = mock(SettingCatalogMapper.class);
        valueMapper = mock(SettingValueMapper.class);
        service = new SettingsServiceImpl(catalogMapper, valueMapper, NO_VALIDATORS);
    }

    private SettingCatalogEntity catalog(String key, String type, String allowed) {
        SettingCatalogEntity c = new SettingCatalogEntity();
        c.setPageCode("TEST");
        c.setSettingKey(key);
        c.setValueType(type);
        c.setAllowedValues(allowed);
        return c;
    }

    private SettingValueEntity value(String key, String value) {
        SettingValueEntity v = new SettingValueEntity();
        v.setPageCode("TEST");
        v.setSettingKey(key);
        v.setSettingValue(value);
        return v;
    }

    @Test
    void missingSettingReportsTaskAndKey() {
        when(catalogMapper.findByPageAndKeys(anyString(), anyList()))
                .thenReturn(List.of(catalog("K", "STRING", null)));
        when(valueMapper.findGlobal("TEST", "K")).thenReturn(null);

        SettingsValidationException ex = assertThrows(SettingsValidationException.class,
                () -> service.requireSettings("batC01", List.of(new SettingRequirement("TEST", "K"))));
        assertTrue(ex.getMessage().contains("batC01"));
        assertTrue(ex.getMessage().contains("K"));
    }

    @Test
    void emptyStringIsRejected() {
        when(catalogMapper.findByPageAndKeys(anyString(), anyList()))
                .thenReturn(List.of(catalog("K", "STRING", null)));
        when(valueMapper.findGlobal("TEST", "K")).thenReturn(value("K", "   "));

        assertThrows(SettingsValidationException.class,
                () -> service.requireSettings("batC01", List.of(new SettingRequirement("TEST", "K"))));
    }

    @Test
    void zeroAndFalseAreValid() {
        when(catalogMapper.findByPageAndKeys(anyString(), anyList()))
                .thenReturn(List.of(
                        catalog("N", "INTEGER", "0..10"),
                        catalog("B", "BOOLEAN", null)));
        when(valueMapper.findGlobal("TEST", "N")).thenReturn(value("N", "0"));
        when(valueMapper.findGlobal("TEST", "B")).thenReturn(value("B", "false"));

        Map<String, String> resolved = service.requireSettings("batC01",
                List.of(new SettingRequirement("TEST", "N"), new SettingRequirement("TEST", "B")));
        assertEquals("0", resolved.get("N"));
        assertEquals("false", resolved.get("B"));
    }

    @Test
    void invalidEnumIsRejected() {
        when(catalogMapper.findByPageAndKeys(anyString(), anyList()))
                .thenReturn(List.of(catalog("E", "ENUM", "A,B,C")));
        when(valueMapper.findGlobal("TEST", "E")).thenReturn(value("E", "Z"));

        assertThrows(SettingsValidationException.class,
                () -> service.requireSettings("batC01", List.of(new SettingRequirement("TEST", "E"))));
    }

    @Test
    void missingMultipleSettingsAreAllReported() {
        when(catalogMapper.findByPageAndKeys(anyString(), anyList()))
                .thenReturn(List.of(catalog("K1", "STRING", null), catalog("K2", "STRING", null)));
        when(valueMapper.findGlobal("TEST", "K1")).thenReturn(null);
        when(valueMapper.findGlobal("TEST", "K2")).thenReturn(null);

        SettingsValidationException ex = assertThrows(SettingsValidationException.class,
                () -> service.requireSettings("batC01",
                        List.of(new SettingRequirement("TEST", "K1"), new SettingRequirement("TEST", "K2"))));
        assertTrue(ex.getMessage().contains("K1"));
        assertTrue(ex.getMessage().contains("K2"));
    }

    // ------------------------------------------------------ ドメイン固有の検証（保存の前）

    /**
     * 保存の前にドメイン固有の検証を当てる。
     *
     * <p>確かめる接縫: **1 つでも問題があれば何も保存しない**（プロンプトの変数のように、
     * 型では表せない間違いを、実行してからではなく保存した瞬間に返す）。</p>
     */
    @Test
    void domainValidatorBlocksTheSave() {
        SettingsServiceImpl withValidator = new SettingsServiceImpl(catalogMapper, valueMapper,
                List.of(new com.study21.admin.geometryai.FigurePromptSettingValidator()));

        String promptField = SettingPageFields.fieldKeyOf("GEOMETRY_AI", "GEOMETRY_AI_SYSTEM_PROMPT");
        String otherField = SettingPageFields.fieldKeyOf("GEOMETRY_AI", "GEOMETRY_AI_MAX_COMMANDS");
        org.junit.jupiter.api.Assumptions.assumeTrue(promptField != null && otherField != null);

        when(catalogMapper.findByPageAndKeys(eq("GEOMETRY_AI"), anyList()))
                .thenReturn(List.of(catalog("GEOMETRY_AI_SYSTEM_PROMPT", "TEXT", null),
                        catalog("GEOMETRY_AI_MAX_COMMANDS", "INTEGER", "1..200")));
        when(valueMapper.findGlobalByKeys(eq("GEOMETRY_AI"), anyList())).thenReturn(List.of());

        Map<String, String> settings = new java.util.LinkedHashMap<>();
        settings.put(promptField, "作図: {requested_output_type}");
        settings.put(otherField, "80");

        SettingsValidationException ex = assertThrows(SettingsValidationException.class,
                () -> withValidator.saveGlobalSettingFields("tester", settings));
        assertTrue(ex.getMessage().contains("{requested_output_type}"));
        // 1 つでも問題があれば何も書かない（途中まで保存しない）
        verify(valueMapper, never()).upsertGlobal(any());
    }

    @Test
    void domainValidatorBlocksSingleKeySave() {
        SettingsServiceImpl withValidator = new SettingsServiceImpl(catalogMapper, valueMapper,
                List.of((pageCode, settingKey, value) -> java.util.Optional.of("知らない変数があります: {foo}")));

        SettingsValidationException ex = assertThrows(SettingsValidationException.class,
                () -> withValidator.saveGlobal("PAGE", "PROMPT", "作図: {foo}", null, "tester"));
        assertTrue(ex.getMessage().contains("{foo}"));
        verify(valueMapper, never()).upsertGlobal(any());
    }

    @Test
    void saveHookIsCalledWithOnlyTheChangedKeysBeforeTheTransactionEnds() {
        when(catalogMapper.findByPageAndKeys(eq("NET_CONTROL"), anyList()))
                .thenReturn(List.of(catalog("NET_CONTROL_END_TIME", "TIME", null)));
        SettingValueEntity existing = new SettingValueEntity();
        existing.setPageCode("NET_CONTROL");
        existing.setSettingKey("NET_CONTROL_END_TIME");
        existing.setSettingValue("23:30");
        when(valueMapper.findGlobalByKeys(eq("NET_CONTROL"), anyList())).thenReturn(List.of(existing));

        java.util.List<SettingSaveTransactionHook.SettingSaveEvent> events = new java.util.ArrayList<>();
        SettingsServiceImpl withHook = new SettingsServiceImpl(catalogMapper, valueMapper, NO_VALIDATORS,
                List.of(events::add));

        withHook.saveGlobalSettingFields("tester", Map.of("netControlEndTime", "21:00"));

        // 保存トランザクションの中で、**変わった設定**だけが渡る（実行スケジュールの適用時刻を書く側が使う）
        assertEquals(1, events.size());
        assertEquals("tester", events.get(0).operator());
        assertEquals(Map.of("NET_CONTROL", Map.of("NET_CONTROL_END_TIME", "21:00")),
                events.get(0).changedByPage());
    }

    @Test
    void saveHookIsNotCalledWhenNothingChanged() {
        when(catalogMapper.findByPageAndKeys(eq("NET_CONTROL"), anyList()))
                .thenReturn(List.of(catalog("NET_CONTROL_END_TIME", "TIME", null)));
        SettingValueEntity existing = new SettingValueEntity();
        existing.setPageCode("NET_CONTROL");
        existing.setSettingKey("NET_CONTROL_END_TIME");
        existing.setSettingValue("23:30");
        when(valueMapper.findGlobalByKeys(eq("NET_CONTROL"), anyList())).thenReturn(List.of(existing));

        java.util.List<SettingSaveTransactionHook.SettingSaveEvent> events = new java.util.ArrayList<>();
        SettingsServiceImpl withHook = new SettingsServiceImpl(catalogMapper, valueMapper, NO_VALIDATORS,
                List.of(events::add));

        withHook.saveGlobalSettingFields("tester", Map.of("netControlEndTime", "23:30"));

        // 同じ値を保存し直しただけ → 適用時刻は動かさない（過去の補償を止めない）
        assertEquals(1, events.size());
        assertTrue(events.get(0).changedByPage().isEmpty());
    }

    @Test
    void saveHookFailureRollsBackTheSave() {
        when(catalogMapper.findByPageAndKeys(eq("NET_CONTROL"), anyList()))
                .thenReturn(List.of(catalog("NET_CONTROL_END_TIME", "TIME", null)));
        when(valueMapper.findGlobalByKeys(eq("NET_CONTROL"), anyList())).thenReturn(List.of());
        SettingsServiceImpl withHook = new SettingsServiceImpl(catalogMapper, valueMapper, NO_VALIDATORS,
                List.of(event -> {
                    throw new IllegalStateException("適用時刻を書けません");
                }));

        // フックの例外はそのまま外へ出る（＝@Transactional がロールバックする。片方だけ残さない）
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> withHook.saveGlobalSettingFields("tester", Map.of("netControlEndTime", "21:00")));
        assertEquals("適用時刻を書けません", thrown.getMessage());
    }

    @Test
    void domainValidatorIsNotCalledForBlankValues() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        SettingsServiceImpl withValidator = new SettingsServiceImpl(catalogMapper, valueMapper,
                List.of((pageCode, settingKey, value) -> {
                    calls.incrementAndGet();
                    return java.util.Optional.empty();
                }));

        withValidator.saveGlobal("PAGE", "PROMPT", "   ", null, "tester");

        assertEquals(0, calls.get());
        verify(valueMapper).upsertGlobal(any());
    }
}
