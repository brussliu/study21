package com.study21.admin.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * プロンプト設定の保存時検証。
 *
 * <p>確かめる接縫: **知らない変数を保存させない**（実行してから失敗するのではなく、
 * 保存した瞬間に理由と使える変数の一覧が出る）。AI 生図の 6 種のキーと AI 画図助手の
 * User Prompt を対象にし、それ以外のキーには口を出さない。</p>
 */
class FigurePromptSettingValidatorTest {

    private final FigurePromptSettingValidator validator = new FigurePromptSettingValidator();

    @Test
    @DisplayName("知らない変数があれば、使える変数の一覧つきで理由を返す")
    void rejectsUnknownVariable() {
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_A_TASK_TEMPLATE",
                "作図: {requested_output_type} と {user_note}"))
                .hasValueSatisfying(message -> assertThat(message)
                        .contains("{requested_output_type}")
                        .contains("{user_note}")
                        .contains("使える変数")
                        // 新しい名前を案内する（保存した瞬間に直せる）
                        .contains("{resultType}")
                        .contains("{note}"));
    }

    @Test
    @DisplayName("登録済みの変数だけなら通す（共通・モード別・DTO の Schema）")
    void acceptsKnownVariables() {
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_SYSTEM_PROMPT",
                "許可: {allowedCommands}\n出力: {outputSchema}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_B_TASK_TEMPLATE",
                "{modeLabel} / {resultType} / {formulaCorrection} / {parameterValues} / {domain}"))
                .as("補充項目の変数は {domain} のような別名ではなく登録名を使う")
                .isPresent();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_B_TASK_TEMPLATE",
                "{modeLabel} / {resultType} / {formulaCorrection} / {parameters} / {domain}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_C_TASK_TEMPLATE",
                "{goal} / {problemCorrection} / {whenInsufficient} / {viewRange}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_D_TASK_TEMPLATE",
                "{goal} / {keepObjects} / {changeObjects} / {textCorrection}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_A_TASK_TEMPLATE",
                "{reproduceFocus} / {knownValues} / {coordinateRange} / {keepLabels}")).isEmpty();
    }

    @Test
    @DisplayName("AI 画図助手の User Prompt は独自の変数で検証する")
    void validatesAssistUserPromptWithItsOwnVariables() {
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_USER_PROMPT",
                "オブジェクト: {objects}\n指示: {instruction}\n最大 {maxCommands} 個")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_USER_PROMPT",
                "指示: {resultType}"))
                .hasValueSatisfying(message -> assertThat(message).contains("{objects}"));
    }

    @Test
    @DisplayName("対象外のページ・キーには口を出さない")
    void ignoresOtherSettings() {
        assertThat(validator.validate("CLASSROOM_AI", "CLASSROOM_AI_NOTE_USER_PROMPT", "{transcript}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_MAX_COMMANDS", "{foo}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_SYSTEM_PROMPT", "{foo}")).isEmpty();
        assertThat(validator.validate("GEOMETRY_AI", "GEOMETRY_AI_PROVIDER", null)).isEmpty();
    }
}
