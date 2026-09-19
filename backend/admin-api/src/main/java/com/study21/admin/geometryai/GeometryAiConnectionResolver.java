package com.study21.admin.geometryai;

import com.study21.admin.setting.SettingsService;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiModelSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 設定 `GEOMETRY_AI_PROVIDER`（例 `qwen:4`）から**実際の接続情報**を解決する。
 *
 * <p>モデル名・URL・API Key は `AI_MODEL` ページの**同じスロット**を共用する
 * （`AI_QWEN_MODEL_4` / `AI_QWEN_URL` / `AI_QWEN_API_KEY`）。キーの組み立ては
 * {@link AiModelSlot}（common-core）が唯一の定義で、要求の受付（user-api）も同じものを使う。</p>
 *
 * <p><strong>モデルの固定</strong>: 要求行のスナップショットに**受付時のモデル名**が入っているときは
 * それをそのまま使う。同じスロット（例 `qwen:4`）の下でモデル名が書き換えられても、並んでいる
 * 要求が**黙って別のモデルへ移らない**ようにするため（警告ログと実行記録に残す）。
 * スロットそのものが消えている（設定が未設定・空）ときは**日本語の理由で失敗**し、
 * 別のモデルへは切り替えない。**API Key と URL はスナップショットに入れず**、ここで安全に読む。</p>
 */
@Component
public class GeometryAiConnectionResolver {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiConnectionResolver.class);

    private final SettingsService settingsService;
    /** 検証用のスタブ（`study21.geometry-ai.stub=true`）。有効なら AI を実際に呼ばない。 */
    private final boolean stub;

    public GeometryAiConnectionResolver(SettingsService settingsService,
                                        @Value("${study21.geometry-ai.stub:false}") boolean stub) {
        this.settingsService = settingsService;
        this.stub = stub;
    }

    /**
     * 解決した接続情報。
     *
     * @param provider        提供元（例 qwen）
     * @param model           実際に呼ぶモデル名（固定したモデルがあればそれ）
     * @param url             接続先（**スナップショットには入れない**）
     * @param apiKey          API Key（**スナップショットには入れない**）
     * @param slot            スロット（例 qwen:4）
     * @param configuredModel いまの設定に入っているモデル名（固定と違うときに気づけるように残す）
     * @param modelFromPinned 固定したモデルを使ったか（＝いまの設定と違う）
     */
    public record AiConnection(String provider, String model, String url, String apiKey,
                               String slot, String configuredModel, boolean modelFromPinned) {

        /** スタブ動作・固定なしのときの最小の形（テストと後方互換）。 */
        public AiConnection(String provider, String model, String url, String apiKey) {
            this(provider, model, url, apiKey, null, model, false);
        }
    }

    /** 設定のスロット（`qwen:4`）を解決する（固定したモデルは無し）。 */
    public AiConnection resolve(String taskCode, String slot) {
        return resolve(taskCode, slot, null);
    }

    /**
     * 設定のスロットを解決する（**固定したモデル名があればそれを使う**）。
     *
     * @param pinnedModel 要求行に固定したモデル名（null・空なら「固定できていない」＝いまの設定を使う）
     */
    public AiConnection resolve(String taskCode, String slot, String pinnedModel) {
        AiModelSlot target = AiModelSlot.require(slot);
        String configured;
        String url;
        String apiKey;
        try {
            configured = settingsService.requireGlobal(taskCode, AiModelSlot.PAGE, target.modelKey());
            url = settingsService.requireGlobal(taskCode, AiModelSlot.PAGE, target.urlKey());
            apiKey = settingsService.requireGlobal(taskCode, AiModelSlot.PAGE, target.apiKeyKey());
        } catch (SettingsValidationException cause) {
            if (!stub) {
                // モデルが消えている・キーが無いときは**別のモデルへ切り替えず**理由を返す
                throw new ValidationException("AI のモデル設定を読めません（スロット " + target.slot() + "）。"
                        + "「AIモデル」ページの " + target.modelKey() + " / " + target.urlKey()
                        + " / " + target.apiKeyKey() + " を確認してください。");
            }
            // 検証用スタブ（外部へは出ない）では接続設定が無くても動かす
            log.warn("AI 生図はスタブ動作です（接続設定なし）。key={}", target.modelKey());
            return new AiConnection(target.provider(), "stub-model", "stub://local", "stub-key",
                    target.slot(), "stub-model", false);
        }

        String trimmed = pinnedModel == null ? null : pinnedModel.strip();
        if (trimmed == null || trimmed.isEmpty() || trimmed.equals(configured)) {
            return new AiConnection(target.provider(), configured, url, apiKey,
                    target.slot(), configured, false);
        }
        // 受付時に固定したモデルを使う（黙って別のモデルへ切り替えない。記録にも残す）
        log.warn("AI 生図は受付時に固定したモデルを使います（いまの設定と違います）。"
                + "slot={} pinned={} configured={}", target.slot(), trimmed, configured);
        return new AiConnection(target.provider(), trimmed, url, apiKey, target.slot(), configured, true);
    }
}
