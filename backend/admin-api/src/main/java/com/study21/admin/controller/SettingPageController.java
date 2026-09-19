package com.study21.admin.controller;

import com.study21.admin.ai.AiConnectionTester;
import com.study21.admin.ai.SttConnectionTester;
import com.study21.admin.geometryai.dto.AiResponseDtos;
import com.study21.admin.geometryai.dto.AiResponseSchemaService;
import com.study21.admin.schedule.ScheduleConfigService;
import com.study21.admin.schedule.ScheduleSettingValidator;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.exception.ValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AiResponseSchemaService schemaService;
    private final AiConnectionTester connectionTester;
    private final SttConnectionTester sttConnectionTester;
    /** 保存の**コミット後**にバッチの実行スケジュールのメモリを更新する。 */
    private final ScheduleConfigService scheduleConfigService;
    /** 実行スケジュールの欄をまたぐ検証（保存の前）。 */
    private final ScheduleSettingValidator scheduleSettingValidator;

    public SettingPageController(SettingsService settingsService, AiResponseSchemaService schemaService,
                                 AiConnectionTester connectionTester, SttConnectionTester sttConnectionTester,
                                 ScheduleConfigService scheduleConfigService,
                                 ScheduleSettingValidator scheduleSettingValidator) {
        this.settingsService = settingsService;
        this.schemaService = schemaService;
        this.connectionTester = connectionTester;
        this.sttConnectionTester = sttConnectionTester;
        this.scheduleConfigService = scheduleConfigService;
        this.scheduleSettingValidator = scheduleSettingValidator;
    }

    /**
     * AIモデルページの【接続テスト】：URL・API Key・モデル名で本当に呼べるかを 1 回だけ確かめる。
     *
     * <p>画面は保存前の入力値（モデル1・API Key・URL）をそのまま送る。接続できなかった場合は
     * 200 の本文で {@code ok=false} と理由を返す（設定の不備は 400）。</p>
     */
    @PostMapping("/testAi")
    public ApiResponse<Map<String, Object>> testAi(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(connectionTester.test(
                stringOf(request.get("provider")),
                stringOf(request.get("model")),
                stringOf(request.get("url")),
                request.get("apiKey") == null ? null : String.valueOf(request.get("apiKey"))));
    }

    /**
     * AIモデルページの**音声認識（STT）の【接続テスト】**：無音を 1 回送って設定を確かめる。
     *
     * <p>対象は Google Speech-to-Text と Alibaba Paraformer-Realtime-V2（どちらも「AIモデル」ページの
     * 専用タブ）。チャットの【接続テスト】と同じく、保存前の入力値をそのまま受け取る。</p>
     */
    @PostMapping("/testStt")
    public ApiResponse<Map<String, Object>> testStt(@RequestBody Map<String, Object> request) {
        return ApiResponse.ok(sttConnectionTester.test(
                stringOf(request.get("provider")),
                stringOf(request.get("model")),
                stringOf(request.get("url")),
                request.get("apiKey") == null ? null : String.valueOf(request.get("apiKey"))));
    }

    /**
     * AI 出力データ構造（DTO）の JSON Schema を返す（設定ページの **Data TAB** 用）。
     *
     * <p>DTO が唯一の定義なので、スキーマは実行時に生成する（DB や JS に固定の JSON を持たない）。
     * 画面はこれを使って「項目構造」と「JSON Schema」を表示する（見るだけ。編集はしない）。
     * DTO を直せば、この API の応答もプロンプトへ注入する出力形式も同時に変わる。</p>
     */
    @GetMapping("/ai-response-schema")
    public ApiResponse<Map<String, Object>> aiResponseSchema(@RequestParam("task") String taskCode) {
        Class<?> dtoClass = AiResponseDtos.dtoOf(taskCode).orElseThrow(() ->
                new ValidationException("AI 出力 DTO が未登録です: " + taskCode));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskCode", taskCode);
        data.put("dto", dtoClass.getSimpleName());
        data.put("schema", schemaService.schemaOf(dtoClass));
        return ApiResponse.ok(data);
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
     *
     * <p>保存（コミット）が済んだあとに、バッチの実行スケジュールのメモリを更新する。
     * 更新に失敗しても**保存は巻き戻さない**（DB は正）。その場合は「保存済み・実行設定への
     * 反映待ち」として画面に伝え、前の有効な設定のまま動かしつつ自動で再試行する。</p>
     */
    @PostMapping("/saveSettings")
    public ApiResponse<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> request) {
        String operator = stringOf(request.get("userId"));
        Map<String, String> settings = flatten(request.get("settings"));
        // 欄をまたぐ検証（ずらしは 0〜実行間隔-1 / 開始と終了は同時刻にしない）。
        // ここで弾けば DB には保存されない
        scheduleSettingValidator.validate(settings);
        Map<String, String> saved = settingsService.saveGlobalSettingFields(operator, settings);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settings", saved);

        // コミット後に反映する（トランザクションの中でキャッシュを触ると、ロールバックしたのに
        // 実行設定だけ変わっている、という食い違いが起きる）
        ScheduleConfigService.RefreshResult refresh = scheduleConfigService.refresh("設定保存");
        data.put("scheduleVersion", refresh.version());
        data.put("schedulePending", !refresh.published());
        data.put("scheduleMessage", refresh.published() ? null : refresh.error());
        if (refresh.published()) {
            return ApiResponse.ok(data, "設定を保存しました。");
        }
        return ApiResponse.ok(data, "保存済み・実行設定への反映待ち（" + refresh.error() + "）。自動で再試行します。");
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
