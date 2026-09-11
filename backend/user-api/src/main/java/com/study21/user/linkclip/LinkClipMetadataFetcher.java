package com.study21.user.linkclip;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL から Open Graph / meta 情報を取得する（2.0 の fetchMetadata 相当）。
 * 取得に失敗しても例外にせず空のメタ情報を返す（保存自体は成功させる）。
 * oEmbed や favicon は 2.0 でも未実装のため扱わない。
 */
@Component
public class LinkClipMetadataFetcher {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_CHARS = 400_000;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 抽出結果。取得できなかった項目は null。 */
    public record Metadata(String siteName, String title, String description, String publisherName,
                           Timestamp publishedAt, Integer videoSeconds, String thumbnailUrl) {}

    public Metadata fetch(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) return empty();
        if (LinkClipUrls.isLocalPath(trimmed)) return localFileMetadata(trimmed);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return empty();

        String html;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimmed))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Study21-LinkClip/1.0")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) return empty();
            String body = new String(response.body(), StandardCharsets.UTF_8);
            html = body.length() > MAX_BODY_CHARS ? body.substring(0, MAX_BODY_CHARS) : body;
        } catch (Exception ex) {
            return empty();
        }

        String title = firstNonNull(
                metaContent(html, "og:title"),
                metaContent(html, "twitter:title"),
                elementText(html, "title"));
        String description = firstNonNull(
                metaContent(html, "og:description"),
                metaContent(html, "description"),
                metaContent(html, "twitter:description"));
        String siteName = firstNonNull(
                metaContent(html, "og:site_name"),
                metaContent(html, "application-name"));
        String publisher = firstNonNull(
                metaContent(html, "author"),
                metaContent(html, "article:author"),
                metaContent(html, "itemprop:author"),
                elementTextByAttribute(html, "itemprop", "author"));
        String thumbnail = firstNonNull(
                metaContent(html, "og:image"),
                metaContent(html, "og:image:url"),
                metaContent(html, "twitter:image"));
        Timestamp publishedAt = parseTimestamp(firstNonNull(
                metaContent(html, "article:published_time"),
                metaContent(html, "datePublished"),
                elementTextByAttribute(html, "itemprop", "datePublished"),
                elementTextByAttribute(html, "pubdate", "pubdate")));
        Integer videoSeconds = parseDurationSeconds(firstNonNull(
                metaContent(html, "video:duration"),
                metaContent(html, "og:video:duration"),
                elementTextByAttribute(html, "itemprop", "duration")));

        // YouTube は OG 画像が無い場合のフォールバックを持つ（2.0 と同じ）。
        if (thumbnail == null) {
            String videoId = LinkClipUrls.youTubeVideoId(trimmed);
            if (videoId != null) thumbnail = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        }
        return new Metadata(trim(siteName), trim(title), trim(description), trim(publisher),
                publishedAt, videoSeconds, trim(thumbnail));
    }

    private Metadata localFileMetadata(String url) {
        String name = LinkClipUrls.localFileName(url);
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) extension = name.substring(dot + 1).toUpperCase(Locale.ROOT);
        String description = extension.isEmpty() ? url : extension + " ファイル / " + url;
        return new Metadata("ローカルファイル", name, description, "ローカルファイル", null, null, null);
    }

    private Metadata empty() {
        return new Metadata(null, null, null, null, null, null, null);
    }

    // ---------- HTML からの抽出 ----------

    /** <meta property="og:title" content="..."> / name= でも property= でも拾う。 */
    private String metaContent(String html, String key) {
        Pattern pattern = Pattern.compile(
                "<meta[^>]+(?:property|name)\\s*=\\s*[\"']" + Pattern.quote(key) + "[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            Matcher content = Pattern.compile("content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                    .matcher(tag);
            if (content.find()) return decode(content.group(1));
        }
        // content が前に来る書き方も拾う
        Pattern reversed = Pattern.compile(
                "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?(?:property|name)\\s*=\\s*[\"']"
                        + Pattern.quote(key) + "[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher reversedMatcher = reversed.matcher(html);
        return reversedMatcher.find() ? decode(reversedMatcher.group(1)) : null;
    }

    private String elementText(String html, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + "[^>]*>([\\s\\S]*?)</" + tagName + ">",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? decode(stripTags(matcher.group(1))) : null;
    }

    private String elementTextByAttribute(String html, String attribute, String value) {
        Pattern pattern = Pattern.compile("<[^>]+" + attribute + "\\s*=\\s*[\"']" + Pattern.quote(value)
                + "[\"'][^>]*>([\\s\\S]{0,400}?)<", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? decode(stripTags(matcher.group(1))) : null;
    }

    // ---------- 値の整形 ----------

    private String stripTags(String value) {
        return TAG.matcher(value).replaceAll(" ");
    }

    private String decode(String value) {
        if (value == null) return null;
        return value.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private String trim(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (trimmed != null) return trimmed;
        }
        return null;
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null) return null;
        for (String candidate : candidates(value)) {
            try {
                return Timestamp.from(OffsetDateTime.parse(candidate).toInstant());
            } catch (RuntimeException ignored) {
                // 次の形式を試す
            }
            try {
                return Timestamp.from(java.time.LocalDateTime.parse(candidate.replace(' ', 'T'))
                        .atZone(ZoneId.systemDefault()).toInstant());
            } catch (RuntimeException ignored) {
                // 次の形式を試す
            }
        }
        return null;
    }

    private List<String> candidates(String value) {
        List<String> list = new ArrayList<>(new LinkedHashSet<>(List.of(value.trim().split("\\s+"))));
        return list;
    }

    /** 秒数 or ISO-8601(PnDTnHnMnS) の両方を受ける。 */
    private Integer parseDurationSeconds(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.matches("\\d+")) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        Matcher iso = Pattern.compile("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$",
                Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (!iso.find()) return null;
        int seconds = 0;
        seconds += Optional.ofNullable(iso.group(1)).map(Integer::parseInt).orElse(0) * 86_400;
        seconds += Optional.ofNullable(iso.group(2)).map(Integer::parseInt).orElse(0) * 3_600;
        seconds += Optional.ofNullable(iso.group(3)).map(Integer::parseInt).orElse(0) * 60;
        seconds += Optional.ofNullable(iso.group(4)).map(Integer::parseInt).orElse(0);
        return seconds > 0 ? seconds : null;
    }
}
