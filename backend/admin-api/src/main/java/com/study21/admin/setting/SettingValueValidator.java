package com.study21.admin.setting;

import java.util.Optional;

/**
 * 設定値の**ドメイン固有の検証**（型・有効値では表せないもの）。
 *
 * <p>{@link SettingsService} は型（INTEGER / ENUM …）と有効値の範囲しか見ない。プロンプトの
 * テンプレートのように「中身の変数が使える名前か」といった検証は、その設定を知っている側
 * （AI 生図なら {@code FigurePromptSettingValidator}）が行う。</p>
 *
 * <p><strong>保存の前に呼ぶ</strong>ので、間違いは画面へその場で返る（あとからバッチが失敗して
 * 気づく、ということが起きない）。</p>
 */
public interface SettingValueValidator {

    /**
     * 値を検証する。
     *
     * @param pageCode   設定ページの区分（例 GEOMETRY_AI）
     * @param settingKey 設定キー
     * @param value      保存しようとしている値（空でない）
     * @return 問題があれば**日本語の理由**。問題なければ空
     */
    Optional<String> validate(String pageCode, String settingKey, String value);
}
