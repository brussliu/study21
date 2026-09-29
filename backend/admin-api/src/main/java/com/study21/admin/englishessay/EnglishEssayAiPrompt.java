package com.study21.admin.englishessay;

import java.util.Map;

/**
 * 設定ページのプロンプト（{@code ENGLISH_ESSAY_*_PROMPT}）の入力変数を埋める。
 *
 * <p>設定値は<b>単一の正</b>なので、プロンプト本文をコードへ写さない。ここが受け持つのは
 * {@code {{level}}} のような印の置換だけ。設定に無い印（将来の追加変数）は<b>そのまま残す</b>
 * （勝手に消すと、何が埋まっていないかが分からなくなる）。</p>
 */
final class EnglishEssayAiPrompt {

    private EnglishEssayAiPrompt() {
    }

    /**
     * {@code {{name}}} を値で置き換える。
     *
     * @param template 設定のプロンプト（null は空文字として扱う）
     * @param values   変数の値（null は空文字にする）
     */
    static String render(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        if (values == null || values.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }
}
