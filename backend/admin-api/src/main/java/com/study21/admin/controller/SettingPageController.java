package com.study21.admin.controller;

import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * システム設定ページ（SystemSettingsView + study2SettingRuntime）向け API。
 *
 * <p>画面側は旧 Study2 の setting.jsp と同じ URL（/api/admin/setting/initSettings・saveSettings）と
 * camelCase のフィールドキーで通信するため、この専用エンドポイントで受ける。
 * フィールドキーと DB (ページ区分, 設定キー) の変換は {@link com.study21.admin.setting.SettingPageFields} で行う。</p>
 *
 * <p>注: 本スケルトンは認証未実装のため、本エンドポイントは SecurityConfig で許可されている。
 * 認証導入時は ADMIN ロール必須とする。</p>
 */
@RestController
@RequestMapping("/api/admin/setting")
public class SettingPageController {

    private final SettingsService settingsService;

    public SettingPageController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * ページ初期化・再読込：GLOBAL 設定値を画面のフィールドキーで一括取得する。
     * 画面に対応する設定値がない項目は返さない（画面側で空表示になる）。
     */
    @PostMapping("/initSettings")
    public ApiResponse<Map<String, Object>> initSettings(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, String> settings = settingsService.loadGlobalSettingFields();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settings", settings);
        return ApiResponse.ok(data);
    }

    /**
     * 設定を保存：画面の全フィールド値を検証してから 1 トランクザクションで一括 upsert する。
     * 検証エラー（未定義キー・型不正・範囲外）は 400 + エラーメッセージで応答し、保存しない。
     */
    @PostMapping("/saveSettings")
    public ApiResponse<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> request) {
        String operator = stringOf(request.get("userId"));
        Map<String, String> settings = flatten(request.get("settings"));
        Map<String, String> saved = settingsService.saveGlobalSettingFields(operator, settings);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settings", saved);
        return ApiResponse.ok(data, "設定を保存しました。");
    }

    private Map<String, String> flatten(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()),
                        entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
