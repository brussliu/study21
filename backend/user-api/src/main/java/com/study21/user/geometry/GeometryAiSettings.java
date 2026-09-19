package com.study21.user.geometry;

import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiModelSlot;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生図・AI 画図助手の設定（`GEOMETRY_AI` ページ）と、AI の接続設定（`AI_MODEL` ページ）を読む。
 *
 * <p>設定の置き場は `COM_設定情報`（GLOBAL）だけで、**保存は admin-api の設定画面**が行う。
 * user-api は読むだけ（`GeometryAiSettingMapper`）。AI バッチ（batC51/52/53）は admin-api が
 * `SettingsService.requireSettings` で厳密に検証するが、こちらは**画面に出す上限と既定値**を
 * 読むためのものなので、欠落時の扱いを次のように分けている:</p>
 *
 * <ul>
 *   <li>有効／無効（`GEOMETRY_AI_ENABLED`）: 未設定・空・'true' 以外は**無効**として扱う
 *       （機能スイッチの fail-safe。勝手に AI を呼ばない）</li>
 *   <li>上限・回数（MB・画素数・日次上限）: 未設定なら **seed と同じ値**を使い、警告をログに残す
 *       （画面が使えなくなるより、制限付きで動く方がよい）</li>
 *   <li>AI の接続情報（URL・モデル・API Key）: 未設定なら**日本語の理由でエラー**
 *       （呼び出しを試みない）</li>
 * </ul>
 */
@Component
public class GeometryAiSettings {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiSettings.class);

    /** 設定ページ（COM_設定項目 のページ区分）。 */
    public static final String PAGE = "GEOMETRY_AI";
    /** AI の接続設定ページ（既存。スロットごとのモデル・URL・API Key）。 */
    public static final String AI_MODEL_PAGE = "AI_MODEL";

    public static final String KEY_ENABLED = "GEOMETRY_AI_ENABLED";
    public static final String KEY_PROVIDER = "GEOMETRY_AI_PROVIDER";
    public static final String KEY_MAX_IMAGE_MB = "GEOMETRY_AI_MAX_IMAGE_MB";
    public static final String KEY_MAX_IMAGE_PIXELS = "GEOMETRY_AI_MAX_IMAGE_PIXELS";
    public static final String KEY_DEFAULT_CROP = "GEOMETRY_AI_DEFAULT_CROP";
    public static final String KEY_DEFAULT_KIND = "GEOMETRY_AI_DEFAULT_KIND";
    public static final String KEY_APPROVAL = "GEOMETRY_AI_APPROVAL";
    public static final String KEY_DAILY_LIMIT = "GEOMETRY_AI_DAILY_LIMIT_PER_ACCOUNT";
    public static final String KEY_ASSIST_ENABLED = "GEOMETRY_AI_ASSIST_ENABLED";
    public static final String KEY_ASSIST_PROVIDER = "GEOMETRY_AI_ASSIST_PROVIDER";
    public static final String KEY_ASSIST_SYSTEM_PROMPT = "GEOMETRY_AI_ASSIST_SYSTEM_PROMPT";
    public static final String KEY_ASSIST_USER_PROMPT = "GEOMETRY_AI_ASSIST_USER_PROMPT";
    public static final String KEY_ASSIST_TIMEOUT = "GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS";
    public static final String KEY_ASSIST_MAX_COMMANDS = "GEOMETRY_AI_ASSIST_MAX_COMMANDS";
    public static final String KEY_ASSIST_DAILY_LIMIT = "GEOMETRY_AI_ASSIST_DAILY_LIMIT_PER_ACCOUNT";

    private static final List<String> KEYS = List.of(
            KEY_ENABLED, KEY_PROVIDER, KEY_MAX_IMAGE_MB, KEY_MAX_IMAGE_PIXELS, KEY_DEFAULT_CROP,
            KEY_DEFAULT_KIND, KEY_APPROVAL, KEY_DAILY_LIMIT, KEY_ASSIST_ENABLED, KEY_ASSIST_PROVIDER,
            KEY_ASSIST_SYSTEM_PROMPT, KEY_ASSIST_USER_PROMPT, KEY_ASSIST_TIMEOUT,
            KEY_ASSIST_MAX_COMMANDS, KEY_ASSIST_DAILY_LIMIT);

    /** seed と同じ値（`TBL_COM_設定情報_init.sql`）。未設定のときだけ使う。 */
    private static final int FALLBACK_MAX_IMAGE_MB = 10;
    private static final int FALLBACK_MAX_IMAGE_PIXELS = 1536;
    private static final int FALLBACK_DAILY_LIMIT = 20;
    private static final int FALLBACK_ASSIST_TIMEOUT = 60;
    private static final int FALLBACK_ASSIST_MAX_COMMANDS = 20;
    private static final int FALLBACK_ASSIST_DAILY_LIMIT = 50;

    private final GeometryAiSettingMapper settingMapper;

    public GeometryAiSettings(GeometryAiSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    /**
     * 設定値のひとまとまり（1 リクエストの処理中は同じ値を使う）。
     * 値は生の文字列で、解釈は下のアクセサが行う。
     */
    public record Snapshot(Map<String, String> values) {

        public String raw(String key) {
            String value = values.get(key);
            return value == null || value.isBlank() ? null : value.trim();
        }

        public boolean flag(String key, boolean fallback) {
            String value = raw(key);
            if (value == null) {
                return fallback;
            }
            return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
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

    /** `GEOMETRY_AI` ページの設定をまとめて読む（1 クエリ）。 */
    public Snapshot load() {
        Map<String, String> values = new LinkedHashMap<>();
        for (GeometryAiSettingEntity entity : settingMapper.findByKeys(PAGE, KEYS)) {
            values.put(entity.getSettingKey(), entity.getSettingValue());
        }
        return new Snapshot(values);
    }

    /** 未設定のキー（画面に理由を出すため）。 */
    public List<String> missingKeys(Snapshot snapshot) {
        List<String> missing = new java.util.ArrayList<>();
        for (String key : List.of(KEY_ENABLED, KEY_MAX_IMAGE_MB, KEY_MAX_IMAGE_PIXELS, KEY_DEFAULT_CROP,
                KEY_DEFAULT_KIND, KEY_APPROVAL)) {
            if (snapshot.raw(key) == null) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * AI 生図を使うか。
     *
     * <p><b>未設定なら有効</b>（利用者の指示で「有効／無効」の設定項目を画面から外したため、
     * 既定は有効にする）。明示的に `false` のときだけ無効になる。</p>
     */
    public boolean enabled(Snapshot snapshot) {
        return snapshot.flag(KEY_ENABLED, true);
    }

    /** AI 画図助手を使うか（AI 生図が有効であることが前提）。未設定なら有効。 */
    public boolean assistEnabled(Snapshot snapshot) {
        return enabled(snapshot) && snapshot.flag(KEY_ASSIST_ENABLED, true);
    }

    public int maxImageMb(Snapshot snapshot) {
        return positive(snapshot.number(KEY_MAX_IMAGE_MB, FALLBACK_MAX_IMAGE_MB), KEY_MAX_IMAGE_MB);
    }

    public int maxImagePixels(Snapshot snapshot) {
        return positive(snapshot.number(KEY_MAX_IMAGE_PIXELS, FALLBACK_MAX_IMAGE_PIXELS), KEY_MAX_IMAGE_PIXELS);
    }

    /** 画面を開いたときの切り抜き（manual = 毎回指定 / center = 中央 70% / all = 全体）。 */
    public String defaultCrop(Snapshot snapshot) {
        String value = snapshot.raw(KEY_DEFAULT_CROP);
        return value == null || !List.of("manual", "center", "all").contains(value) ? "manual" : value;
    }

    /** 画面の分類の初期値（figure / function / mixed）。 */
    public String defaultKind(Snapshot snapshot) {
        String value = snapshot.raw(KEY_DEFAULT_KIND);
        return value == null || !List.of("figure", "function", "mixed").contains(value) ? "figure" : value;
    }

    /** 承認フロー（manual / auto）。**サーバーに GeoGebra が無いので当面 manual だけを使う。** */
    public String approval(Snapshot snapshot) {
        String value = snapshot.raw(KEY_APPROVAL);
        return value == null || !List.of("manual", "auto").contains(value) ? "manual" : value;
    }

    /** 1 アカウント 1 日の生図回数（0 = 無制限）。 */
    public int dailyLimit(Snapshot snapshot) {
        int value = snapshot.number(KEY_DAILY_LIMIT, FALLBACK_DAILY_LIMIT);
        return Math.max(0, value);
    }

    /** 1 アカウント 1 日の助手の指示回数（0 = 無制限）。 */
    public int assistDailyLimit(Snapshot snapshot) {
        return Math.max(0, snapshot.number(KEY_ASSIST_DAILY_LIMIT, FALLBACK_ASSIST_DAILY_LIMIT));
    }

    /**
     * 要求の受付時に**固定する「有効な設定」**を作る（要求行の「設定スナップショット」へ入れる）。
     *
     * <p>理由（設計 §6）: 実行はバックエンドの働き手が後から行うので、待ち行列に並んでいる間に
     * 設定を変えると、**利用者が見た条件と違う条件で AI が走る**ことになる。受付時に
     * 「共通 → モード別」の継承を適用した結果（プロンプトの本文とモデルのパラメータ）を
     * ここで固めておき、働き手はそれをそのまま使う。</p>
     *
     * <p>固定するのは**モデルのスロット（例 `qwen:4`）と、そのときのモデル名**の両方。
     * スロットだけだと、同じ ID の下でモデル名を書き換えたときに、並んでいる要求が黙って
     * 別のモデルへ移ってしまう。モデル名が読めない（`AI_MODEL` が未設定の）ときは空で固定し、
     * 実行の工程が呼ぶ直前に読み直して固定する。</p>
     *
     * <p>**秘密（API Key・URL）は入れない**。入れるのはプロンプトの本文・モデルのスロットと
     * モデル名・上限・出力形式・タイムアウト・再実行回数と、**実行版**（{@code revision}）だけ。</p>
     *
     * @param mode     作図モード（A〜D。null は歴史的な要求＝ A として固定する）
     * @param revision 実行版（受付＝1。利用者が入力を作り直したら +1）
     * @throws com.study21.common.core.exception.ValidationException 共通の System Prompt が未設定のとき
     */
    public String pinnedConfigJson(String mode, int revision) {
        String normalized = AiFigureSettingKeys.normalizeMode(mode);
        if (normalized.isEmpty() || !List.of("A", "B", "C", "D").contains(normalized)) {
            normalized = "A";
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (GeometryAiSettingEntity entity
                : settingMapper.findByKeys(PAGE, AiFigureSettingKeys.keysFor(normalized))) {
            values.put(entity.getSettingKey(), entity.getSettingValue());
        }
        String provider = values.get(AiFigureSettingKeys.PROVIDER);
        String modeProvider = values.get(AiFigureSettingKeys.modeKey(normalized,
                AiFigureSettingKeys.SUFFIX_PROVIDER));
        if (modeProvider != null && !modeProvider.isBlank()) {
            provider = modeProvider;
        }
        return AiFigureConfig.resolve(normalized, null, values, OffsetDateTime.now().toString(),
                currentModelName(provider), revision).toSnapshotJson(null);
    }

    /** 受付時のモデル名（`AI_MODEL` のスロットから読む。読めなければ null＝実行時に固定する）。 */
    private String currentModelName(String providerSlot) {
        return AiModelSlot.of(providerSlot)
                .flatMap(slot -> settingMapper
                        .findByKeys(AiModelSlot.PAGE, List.of(slot.modelKey())).stream()
                        .findFirst()
                        .map(GeometryAiSettingEntity::getSettingValue))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }

    /**
     * いまの要求の次の**実行版**（スナップショットの revision + 1）。
     *
     * <p>利用者が入力を直して出し直したときに使う。技術的な再試行（働き手の拾い直し・
     * AI の再呼び出し）では**増やさない**（同じ版のまま再開する）。</p>
     */
    public int nextRevision(String snapshotJson) {
        return AiFigureConfig.fromSnapshotJson(snapshotJson).map(config -> config.revision() + 1).orElse(1);
    }

    public int assistMaxCommands(Snapshot snapshot) {
        return positive(snapshot.number(KEY_ASSIST_MAX_COMMANDS, FALLBACK_ASSIST_MAX_COMMANDS),
                KEY_ASSIST_MAX_COMMANDS);
    }

    public int assistTimeoutSeconds(Snapshot snapshot) {
        return positive(snapshot.number(KEY_ASSIST_TIMEOUT, FALLBACK_ASSIST_TIMEOUT), KEY_ASSIST_TIMEOUT);
    }

    private static int positive(int value, String key) {
        if (value > 0) {
            return value;
        }
        log.warn("AI 生図の設定値が正しくないため既定値を使います。key={} value={}", key, value);
        return switch (key) {
            case KEY_MAX_IMAGE_MB -> FALLBACK_MAX_IMAGE_MB;
            case KEY_MAX_IMAGE_PIXELS -> FALLBACK_MAX_IMAGE_PIXELS;
            case KEY_ASSIST_MAX_COMMANDS -> FALLBACK_ASSIST_MAX_COMMANDS;
            default -> FALLBACK_ASSIST_TIMEOUT;
        };
    }
}
