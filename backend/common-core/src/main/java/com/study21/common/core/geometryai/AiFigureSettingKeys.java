package com.study21.common.core.geometryai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 生図（`GEOMETRY_AI` ページ）の**設定キーの唯一の定義**。
 *
 * <p>共通の設定（`GEOMETRY_AI_*`）とモード別の設定（`GEOMETRY_AI_&lt;A〜D&gt;_*`）の名前を
 * ここで 1 回だけ決める。admin-api（設定ページ・バッチ）と user-api（画面・要求の受付）の
 * **両方から同じ名前を使う**ので、どちらか一方だけを直して食い違うことがない
 * （同じ規則を 2 か所に書かない）。</p>
 *
 * <p>モード別の項目は**未設定・空のとき共通をそのまま使う**（設計 §6 の継承）。
 * したがって利用者は全部を書かなくてよい。</p>
 */
public final class AiFigureSettingKeys {

    /** 設定ページ（COM_設定項目 のページ区分）。 */
    public static final String PAGE = "GEOMETRY_AI";

    /** 設定キーの接頭辞。 */
    public static final String PREFIX = "GEOMETRY_AI_";

    // ---------------------------------------------------------------- 共通の設定

    /** 有効／無効。 */
    public static final String ENABLED = PREFIX + "ENABLED";
    /** 使用するモデルのスロット（例 qwen:4）。 */
    public static final String PROVIDER = PREFIX + "PROVIDER";
    /** 出力形式（JSON / COMMAND）。 */
    public static final String OUTPUT_FORMAT = PREFIX + "OUTPUT_FORMAT";
    /** 共通の System Prompt（**必須**）。 */
    public static final String SYSTEM_PROMPT = PREFIX + "SYSTEM_PROMPT";
    /** 共通の User Prompt（タスクテンプレート。**任意**。空でも動く）。 */
    public static final String INSTRUCTION_TEMPLATE = PREFIX + "INSTRUCTION_TEMPLATE";
    public static final String TEMPERATURE = PREFIX + "TEMPERATURE";
    public static final String MAX_COMPLETION_TOKENS = PREFIX + "MAX_COMPLETION_TOKENS";
    public static final String REQUEST_TIMEOUT_SECONDS = PREFIX + "REQUEST_TIMEOUT_SECONDS";
    public static final String RETRY_LIMIT = PREFIX + "RETRY_LIMIT";
    public static final String MAX_COMMANDS = PREFIX + "MAX_COMMANDS";
    public static final String ALLOWED_COMMANDS = PREFIX + "ALLOWED_COMMANDS";

    // ------------------------------------------------------------ モード別の接尾辞

    public static final String SUFFIX_SYSTEM_PROMPT = "SYSTEM_PROMPT";
    public static final String SUFFIX_TASK_TEMPLATE = "TASK_TEMPLATE";
    public static final String SUFFIX_PROVIDER = "PROVIDER";
    public static final String SUFFIX_TEMPERATURE = "TEMPERATURE";
    public static final String SUFFIX_MAX_COMPLETION_TOKENS = "MAX_COMPLETION_TOKENS";
    public static final String SUFFIX_REQUEST_TIMEOUT_SECONDS = "REQUEST_TIMEOUT_SECONDS";
    public static final String SUFFIX_RETRY_LIMIT = "RETRY_LIMIT";

    private AiFigureSettingKeys() {
    }

    /** モード別の設定キー（例: {@code GEOMETRY_AI_A_SYSTEM_PROMPT}）。 */
    public static String modeKey(String mode, String suffix) {
        return PREFIX + normalizeMode(mode) + "_" + suffix;
    }

    /** モード別の System Prompt のキー。 */
    public static String systemPromptKey(String mode) {
        return modeKey(mode, SUFFIX_SYSTEM_PROMPT);
    }

    /** モード別のタスクテンプレート（User Prompt）のキー。 */
    public static String taskTemplateKey(String mode) {
        return modeKey(mode, SUFFIX_TASK_TEMPLATE);
    }

    /**
     * 実行に必要な**共通の設定キー**（すべてのモードで同じ）。
     *
     * <p>{@link #INSTRUCTION_TEMPLATE} は入っていない（**空でよい**。モード側だけを書く運用を許す）。</p>
     */
    public static List<String> commonKeys() {
        return List.of(ENABLED, PROVIDER, OUTPUT_FORMAT, SYSTEM_PROMPT, INSTRUCTION_TEMPLATE,
                TEMPERATURE, MAX_COMPLETION_TOKENS, REQUEST_TIMEOUT_SECONDS, RETRY_LIMIT,
                MAX_COMMANDS, ALLOWED_COMMANDS);
    }

    /** そのモードの設定キー（継承の判定に使う。未設定なら共通を使う）。 */
    public static List<String> modeKeys(String mode) {
        return List.of(systemPromptKey(mode), taskTemplateKey(mode),
                modeKey(mode, SUFFIX_PROVIDER), modeKey(mode, SUFFIX_TEMPERATURE),
                modeKey(mode, SUFFIX_MAX_COMPLETION_TOKENS), modeKey(mode, SUFFIX_REQUEST_TIMEOUT_SECONDS),
                modeKey(mode, SUFFIX_RETRY_LIMIT));
    }

    /** 共通＋そのモードの設定キー（要求の受付でまとめて読むときに使う）。 */
    public static List<String> keysFor(String mode) {
        List<String> keys = new ArrayList<>(commonKeys());
        keys.addAll(modeKeys(mode));
        return List.copyOf(keys);
    }

    /** モードの表記をそろえる（小文字・前後の空白を許す）。 */
    public static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
    }
}
