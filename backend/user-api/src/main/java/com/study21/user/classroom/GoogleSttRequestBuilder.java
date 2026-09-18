package com.study21.user.classroom;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;

/**
 * Google Cloud Speech-to-Text v1（`speech:recognize`）の JSON リクエストを組み立てる。
 *
 * <p>OpenAI 互換の multipart とは形式が違うため、provider=`google` のときだけこのビルダーを使う
 * （{@link ClassroomSttHttpClient} が分岐する）。**API Key は URL の {@code key} クエリパラメータ**
 * に付ける（Authorization ヘッダではない）。</p>
 *
 * <p>音声は {@code audio.content} に base64 で入れる。エンコードはアップロードの MIME から決める
 * （ブラウザの {@code MediaRecorder} は Chrome=webm/opus、Firefox=ogg/opus、Safari=mp4/AAC）。
 * Opus 系（WEBM_OPUS / OGG_OPUS）はコンテナがサンプルレートを持つので
 * **sampleRateHertz は送らない**（mp3 も bitstream から読めるので送らない）。</p>
 *
 * <p>言語コードは {@link ClassroomAiSettings#sttLanguageCode} が返す BCP-47（例 ja-JP）をそのまま
 * 主言語に使い、混在モードは {@link ClassroomAiSettings#sttAlternativeLanguageCode} が返す
 * {@code alternativeLanguageCode}（例 en-US）を alternativeLanguageCodes に載せる。</p>
 */
public final class GoogleSttRequestBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoogleSttRequestBuilder() {
    }

    /**
     * アップロードの MIME → Google の AudioEncoding。非対応（mp4/AAC など）は null。
     *
     * <p>対応表:</p>
     * <ul>
     *   <li>{@code audio/webm} / {@code video/webm}（Chrome）→ {@code WEBM_OPUS}</li>
     *   <li>{@code audio/ogg} / {@code application/ogg}（Firefox）→ {@code OGG_OPUS}</li>
     *   <li>{@code audio/l16} / {@code audio/pcm}（画面が分塊ごとに作る**ヘッダ無しの 16bit PCM**）
     *       → {@code LINEAR16}。このときだけ {@code sampleRateHertz} を付ける（ヘッダが無いため）</li>
     *   <li>{@code audio/mpeg} / {@code audio/mp3} → {@code MP3}</li>
     *   <li>空・不明 → {@code WEBM_OPUS}（本機能の既定 MIME が webm のため）</li>
     *   <li>{@code audio/mp4} / {@code audio/m4a} 等 → null（Google v1 は AAC/mp4 を直接受け付けない）</li>
     * </ul>
     */
    public static String encodingOf(String mime) {
        String value = mime == null ? "" : mime.trim();
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "audio/webm", "video/webm", "" -> "WEBM_OPUS";
            case "audio/ogg", "application/ogg" -> "OGG_OPUS";
            case "audio/l16", "audio/pcm", "audio/raw" -> "LINEAR16";
            case "audio/mpeg", "audio/mp3" -> "MP3";
            default -> null;
        };
    }

    /** 非対応エンコードの日本語メッセージ（画面の録音方式を変えてもらう）。 */
    public static String unsupportedEncodingMessage(String mime) {
        return "Google Speech-to-Text はこの音声形式（" + (mime == null || mime.isBlank() ? "不明" : mime)
                + "）を直接受け付けません。Chrome（webm）か Firefox（ogg）で録音してください。";
    }

    /**
     * `sampleRateHertz`（ヘッダを持たない形式のときだけ返す。持つ形式はコンテナ・bitstream から読める）。
     *
     * <p>画面が作る `audio/L16` は 16kHz モノラル 16bit 固定なので 16000 を返す。</p>
     */
    public static Integer sampleRateOf(String mime) {
        String encoding = encodingOf(mime);
        return "LINEAR16".equals(encoding) ? 16000 : null;
    }

    /** STT モデル（空なら Google 既定の `latest_long`）。 */
    public static String modelOf(String model) {
        return model == null || model.isBlank() ? "latest_long" : model.trim();
    }

    /** API Key を `key` クエリパラメータとして付けた URL。既に `key=` があれば付けない。 */
    public static URI buildUri(String url, String apiKey) {
        String value = url == null ? "" : url.trim();
        if (value.contains("key=")) {
            return URI.create(value);
        }
        String separator = value.contains("?") ? "&" : "?";
        return URI.create(value + separator + "key=" + apiKey);
    }

    /** `speech:recognize` の JSON 本文を組み立てる。 */
    public static String buildBody(ClassroomSttClient.SttRequest request) {
        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode config = root.putObject("config");
        config.put("encoding", encodingOf(request.mime()));
        config.put("languageCode", request.languageCode() == null || request.languageCode().isBlank()
                ? "ja-JP" : request.languageCode());
        config.put("model", modelOf(request.model()));
        config.put("enableAutomaticPunctuation", true);
        if (request.alternativeLanguageCode() != null && !request.alternativeLanguageCode().isBlank()) {
            ArrayNode alternatives = config.putArray("alternativeLanguageCodes");
            alternatives.add(request.alternativeLanguageCode());
        }
        // ヘッダを持たない形式（LINEAR16）のときだけ sampleRateHertz を付ける。
        // WEBM_OPUS / OGG_OPUS / MP3 はコンテナ・bitstream から読めるので付けない
        Integer sampleRate = sampleRateOf(request.mime());
        if (sampleRate != null) {
            config.put("sampleRateHertz", sampleRate);
        }

        ObjectNode audio = root.putObject("audio");
        byte[] bytes = request.audio() == null ? new byte[0] : request.audio();
        audio.put("content", Base64.getEncoder().encodeToString(bytes));
        return root.toString();
    }
}
