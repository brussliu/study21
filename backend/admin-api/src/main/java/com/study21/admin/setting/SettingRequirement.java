package com.study21.admin.setting;

/**
 * バッチタスクが要求する設定項目の宣言（識別子のみ）。
 * 型・有効値はカタログ（COM_設定項目）を単一の正として解決する。
 *
 * @param pageCode   設定ページ識別
 * @param settingKey 設定項目識別
 */
public record SettingRequirement(String pageCode, String settingKey) {
}
