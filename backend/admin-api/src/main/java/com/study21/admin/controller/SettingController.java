package com.study21.admin.controller;

import com.study21.admin.setting.SettingCatalogEntity;
import com.study21.admin.setting.SettingScope;
import com.study21.admin.setting.SettingValueEntity;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * システム設定 API（管理者向け）。
 *
 * <p>注: 本スケルトンは認証未実装のため、本エンドポイントは SecurityConfig で許可されている。
 * 認証導入時は ADMIN ロール必須とする。</p>
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingController {

    private final SettingsService settingsService;

    public SettingController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/catalog")
    public ApiResponse<List<SettingCatalogEntity>> catalog(
            @RequestParam(value = "pageCode", required = false) String pageCode) {
        return ApiResponse.ok(settingsService.listCatalog(pageCode));
    }

    @GetMapping("/values")
    public ApiResponse<List<SettingValueEntity>> values(
            @RequestParam(value = "scope", defaultValue = "GLOBAL") String scope,
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "parentId", required = false) String parentId) {
        return ApiResponse.ok(settingsService.listByScope(SettingScope.from(scope), studentId, parentId));
    }

    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody Map<String, Object> request) {
        String pageCode = stringOf(request.get("pageCode"));
        String settingKey = stringOf(request.get("settingKey"));
        String value = request.get("value") == null ? null : String.valueOf(request.get("value"));
        String note = stringOf(request.get("note"));
        String operator = stringOf(request.get("operator"));
        settingsService.saveGlobal(pageCode, settingKey, value, note, operator);
        return ApiResponse.ok(null);
    }

    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
