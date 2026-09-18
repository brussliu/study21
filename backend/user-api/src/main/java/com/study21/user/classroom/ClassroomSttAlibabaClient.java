package com.study21.user.classroom;

import com.study21.common.core.stt.DashScopeAsrClient;

import java.util.Locale;

/**
 * 阿里巴巴（DashScope）の Paraformer-Realtime-V2 で 1 分塊を書き起こす。
 *
 * <p>プロトコル（WebSocket の run-task → 音声 → finish-task）は
 * {@link DashScopeAsrClient}（common-core。設定ページの【接続テスト】とも共用）が持つ。
 * ここは**授業録音の分塊を DashScope の言葉に翻訳する**だけ:</p>
 *
 * <ul>
 *   <li>音声は**ヘッダ無しの 16bit PCM**（画面が分塊ごとに作って送る `audio/L16`）を想定する。
 *       WAV で来たときは `wav` として送る。それ以外（webm など）は認識できないので理由を返す。</li>
 *   <li>言語は BCP-47（ja-JP / zh-CN / en-US）→ DashScope のヒント（ja / zh / en）。</li>
 *   <li>結果は 1 分塊 = 1 セグメントとして返す（時刻はサーバーが分塊から決める）。</li>
 * </ul>
 */
public class ClassroomSttAlibabaClient {

    /** DashScope へ送るサンプリング周波数（画面が作る PCM に合わせる）。 */
    static final int SAMPLE_RATE = 16000;

    /**
     * 宣言するサンプリング周波数。
     *
     * <p>**ヘッダ無し PCM（画面が作る音声）は 16kHz**（画面が作る値に合わせる）。
     * 取り込んだ mp3・wav は**ファイル自身の周波数**（44.1kHz など）を持つので、ヘッダから読んで渡す
     * （16kHz を宣言すると DashScope が「sample rate 16000 not equals with real 44100」で
     * デコードに失敗する＝実測）。読めないときは 0（`sample_rate` を送らない）。</p>
     */
    static int sampleRateOf(String format, byte[] audio) {
        if ("pcm".equals(format)) {
            return SAMPLE_RATE;
        }
        return AudioFileSampleRate.of(audio, format);
    }

    private final DashScopeAsrClient client;

    public ClassroomSttAlibabaClient(DashScopeAsrClient client) {
        this.client = client;
    }

    /** 1 分塊を書き起こす。 */
    public ClassroomSttClient.SttResponse transcribe(ClassroomSttClient.SttRequest request) {
        String format = formatOf(request.mime());
        if (format == null) {
            return ClassroomSttClient.SttResponse.failure(0, "HTTP_4XX",
                    "この音声形式（" + (request.mime() == null || request.mime().isBlank() ? "不明" : request.mime())
                            + "）は Paraformer-Realtime-V2 では認識できません。"
                            + "Chrome / Edge で録音し直してください。");
        }
        DashScopeAsrClient.Result result = client.transcribe(new DashScopeAsrClient.Request(
                request.url(),
                request.apiKey(),
                request.model(),
                format,
                sampleRateOf(format, request.audio()),
                languageHintOf(request.languageCode()),
                request.audio(),
                request.timeoutSeconds()));
        if (!result.ok()) {
            // 4xx 相当（キー・モデルの指定違い）は再試行しても直らないので fatal にする
            String code = "TIMEOUT".equals(result.errorCode()) || "CONNECT_FAILED".equals(result.errorCode())
                    ? result.errorCode() : "HTTP_4XX";
            return ClassroomSttClient.SttResponse.failure(0, code, result.errorMessage());
        }
        String text = result.text() == null ? "" : result.text().trim();
        return ClassroomSttClient.SttResponse.success(200,
                text.isEmpty() ? java.util.List.of()
                        : java.util.List.of(new ClassroomSttClient.Segment(text, null, request.languageCode(),
                        null, null)));
    }

    /**
     * アップロードの MIME → DashScope の `format`。認識できない形式は null。
     *
     * <p>`audio/L16` は画面が作る**ヘッダ無しの 16bit PCM**（RFC 4856）で、DashScope へは `pcm` として送る。</p>
     */
    static String formatOf(String mime) {
        String value = mime == null ? "" : mime.trim();
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "audio/l16", "audio/pcm", "audio/raw" -> "pcm";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/opus", "audio/ogg" -> "opus";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            default -> null;
        };
    }

    /** BCP-47（ja-JP）→ DashScope の言語ヒント（ja）。分からない言語は null（自動）。 */
    static String languageHintOf(String languageCode) {
        String value = languageCode == null ? "" : languageCode.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        int dash = value.indexOf('-');
        String primary = dash > 0 ? value.substring(0, dash) : value;
        return switch (primary) {
            case "ja", "zh", "en", "ko", "yue", "de", "fr", "ru", "es", "it", "pt", "ar", "hi", "th", "vi" -> primary;
            default -> null;
        };
    }
}
