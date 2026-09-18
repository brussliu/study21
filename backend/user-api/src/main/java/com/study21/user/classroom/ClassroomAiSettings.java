package com.study21.user.classroom;

import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 授業録音 / AI 授業記録の設定（`CLASSROOM_AI` ページ）を読む。
 *
 * <p>設定の置き場は `COM_設定情報`（GLOBAL）だけで、**保存は admin-api の設定画面**が行う。
 * user-api は読むだけ（{@link ClassroomAiSettingMapper}）。扱いを {@link com.study21.user.geometry.GeometryAiSettings}
 * と揃える:</p>
 *
 * <ul>
 *   <li>有効／無効（`CLASSROOM_AI_ENABLED`）: **未設定なら有効**、明示的に `false` のときだけ無効
 *       （利用者の指示で「有効／無効」の設定項目を画面から外したため）</li>
 *   <li>上限・分塊長・トリガー: 未設定なら **seed と同じ値**を使う</li>
 *   <li>STT の接続情報（endpoint / apiKey / model）: 未設定なら**日本語の理由でエラー**
 *       （スタブのときは呼ばないので不要）</li>
 * </ul>
 */
@Component
public class ClassroomAiSettings {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiSettings.class);

    /** 設定ページ（COM_設定項目 のページ区分）。 */
    public static final String PAGE = "CLASSROOM_AI";

    /**
     * STT の接続情報を置く設定ページ（AIモデル）。
     *
     * <p>音声認識も AI サービスなので、接続情報（モデル・URL・API Key）は「AIモデル」ページの
     * タブで管理する（利用者の指示: Google Speech-to-Text と Alibaba Paraformer-Realtime-V2 を
     * AI モデルと同じ扱いにする）。provider ごとに 1 組だけ持つ。</p>
     */
    public static final String PAGE_AI_MODEL = "AI_MODEL";

    public static final String KEY_ENABLED = "CLASSROOM_AI_ENABLED";
    public static final String KEY_STT_PROVIDER = "CLASSROOM_AI_STT_PROVIDER";
    /**
     * STT を**ブラウザ（Web Speech API）**で行うプロバイダー値。
     *
     * <p>認識は画面（Chrome / Edge）が行い、結果は `POST /classrooms/{id}/transcripts` で受け取る。
     * サーバーは STT を呼ばないので **モデル・Endpoint・API Key は不要**（未設定でも動く）。</p>
     */
    public static final String PROVIDER_BROWSER = "browser";
    /** Google Cloud Speech-to-Text v1（`speech:recognize`）。接続情報は「AIモデル」ページ。 */
    public static final String PROVIDER_GOOGLE = "google";
    /** 阿里巴巴 DashScope の Paraformer-Realtime-V2（WebSocket）。接続情報は「AIモデル」ページ。 */
    public static final String PROVIDER_ALIBABA = "alibaba";

    /** 「AIモデル」ページの Google Speech-to-Text タブ（接続情報）。 */
    public static final String KEY_GOOGLE_STT_MODEL = "AI_GOOGLE_STT_MODEL";
    public static final String KEY_GOOGLE_STT_URL = "AI_GOOGLE_STT_URL";
    public static final String KEY_GOOGLE_STT_API_KEY = "AI_GOOGLE_STT_API_KEY";
    /** 「AIモデル」ページの Alibaba Paraformer-Realtime-V2 タブ（接続情報）。 */
    public static final String KEY_ALIBABA_STT_MODEL = "AI_ALIBABA_STT_MODEL";
    public static final String KEY_ALIBABA_STT_URL = "AI_ALIBABA_STT_URL";
    public static final String KEY_ALIBABA_STT_API_KEY = "AI_ALIBABA_STT_API_KEY";

    /**
     * 旧プロバイダー（whisper / azure / other）が使う接続情報。
     *
     * <p>画面の選択肢が browser / google / alibaba の 3 つになったので、これらは互換のために
     * 残している（DB に値が残っていても読み書きでき、保存もできる）。</p>
     */
    public static final String KEY_STT_ENDPOINT = "CLASSROOM_AI_STT_ENDPOINT";
    public static final String KEY_STT_API_KEY = "CLASSROOM_AI_STT_API_KEY";
    public static final String KEY_STT_MODEL = "CLASSROOM_AI_STT_MODEL";
    public static final String KEY_STT_TIMEOUT_SECONDS = "CLASSROOM_AI_STT_TIMEOUT_SECONDS";
    public static final String KEY_CHUNK_SECONDS = "CLASSROOM_AI_CHUNK_SECONDS";
    /**
     * 授業の AI 解析（フェーズノート batC61 / 最終まとめ batC62）を使うか。
     *
     * <p>**false のときは PENDING のノート行を作らない**（＝ AI を呼ぶ入口が無くなる）。
     * 書き起こし（STT）だけを試したいときに使う（利用者の指示）。</p>
     */
    public static final String KEY_NOTE_ENABLED = "CLASSROOM_AI_NOTE_ENABLED";
    // 言語コード（CLASSROOM_AI_LANG_*）は**コードで固定**したため、もう読み込まない
    // （旧キーは DB に残っていても無視される。利用者の指示: 画面で設定しない）
    public static final String KEY_TRIGGER_INTERVAL_MINUTES = "CLASSROOM_AI_TRIGGER_INTERVAL_MINUTES";
    public static final String KEY_TRIGGER_MIN_CHARS = "CLASSROOM_AI_TRIGGER_MIN_CHARS";
    public static final String KEY_TRIGGER_KEYWORDS = "CLASSROOM_AI_TRIGGER_KEYWORDS";
    public static final String KEY_TRIGGER_COOLDOWN_MINUTES = "CLASSROOM_AI_TRIGGER_COOLDOWN_MINUTES";
    public static final String KEY_MAX_RECORDING_MINUTES = "CLASSROOM_AI_MAX_RECORDING_MINUTES";
    public static final String KEY_RETENTION_DAYS = "CLASSROOM_AI_RETENTION_DAYS";
    public static final String KEY_DAILY_LIMIT = "CLASSROOM_AI_DAILY_LIMIT_PER_ACCOUNT";
    public static final String KEY_VIEW_SCOPE = "CLASSROOM_AI_VIEW_SCOPE";

    private static final List<String> KEYS = List.of(
            KEY_ENABLED, KEY_STT_PROVIDER, KEY_STT_ENDPOINT, KEY_STT_API_KEY, KEY_STT_MODEL,
            KEY_STT_TIMEOUT_SECONDS, KEY_CHUNK_SECONDS, KEY_NOTE_ENABLED,
            KEY_TRIGGER_INTERVAL_MINUTES, KEY_TRIGGER_MIN_CHARS, KEY_TRIGGER_KEYWORDS,
            KEY_TRIGGER_COOLDOWN_MINUTES, KEY_MAX_RECORDING_MINUTES, KEY_RETENTION_DAYS,
            KEY_DAILY_LIMIT, KEY_VIEW_SCOPE);

    /** 「AIモデル」ページから読む STT の接続情報（provider ごとに 1 組）。 */
    private static final List<String> STT_CONNECTION_KEYS = List.of(
            KEY_GOOGLE_STT_MODEL, KEY_GOOGLE_STT_URL, KEY_GOOGLE_STT_API_KEY,
            KEY_ALIBABA_STT_MODEL, KEY_ALIBABA_STT_URL, KEY_ALIBABA_STT_API_KEY);

    /** seed と同じ値（`TBL_COM_設定情報_init.sql`）。未設定のときだけ使う。 */
    private static final int FALLBACK_CHUNK_SECONDS = 20;
    private static final int FALLBACK_MAX_RECORDING_MINUTES = 120;
    private static final int FALLBACK_RETENTION_DAYS = 30;
    private static final int FALLBACK_TRIGGER_INTERVAL = 5;
    private static final int FALLBACK_TRIGGER_MIN_CHARS = 200;
    private static final int FALLBACK_COOLDOWN_MINUTES = 3;
    private static final int FALLBACK_STT_TIMEOUT_SECONDS = 60;
    /** Google Speech-to-Text v1（`speech:recognize`）の既定。 */
    private static final String FALLBACK_GOOGLE_STT_MODEL = "latest_long";
    private static final String FALLBACK_GOOGLE_STT_URL = "https://speech.googleapis.com/v1/speech:recognize";
    private static final String FALLBACK_GOOGLE_STT_TAB = "Google Speech-to-Text";
    /** 阿里巴巴 DashScope Paraformer-Realtime-V2（WebSocket）の既定。 */
    private static final String FALLBACK_ALIBABA_STT_MODEL = "paraformer-realtime-v2";
    private static final String FALLBACK_ALIBABA_STT_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private static final String FALLBACK_ALIBABA_STT_TAB = "Alibaba Paraformer-Realtime-V2";

    private final ClassroomAiSettingMapper settingMapper;
    /** 検証用のスタブ（`study21.classroom-ai.stub=true`）。有効なら STT を実際に呼ばない。 */
    private final boolean stub;

    public ClassroomAiSettings(ClassroomAiSettingMapper settingMapper,
                               @Value("${study21.classroom-ai.stub:false}") boolean stub) {
        this.settingMapper = settingMapper;
        this.stub = stub;
    }

    /** STT の接続情報（設定から解決したもの）。 */
    public record SttConnection(String provider, String model, String url, String apiKey) {
    }

    /** 設定値のひとまとまり（1 リクエストの処理中は同じ値を使う）。 */
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

    /** `CLASSROOM_AI` ページと、STT の接続情報がある `AI_MODEL` ページの設定をまとめて読む（2 クエリ）。 */
    public Snapshot load() {
        Map<String, String> values = new LinkedHashMap<>();
        loadInto(values, PAGE, KEYS);
        loadInto(values, PAGE_AI_MODEL, STT_CONNECTION_KEYS);
        return new Snapshot(values);
    }

    private void loadInto(Map<String, String> values, String pageCode, List<String> keys) {
        for (ClassroomAiSettingEntity entity : settingMapper.findByKeys(pageCode, keys)) {
            values.put(entity.getSettingKey(), entity.getSettingValue());
        }
    }

    /**
     * 授業録音 / AI 授業記録を使うか。
     *
     * <p><b>未設定なら有効</b>（利用者の指示で「無効にする設定項目」を画面から外したため、
     * 既定は有効にする）。明示的に `false` のときだけ無効になる。</p>
     */
    public boolean enabled(Snapshot snapshot) {
        return snapshot.flag(KEY_ENABLED, true);
    }

    public boolean stub() {
        return stub;
    }

    public int chunkSeconds(Snapshot snapshot) {
        return positive(snapshot.number(KEY_CHUNK_SECONDS, FALLBACK_CHUNK_SECONDS), FALLBACK_CHUNK_SECONDS);
    }

    public int maxRecordingMinutes(Snapshot snapshot) {
        return positive(snapshot.number(KEY_MAX_RECORDING_MINUTES, FALLBACK_MAX_RECORDING_MINUTES),
                FALLBACK_MAX_RECORDING_MINUTES);
    }

    public int retentionDays(Snapshot snapshot) {
        return positive(snapshot.number(KEY_RETENTION_DAYS, FALLBACK_RETENTION_DAYS), FALLBACK_RETENTION_DAYS);
    }

    /** 1 アカウント 1 日の録音回数（0 = 無制限）。 */
    public int dailyLimit(Snapshot snapshot) {
        return Math.max(0, snapshot.number(KEY_DAILY_LIMIT, 0));
    }

    public int triggerIntervalMinutes(Snapshot snapshot) {
        return positive(snapshot.number(KEY_TRIGGER_INTERVAL_MINUTES, FALLBACK_TRIGGER_INTERVAL),
                FALLBACK_TRIGGER_INTERVAL);
    }

    public int triggerMinChars(Snapshot snapshot) {
        return positive(snapshot.number(KEY_TRIGGER_MIN_CHARS, FALLBACK_TRIGGER_MIN_CHARS),
                FALLBACK_TRIGGER_MIN_CHARS);
    }

    public int triggerCooldownMinutes(Snapshot snapshot) {
        return positive(snapshot.number(KEY_TRIGGER_COOLDOWN_MINUTES, FALLBACK_COOLDOWN_MINUTES),
                FALLBACK_COOLDOWN_MINUTES);
    }

    /** キーワードトリガー（カンマ区切りをばらす）。 */
    public List<String> triggerKeywords(Snapshot snapshot) {
        String value = snapshot.raw(KEY_TRIGGER_KEYWORDS);
        if (value == null) {
            return List.of("宿題", "試験の重点");
        }
        List<String> keywords = new ArrayList<>();
        for (String part : value.split("[,、]")) {
            String keyword = part.trim();
            if (!keyword.isEmpty() && !keywords.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    /**
     * 授業の AI 解析（フェーズノート / 最終まとめ）を使うか。**未設定なら有効**。
     *
     * <p>false のときはノート行を作らない（batC61 / batC62 を呼ぶ入口が無くなる）。
     * STT（書き起こし）の精度だけを確かめたいときに切る。</p>
     */
    public boolean noteEnabled(Snapshot snapshot) {
        return snapshot.flag(KEY_NOTE_ENABLED, true);
    }


    /**
     * STT をブラウザ（Web Speech API）で行うか。
     *
     * <p>true のときサーバーは STT を呼ばない（分塊は音声の保存だけ。書き起こしは画面が
     * 認識したテキストを送ってくる）。</p>
     */
    public boolean browserStt(Snapshot snapshot) {
        return PROVIDER_BROWSER.equals(snapshot.raw(KEY_STT_PROVIDER));
    }

    public int sttTimeoutSeconds(Snapshot snapshot) {
        return positive(snapshot.number(KEY_STT_TIMEOUT_SECONDS, FALLBACK_STT_TIMEOUT_SECONDS),
                FALLBACK_STT_TIMEOUT_SECONDS);
    }

    /**
     * 言語モード（zh / ja / en / zh-en / ja-en）→ STT へ渡す**言語コード**。
     *
     * <p><b>コードで固定</b>（利用者の指示で画面から設定項目を外した）。画面で言語コードを
     * 設定する必要はない。混在モード（zh-en / ja-en）は主言語のコードを返し、副言語は
     * {@link #sttAlternativeLanguageCode(String)} が返す。旧 'auto' とモード未指定は
     * 日本語（ja-JP）として扱う。</p>
     */
    public String sttLanguageCode(Snapshot snapshot, String languageMode) {
        return switch (languageMode == null ? "" : languageMode) {
            case "zh", "zh-en" -> "zh-CN";
            case "en" -> "en-US";
            case "ja", "ja-en" -> "ja-JP";
            default -> "ja-JP";
        };
    }

    /** 混在モードの**副言語**コード（対応する STT だけが使う）。混在でなければ null。 */
    public String sttAlternativeLanguageCode(String languageMode) {
        return switch (languageMode == null ? "" : languageMode) {
            case "zh-en", "ja-en" -> "en-US";
            default -> null;
        };
    }

    /**
     * STT の接続情報を解決する。
     *
     * <p>接続情報の置き場はプロバイダーで分かれる:</p>
     * <ul>
     *   <li>{@code browser} … 画面（Chrome / Edge）が認識するので**接続情報は要らない**</li>
     *   <li>{@code google} / {@code alibaba} … 「AIモデル」ページの専用タブ
     *       （Google Speech-to-Text / Alibaba Paraformer-Realtime-V2）。モデルと URL は
     *       seed と同じ既定値があり、**API Key だけは seed しない**ので未設定なら日本語で理由を出す</li>
     *   <li>{@code whisper} / {@code azure} / {@code other}（互換）… `CLASSROOM_AI` の
     *       `CLASSROOM_AI_STT_ENDPOINT` / `_API_KEY` / `_MODEL`</li>
     * </ul>
     *
     * <p>スタブ（provider=stub または `study21.classroom-ai.stub=true`）のときは接続情報が無くても動かす。</p>
     */
    public SttConnection resolveStt(Snapshot snapshot) {
        String provider = snapshot.raw(KEY_STT_PROVIDER);
        if (provider == null) {
            provider = "stub";
        }
        // ブラウザ認識はサーバーが STT を呼ばない（画面から結果を受け取る）ので接続情報が要らない
        if (browserStt(snapshot)) {
            return new SttConnection(PROVIDER_BROWSER, null, null, null);
        }
        if (stub || "stub".equals(provider)) {
            return new SttConnection("stub", snapshot.raw(KEY_STT_MODEL) == null ? "stub-model"
                    : snapshot.raw(KEY_STT_MODEL), "stub://local", "stub-key");
        }
        if (PROVIDER_GOOGLE.equals(provider)) {
            return aiModelConnection(snapshot, provider, KEY_GOOGLE_STT_MODEL, KEY_GOOGLE_STT_URL,
                    KEY_GOOGLE_STT_API_KEY, FALLBACK_GOOGLE_STT_MODEL, FALLBACK_GOOGLE_STT_URL,
                    FALLBACK_GOOGLE_STT_TAB);
        }
        if (PROVIDER_ALIBABA.equals(provider)) {
            return aiModelConnection(snapshot, provider, KEY_ALIBABA_STT_MODEL, KEY_ALIBABA_STT_URL,
                    KEY_ALIBABA_STT_API_KEY, FALLBACK_ALIBABA_STT_MODEL, FALLBACK_ALIBABA_STT_URL,
                    FALLBACK_ALIBABA_STT_TAB);
        }
        // 旧プロバイダー（whisper / azure / other）は今までどおり CLASSROOM_AI の接続情報を使う
        String model = snapshot.raw(KEY_STT_MODEL);
        String url = snapshot.raw(KEY_STT_ENDPOINT);
        String apiKey = snapshot.raw(KEY_STT_API_KEY);
        if (model == null) {
            throw new ValidationException("STT のモデル名が設定されていません（CLASSROOM_AI_STT_MODEL）。"
                    + "システム設定の「AI 授業記録（授業録音）」を確認してください。");
        }
        if (url == null) {
            throw new ValidationException("STT のエンドポイント URL が設定されていません（CLASSROOM_AI_STT_ENDPOINT）。"
                    + "システム設定の「AI 授業記録（授業録音）」を確認してください。");
        }
        if (apiKey == null) {
            throw new ValidationException("STT の API Key が設定されていません（CLASSROOM_AI_STT_API_KEY）。"
                    + "システム設定の「AI 授業記録（授業録音）」を確認してください。");
        }
        return new SttConnection(provider, model, url, apiKey);
    }

    /**
     * 「AIモデル」ページのタブから接続情報を組み立てる（モデルと URL は既定値で補い、API Key は必須）。
     *
     * <p>エラーの文言は**どのページのどのタブを開けばよいか**が分かるようにする
     * （キー名だけでは設定画面のどこか分からない）。</p>
     */
    private SttConnection aiModelConnection(Snapshot snapshot, String provider,
                                            String modelKey, String urlKey, String apiKeyKey,
                                            String fallbackModel, String fallbackUrl, String tabLabel) {
        String model = snapshot.raw(modelKey) == null ? fallbackModel : snapshot.raw(modelKey);
        String url = snapshot.raw(urlKey) == null ? fallbackUrl : snapshot.raw(urlKey);
        String apiKey = snapshot.raw(apiKeyKey);
        if (apiKey == null) {
            throw new ValidationException("STT の API Key が設定されていません（" + apiKeyKey + "）。"
                    + "システム設定の【AIモデル】ページの「" + tabLabel + "」で設定してください。");
        }
        return new SttConnection(provider, model, url, apiKey);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
