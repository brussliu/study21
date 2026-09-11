package com.study21.admin.setting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 統一設定読み取りサービス実装。
 * プログラム既定値・フォールバックを持たない。欠落・空・不正は例外。
 * 型・有効値はカタログ（COM_設定項目）から解決する。
 */
@Service
public class SettingsServiceImpl implements SettingsService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SettingCatalogMapper catalogMapper;
    private final SettingValueMapper valueMapper;

    public SettingsServiceImpl(SettingCatalogMapper catalogMapper, SettingValueMapper valueMapper) {
        this.catalogMapper = catalogMapper;
        this.valueMapper = valueMapper;
    }

    @Override
    public Map<String, String> requireSettings(String taskCode, List<SettingRequirement> requirements) {
        List<String> errors = new ArrayList<>();
        Map<String, String> resolved = new LinkedHashMap<>();

        if (requirements == null || requirements.isEmpty()) {
            return resolved;
        }

        // カタログを一括取得して型・有効値を解決する（ページごとにまとめて取得）
        Map<String, List<SettingRequirement>> byPage = new LinkedHashMap<>();
        for (SettingRequirement req : requirements) {
            byPage.computeIfAbsent(req.pageCode(), k -> new ArrayList<>()).add(req);
        }

        for (Map.Entry<String, List<SettingRequirement>> entry : byPage.entrySet()) {
            String pageCode = entry.getKey();
            List<String> keys = entry.getValue().stream().map(SettingRequirement::settingKey).toList();
            List<SettingCatalogEntity> catalogs = catalogMapper.findByPageAndKeys(pageCode, keys);
            Map<String, SettingCatalogEntity> catalogMap = new LinkedHashMap<>();
            for (SettingCatalogEntity c : catalogs) {
                catalogMap.put(c.getSettingKey(), c);
            }

            for (SettingRequirement req : entry.getValue()) {
                SettingCatalogEntity catalog = catalogMap.get(req.settingKey());
                SettingValueEntity value = valueMapper.findGlobal(pageCode, req.settingKey());
                String raw = value == null ? null : value.getSettingValue();
                if (isBlank(raw)) {
                    errors.add(missing(taskCode, pageCode, req.settingKey(), "設定値が未設定または空です"));
                    continue;
                }
                if (catalog == null) {
                    errors.add(invalid(taskCode, pageCode, req.settingKey(), "カタログ定義が存在しません"));
                    continue;
                }
                try {
                    SettingValueType type = SettingValueType.from(catalog.getValueType());
                    resolved.put(req.settingKey(), validate(taskCode, pageCode, req.settingKey(), type, catalog.getAllowedValues(), raw));
                } catch (IllegalArgumentException e) {
                    errors.add(invalid(taskCode, pageCode, req.settingKey(), e.getMessage()));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new SettingsValidationException(errors);
        }
        return resolved;
    }

    @Override
    public String requireGlobal(String taskCode, String pageCode, String settingKey) {
        SettingValueEntity value = valueMapper.findGlobal(pageCode, settingKey);
        if (value == null || isBlank(value.getSettingValue())) {
            throw new SettingsValidationException(
                    List.of(missing(taskCode, pageCode, settingKey, "設定値が未設定または空です")));
        }
        return value.getSettingValue();
    }

    @Override
    public List<SettingValueEntity> listByScope(SettingScope scope, String studentId, String parentId) {
        return valueMapper.findByScope(scope.name(), studentId, parentId);
    }

    @Override
    public List<SettingCatalogEntity> listCatalog(String pageCode) {
        return catalogMapper.findByPage(pageCode);
    }

    @Override
    public void saveGlobal(String pageCode, String settingKey, String value, String note, String operator) {
        SettingValueEntity existing = valueMapper.findGlobal(pageCode, settingKey);
        String operatorId = operator == null || operator.isBlank() ? "admin-api" : operator.trim();

        SettingValueEntity entity = new SettingValueEntity();
        entity.setPageCode(pageCode);
        entity.setSettingKey(settingKey);
        entity.setScope(SettingScope.GLOBAL.name());
        entity.setSettingValue(value == null ? null : value.trim());
        entity.setNote(note);
        entity.setCreatedBy(existing == null ? operatorId : null);
        entity.setUpdatedBy(operatorId);
        valueMapper.upsertGlobal(entity);
    }

    @Override
    public Map<String, String> loadGlobalSettingFields() {
        List<SettingValueEntity> values = valueMapper.findAllGlobal();
        Map<String, String> result = new LinkedHashMap<>();
        for (SettingValueEntity value : values) {
            String fieldKey = SettingPageFields.fieldKeyOf(value.getPageCode(), value.getSettingKey());
            if (fieldKey == null) {
                continue; // 画面に存在しない DB 行（gemini・教材AI・将来用等）は除外
            }
            result.put(fieldKey, value.getSettingValue());
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, String> saveGlobalSettingFields(String operator, Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            throw new SettingsValidationException(List.of("保存する設定値がありません。"));
        }
        String operatorId = operator == null || operator.isBlank() ? "admin-api" : operator.trim();

        List<String> errors = new ArrayList<>();
        Map<String, Map<String, String>> byPage = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String fieldKey = entry.getKey();
            SettingPageFields.FieldRef ref = SettingPageFields.of(fieldKey);
            if (ref == null) {
                errors.add("設定ページに存在しないキーです: " + fieldKey);
                continue;
            }
            byPage.computeIfAbsent(ref.pageCode(), k -> new LinkedHashMap<>())
                    .put(fieldKey, entry.getValue() == null ? null : entry.getValue().trim());
        }

        List<SettingValueEntity> upserts = new ArrayList<>();
        Map<String, String> saved = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> pageEntry : byPage.entrySet()) {
            String pageCode = pageEntry.getKey();
            Map<String, SettingPageFields.FieldRef> refs = pageEntry.getValue().keySet().stream()
                    .collect(Collectors.toMap(k -> k, SettingPageFields::of, (a, b) -> a, LinkedHashMap::new));
            List<String> dbKeys = refs.values().stream().map(SettingPageFields.FieldRef::settingKey).toList();
            Map<String, SettingCatalogEntity> catalogMap = catalogMapOf(pageCode, dbKeys);
            Map<String, SettingValueEntity> existingMap = existingMapOf(pageCode, dbKeys);

            for (Map.Entry<String, String> fieldEntry : pageEntry.getValue().entrySet()) {
                String fieldKey = fieldEntry.getKey();
                SettingPageFields.FieldRef ref = refs.get(fieldKey);
                String raw = fieldEntry.getValue();
                SettingCatalogEntity catalog = catalogMap.get(ref.settingKey());
                if (catalog == null) {
                    errors.add(invalid("設定ページ", pageCode, ref.settingKey(), "カタログ定義が存在しません"));
                    continue;
                }
                if (isBlank(raw) && !existingMap.containsKey(ref.settingKey())) {
                    continue; // 未登録の空の既定値は登録しない（APIキー等）
                }
                String normalized = raw;
                if (!isBlank(raw)) {
                    try {
                        SettingValueType type = SettingValueType.from(catalog.getValueType());
                        normalized = validate("設定ページ", pageCode, ref.settingKey(), type, catalog.getAllowedValues(), raw);
                    } catch (IllegalArgumentException e) {
                        errors.add(invalid("設定ページ", pageCode, ref.settingKey(), e.getMessage()));
                        continue;
                    }
                }
                SettingValueEntity existing = existingMap.get(ref.settingKey());
                SettingValueEntity entity = new SettingValueEntity();
                entity.setPageCode(pageCode);
                entity.setSettingKey(ref.settingKey());
                entity.setScope(SettingScope.GLOBAL.name());
                entity.setSettingValue(normalized);
                entity.setNote(existing == null ? null : existing.getNote());
                entity.setCreatedBy(existing == null ? operatorId : null);
                entity.setUpdatedBy(operatorId);
                upserts.add(entity);
                saved.put(fieldKey, normalized);
            }
        }

        if (!errors.isEmpty()) {
            throw new SettingsValidationException(errors);
        }
        upserts.forEach(valueMapper::upsertGlobal);
        return saved;
    }

    private Map<String, SettingCatalogEntity> catalogMapOf(String pageCode, List<String> dbKeys) {
        return catalogMapper.findByPageAndKeys(pageCode, dbKeys).stream()
                .collect(Collectors.toMap(SettingCatalogEntity::getSettingKey, c -> c, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, SettingValueEntity> existingMapOf(String pageCode, List<String> dbKeys) {
        return valueMapper.findGlobalByKeys(pageCode, dbKeys).stream()
                .collect(Collectors.toMap(SettingValueEntity::getSettingKey, v -> v, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 値を型・有効値に照らして検証し、正規化した文字列を返す。
     */
    private String validate(String taskCode, String pageCode, String key, SettingValueType type, String allowedValues, String raw) {
        String value = raw.trim();
        return switch (type) {
            case INTEGER -> String.valueOf(validateInt(value, allowedValues));
            case DECIMAL -> validateDecimal(value);
            case BOOLEAN -> String.valueOf(validateBoolean(value));
            case ENUM -> validateEnum(value, allowedValues);
            case TIME -> validateTime(value);
            case TEXT, STRING -> value;
        };
    }

    private int validateInt(String value, String allowedValues) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("整数として解釈できません: " + value);
        }
        int[] range = parseRange(allowedValues);
        if (range != null && (parsed < range[0] || parsed > range[1])) {
            throw new IllegalArgumentException("許容範囲外です: " + value + "（" + range[0] + "〜" + range[1] + "）");
        }
        return parsed;
    }

    private String validateDecimal(String value) {
        try {
            return new BigDecimal(value).toPlainString();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数値として解釈できません: " + value);
        }
    }

    private boolean validateBoolean(String value) {
        if ("1".equals(value) || "true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("真偽値として解釈できません: " + value);
    }

    private String validateEnum(String value, String allowedValues) {
        Set<String> allowed = allowedValues(allowedValues);
        if (allowed == null || allowed.isEmpty()) {
            return value;
        }
        if (allowed.contains(value)) {
            return value;
        }
        // 数値表記のゆらぎ（例: 0 → 0.0、2 → 2.0）を数値比較で吸収し、正規の有効値へ変換する。
        BigDecimal numeric;
        try {
            numeric = new BigDecimal(value);
        } catch (NumberFormatException e) {
            numeric = null;
        }
        if (numeric != null) {
            for (String candidate : allowed) {
                try {
                    if (new BigDecimal(candidate).compareTo(numeric) == 0) {
                        return candidate;
                    }
                } catch (NumberFormatException ignored) {
                    // 数値でない有効値（プロバイダー名等）は比較対象外
                }
            }
        }
        throw new IllegalArgumentException("不正な列挙値です: " + value + "（有効値: " + String.join(",", allowed) + "）");
    }

    private String validateTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FMT).format(TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("時刻形式(HH:mm)として解釈できません: " + value);
        }
    }

    private Set<String> allowedValues(String allowedValues) {
        if (isBlank(allowedValues)) {
            return null;
        }
        String[] parts = allowedValues.split(",");
        Set<String> set = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private int[] parseRange(String allowedValues) {
        if (isBlank(allowedValues)) {
            return null;
        }
        String s = allowedValues.trim();
        int idx = s.indexOf("..");
        if (idx < 0) {
            return null;
        }
        try {
            int min = Integer.parseInt(s.substring(0, idx).trim());
            int max = Integer.parseInt(s.substring(idx + 2).trim());
            return new int[]{min, max};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String missing(String taskCode, String pageCode, String key, String reason) {
        return "タスク=" + taskCode + ", page_code=" + pageCode + ", setting_key=" + key + ", 原因=" + reason;
    }

    private String invalid(String taskCode, String pageCode, String key, String reason) {
        return missing(taskCode, pageCode, key, "不正な値（" + reason + "）");
    }
}
