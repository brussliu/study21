package com.study21.admin.setting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SettingsServiceImpl の検証ロジック単体テスト。
 * 既定値を持たない・空文字エラー・0/false を合法値として扱うことを確認する。
 */
class SettingsServiceImplTest {

    private SettingCatalogMapper catalogMapper;
    private SettingValueMapper valueMapper;
    private SettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        catalogMapper = mock(SettingCatalogMapper.class);
        valueMapper = mock(SettingValueMapper.class);
        service = new SettingsServiceImpl(catalogMapper, valueMapper);
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
}
