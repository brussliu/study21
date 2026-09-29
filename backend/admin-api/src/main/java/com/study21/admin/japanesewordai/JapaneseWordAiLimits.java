package com.study21.admin.japanesewordai;

import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日本語単語の AI 取得（{@code batC41}〜{@code batC44}）の**1 回の受付の上限**。
 *
 * <p>上限は<b>設定ページの「1 回の最大単語数」だけ</b>で決まる
 * （{@code BAT_C41_BATCH_MAX} 〜 {@code BAT_C44_BATCH_MAX}。{@code COM_設定項目} の有効値は 10〜200）。
 * <b>コード側に上限の定数は持たない</b>（利用者の指示 2026-09-27
 * 「把这个限制去掉，只使用设定页面的「1回の最大単語数」来进行限制」）。</p>
 *
 * <p>設定が読めない・範囲外のときは<b>例外</b>にする（実行しない）。これは
 * {@code SettingsService} の方針（プログラム既定値・フォールバックを持たない）と同じで、
 * かつ<b>実行そのものも設定が無ければ始まらない</b>（{@link JapaneseWordAiSettings} が例外にする）ため、
 * 「受付だけ通って後で必ず失敗する行」を積まないようにする。</p>
 *
 * <p>画面（AI 取得の窓）は「1 回の受付は N 語までです」と出し、対象（検索条件に一致する語のうち
 * 今回受付ける分）もサーバーが選ぶ。**受付の API も同じ上限で検証する**（画面を通さない呼出もある）。</p>
 */
public final class JapaneseWordAiLimits {

    /** 設定値の下限・上限（{@code COM_設定項目} の有効値に合わせる）。外れていれば設定ミスとして拒否する。 */
    static final int CONFIGURED_MIN = 10;
    static final int CONFIGURED_MAX = 200;

    /** 取得区分 → バッチコード（{@code run} と {@code limits} で同じ対応を使う）。 */
    public static final Map<String, String> BATCH_OF_KIND;

    static {
        Map<String, String> batchOfKind = new LinkedHashMap<>();
        batchOfKind.put("DETAIL", "batC41");
        batchOfKind.put("C", "batC42");
        batchOfKind.put("D", "batC43");
        batchOfKind.put("E", "batC44");
        BATCH_OF_KIND = Map.copyOf(batchOfKind);
    }

    private JapaneseWordAiLimits() {
    }

    /**
     * そのバッチの「1 回の最大単語数」（受付の上限）。**設定が唯一の出所**。
     *
     * <p>未設定・空・数値でない・範囲外は {@link com.study21.common.core.exception.ValidationException}。
     * コード側の既定値へは落とさない（落とすと「設定を消しても動く」＝設定漏れに気づけない）。</p>
     */
    public static int limitOf(SettingsService settingsService, String batchCode) {
        String key = JapaneseWordAiSettings.prefixOf(batchCode) + "_BATCH_MAX";
        String value = settingsService.requireGlobal(batchCode, JapaneseWordAiSettings.PAGE_CODE, key);
        String text = value == null ? "" : value.trim();
        int configured;
        try {
            configured = Integer.parseInt(text);
        } catch (NumberFormatException cause) {
            throw new ValidationException("設定「" + key + "」（設定ページの「1 回の最大単語数」）は数値で設定してください。");
        }
        if (configured < CONFIGURED_MIN || configured > CONFIGURED_MAX) {
            throw new ValidationException("設定「" + key + "」（設定ページの「1 回の最大単語数」）は "
                    + CONFIGURED_MIN + "〜" + CONFIGURED_MAX + " の範囲で設定してください。");
        }
        return configured;
    }

    /**
     * 取得区分ごとの上限（画面の窓がそのまま出す形）。
     *
     * <p>設定が読めないときは例外にする（{@link #limitOf}）。</p>
     *
     * @return {@code { "DETAIL": {"batchCode":"batC41","limit":10}, "C": {...}, ... }}
     *         （並びは DETAIL → C → D → E）
     */
    public static Map<String, Map<String, Object>> byKind(SettingsService settingsService) {
        Map<String, Map<String, Object>> limits = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : BATCH_OF_KIND.entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("batchCode", entry.getValue());
            one.put("limit", limitOf(settingsService, entry.getValue()));
            limits.put(entry.getKey(), one);
        }
        return limits;
    }
}
