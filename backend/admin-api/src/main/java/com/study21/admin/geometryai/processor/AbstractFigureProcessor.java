package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.common.core.geometryai.AiFigureSettingKeys;

/**
 * モード別プロセッサの共通部分（**設定キーの命名規則**だけを持つ）。
 *
 * <p>モード別の設定キーは {@code GEOMETRY_AI_<モード>_SYSTEM_PROMPT} /
 * {@code GEOMETRY_AI_<モード>_TASK_TEMPLATE}。名前の定義は
 * {@link AiFigureSettingKeys}（common-core）に 1 回だけ書いてあり、user-api も同じものを使う
 * （要求の受付時に固定する設定スナップショットとキーがずれないようにするため）。</p>
 */
public abstract class AbstractFigureProcessor implements FigureProcessor {

    /** モード別プロンプトの設定キーの接頭辞。 */
    public static final String MODE_KEY_PREFIX = AiFigureSettingKeys.PREFIX;

    /** モード別の system プロンプトの設定キー（例: GEOMETRY_AI_A_SYSTEM_PROMPT）。 */
    public static String systemPromptKeyOf(FigureMode mode) {
        return AiFigureSettingKeys.systemPromptKey(mode.name());
    }

    /** モード別のタスクテンプレートの設定キー（例: GEOMETRY_AI_A_TASK_TEMPLATE）。 */
    public static String taskTemplateKeyOf(FigureMode mode) {
        return AiFigureSettingKeys.taskTemplateKey(mode.name());
    }

    /** モード別のモデルパラメータの設定キー（例: GEOMETRY_AI_A_TEMPERATURE）。 */
    public static String parameterKeyOf(FigureMode mode, String suffix) {
        return AiFigureSettingKeys.modeKey(mode.name(), suffix);
    }

    @Override
    public String systemPromptKey() {
        return systemPromptKeyOf(mode());
    }

    @Override
    public String taskTemplateKey() {
        return taskTemplateKeyOf(mode());
    }
}
