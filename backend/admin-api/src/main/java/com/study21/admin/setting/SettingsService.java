package com.study21.admin.setting;

import java.util.List;
import java.util.Map;

/**
 * 統一システム設定読み取りサービス。
 *
 * <p>原則: 設定値は必ず DB（COM_設定情報）から読み取る。プログラム既定値・既定プロンプト・
 * フォールバックは一切持たない。未設定・空・不正の場合は即座に {@link SettingsValidationException}
 * を投げる（欠落項目を一括列挙）。</p>
 */
public interface SettingsService {

    /**
     * バッチタスクが必要とする全設定を一括検証・読込する。
     * 欠落・空・型不正・範囲外が複数あっても、全件を列挙して一度に例外を投げる。
     *
     * @return settingKey → 検証済み値（生の文字列）
     * @throws SettingsValidationException 欠落・不正がある場合
     */
    Map<String, String> requireSettings(String taskCode, List<SettingRequirement> requirements);

    /**
     * GLOBAL 設定を 1 件読み取り、必須検証する。欠落・空は例外。
     */
    String requireGlobal(String taskCode, String pageCode, String settingKey);

    /**
     * 作用域・所属オブジェクトで設定値を一覧取得する。
     * 呼び出し元は scope に応じた studentId / parentId を渡すこと（アクセス制御は呼び出し元が担保）。
     */
    List<SettingValueEntity> listByScope(SettingScope scope, String studentId, String parentId);

    /**
     * 設定カタログ（COM_設定項目）をページ単位で取得する。
     */
    List<SettingCatalogEntity> listCatalog(String pageCode);

    /**
     * GLOBAL 設定値を保存（upsert）する。保存前に値の型・有効値を検証する。
     */
    void saveGlobal(String pageCode, String settingKey, String value, String note, String operator);

    /**
     * システム設定ページ向け：画面のフィールドキー（camelCase）で GLOBAL 設定値を一括読込する。
     * 画面に対応するフィールドキーが存在する値のみ返す（対応表外の DB 行は除外）。
     */
    Map<String, String> loadGlobalSettingFields();

    /**
     * システム設定ページ向け：画面のフィールドキー（camelCase）で GLOBAL 設定値を一括保存する。
     * 全件検証してから 1 トランザクションで upsert する（検証エラー時は保存しない）。
     * 空の値は、既存行がある場合のみ空で更新する（未登録の空は登録しない）。
     *
     * @return 保存済みのフィールドキー → 正規化済み値
     * @throws SettingsValidationException 未定義キー・型不正・範囲外がある場合
     */
    Map<String, String> saveGlobalSettingFields(String operator, Map<String, String> settings);
}
