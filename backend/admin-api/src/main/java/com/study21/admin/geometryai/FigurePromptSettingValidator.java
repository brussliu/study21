package com.study21.admin.geometryai;

import com.study21.admin.setting.SettingValueValidator;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI 生図・AI 画図助手の**プロンプト設定を保存する前に検証する**。
 *
 * <p>テンプレートの変数はコードが決める（{@link FigurePromptTemplate} が唯一の定義）。
 * 知らない変数を書いたまま保存すると、**実行して初めて**「知らない変数があります」で失敗し、
 * しかもそれは利用者から見ると「AI が動かない」にしか見えない。ここで保存時に弾いて、
 * 使える変数の一覧も一緒に返す（画面にそのまま出る）。</p>
 */
@Component
public class FigurePromptSettingValidator implements SettingValueValidator {

    /** AI 画図助手の User Prompt が使える変数（{@link GeometryAiAssistPromptBuilder} が展開する）。 */
    static final List<String> ASSIST_USER_VARIABLES =
            List.of("objects", "instruction", "maxCommands", "failure", "xml");

    @Override
    public Optional<String> validate(String pageCode, String settingKey, String value) {
        if (!AiFigureSettingKeys.PAGE.equals(pageCode) || settingKey == null || value == null) {
            return Optional.empty();
        }
        if (isAssistUserPrompt(settingKey)) {
            return unknownVariables(value, new LinkedHashSet<>(ASSIST_USER_VARIABLES), ASSIST_USER_VARIABLES);
        }
        if (isFigurePrompt(settingKey)) {
            Set<String> unknown = FigurePromptTemplate.unknownVariables(value);
            if (unknown.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(unknownMessage(unknown, FigurePromptTemplate.variables().keySet()));
        }
        return Optional.empty();
    }

    /** AI 生図の System Prompt・タスクテンプレート（共通＋モード別）か。 */
    private static boolean isFigurePrompt(String settingKey) {
        if (AiFigureSettingKeys.SYSTEM_PROMPT.equals(settingKey)
                || AiFigureSettingKeys.INSTRUCTION_TEMPLATE.equals(settingKey)) {
            return true;
        }
        for (String mode : List.of("A", "B", "C", "D")) {
            if (AiFigureSettingKeys.systemPromptKey(mode).equals(settingKey)
                    || AiFigureSettingKeys.taskTemplateKey(mode).equals(settingKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssistUserPrompt(String settingKey) {
        return "GEOMETRY_AI_ASSIST_USER_PROMPT".equals(settingKey);
    }

    private static Optional<String> unknownVariables(String value, Set<String> known, List<String> order) {
        Set<String> unknown = new LinkedHashSet<>(FigurePromptTemplate.placeholders(value));
        unknown.removeAll(known);
        return unknown.isEmpty() ? Optional.empty() : Optional.of(unknownMessage(unknown, order));
    }

    private static String unknownMessage(Set<String> unknown, java.util.Collection<String> known) {
        return "知らない変数があります: "
                + String.join(", ", unknown.stream().map(name -> "{" + name + "}").sorted().toList())
                + "。使える変数: "
                + String.join(", ", known.stream().map(name -> "{" + name + "}").toList());
    }
}
