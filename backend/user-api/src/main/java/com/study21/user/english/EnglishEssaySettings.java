package com.study21.user.english;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 英作文AI添削の設定（`COM_設定情報` の `ENGLISH_ESSAY` ページ）を読む。
 *
 * <p>設定の置き場は `COM_設定情報`（GLOBAL）だけで、**保存は admin-api の設定画面**が行う。
 * user-api は読むだけ（{@link EnglishEssaySettingMapper}）。扱いを
 * {@link com.study21.user.classroom.ClassroomAiSettings} と揃える:</p>
 *
 * <ul>
 *   <li>画像の上限（枚数・1 枚の大きさ）: 未設定なら **seed と同じ値**（8 枚・10MB）を使う
 *       （画面が使えなくなるより、制限付きで動く方がよい）</li>
 *   <li>値が正しくない（0 以下・数値でない）ときも同じ既定値へ落とす
 *       （画面へ配る `GET /api/user/english-essays/limits` もこの値を使う）</li>
 * </ul>
 *
 * <p>AI の接続情報（プロバイダ・プロンプト）は admin-api のバッチ（batC11 / batC12）が読む。
 * user-api では扱わない（**API Key を画面へ出さない**）。</p>
 */
@Component
public class EnglishEssaySettings {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssaySettings.class);

    /** 設定ページ（COM_設定項目 のページ区分）。 */
    public static final String PAGE = "ENGLISH_ESSAY";

    /** 1 回に上げられる画像の枚数。 */
    public static final String KEY_MAX_IMAGES = "ENGLISH_ESSAY_MAX_IMAGES";
    /** 画像 1 枚の最大サイズ（MB）。 */
    public static final String KEY_MAX_IMAGE_MB = "ENGLISH_ESSAY_MAX_IMAGE_MB";

    private static final List<String> KEYS = List.of(KEY_MAX_IMAGES, KEY_MAX_IMAGE_MB);

    /** seed と同じ値（`TBL_COM_設定情報_init.sql`）。未設定・読めないときだけ使う。 */
    public static final int DEFAULT_MAX_IMAGES = 8;
    public static final int DEFAULT_MAX_IMAGE_MB = 10;

    private final EnglishEssaySettingMapper settingMapper;

    public EnglishEssaySettings(EnglishEssaySettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    /** 設定値のひとまとまり（1 リクエストの処理中は同じ値を使う）。 */
    public record Snapshot(Map<String, String> values) {

        public String raw(String key) {
            String value = values.get(key);
            return value == null || value.isBlank() ? null : value.trim();
        }

        public int number(String key, int fallback) {
            String value = raw(key);
            if (value == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException cause) {
                return fallback;
            }
        }
    }

    /** `ENGLISH_ESSAY` ページの設定をまとめて読む（1 クエリ）。 */
    public Snapshot load() {
        Map<String, String> values = new LinkedHashMap<>();
        for (EnglishEssaySettingEntity entity : settingMapper.findByKeys(PAGE, KEYS)) {
            values.put(entity.getSettingKey(), entity.getSettingValue());
        }
        return new Snapshot(values);
    }

    /** 1 回に上げられる画像の枚数（既定 8）。 */
    public int maxImages(Snapshot snapshot) {
        return positive(snapshot.number(KEY_MAX_IMAGES, DEFAULT_MAX_IMAGES), KEY_MAX_IMAGES);
    }

    /** 画像 1 枚の最大サイズ（MB。既定 10）。 */
    public int maxImageMb(Snapshot snapshot) {
        return positive(snapshot.number(KEY_MAX_IMAGE_MB, DEFAULT_MAX_IMAGE_MB), KEY_MAX_IMAGE_MB);
    }

    private static int positive(int value, String key) {
        if (value > 0) {
            return value;
        }
        log.warn("英作文AI添削の設定値が正しくないため既定値を使います。key={} value={}", key, value);
        return KEY_MAX_IMAGES.equals(key) ? DEFAULT_MAX_IMAGES : DEFAULT_MAX_IMAGE_MB;
    }
}
