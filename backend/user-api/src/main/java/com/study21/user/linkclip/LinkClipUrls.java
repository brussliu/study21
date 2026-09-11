package com.study21.user.linkclip;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * URL の正規化とソース種別の判定（2.0 の normalizeUrl / sourceKind 判定を踏襲）。
 * 正規化 URL は重複候補の検索に使うだけで、一意制約にはしない（2.0 と同じ挙動）。
 */
public final class LinkClipUrls {
    /** 追跡用パラメータ。正規化時に取り除く。 */
    private static final List<String> TRACKING_PARAMS =
            List.of("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                    "fbclid", "gclid", "yclid", "si", "feature", "spm", "ref_src");

    private LinkClipUrls() {}

    public static boolean isLocalPath(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;
        return trimmed.regionMatches(true, 0, "file:", 0, 5)
                || trimmed.matches("^[A-Za-z]:[\\\\/].+");
    }

    /** Windows のローカルパスを file: URI へ、通常の URL は追跡パラメータを除いて正規化する。 */
    public static String normalize(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) return "";
        if (raw.matches("^[A-Za-z]:[\\\\/].+")) {
            return toFileUri(raw);
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException ex) {
            return raw;
        }
        if (uri.getScheme() == null) {
            try {
                uri = new URI("https://" + raw);
            } catch (URISyntaxException ex) {
                return raw;
            }
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("file".equals(scheme)) {
            return toFileUri(uri.getPath() == null ? raw : uri.getPath());
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return raw;

        // YouTube は動画IDだけの形に統一する（youtu.be / shorts / 追加パラメータを吸収）。
        String videoId = youTubeVideoId(uri);
        if (videoId != null) return "https://www.youtube.com/watch?v=" + videoId;

        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String query = filteredQuery(uri.getRawQuery());
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        return scheme + "://" + host + port + path + (query.isEmpty() ? "" : "?" + query);
    }

    /** ソース種別（DDL の CHECK 値）を URL から判定する。 */
    public static String detectSourceCode(String url) {
        if (isLocalPath(url)) return "LOCAL_FILE";
        String host = hostOf(url);
        if (host.isEmpty()) return "OTHER";
        if (host.contains("youtube.com") || host.contains("youtu.be")) return "YOUTUBE";
        if (host.contains("github.com")) return "GITHUB";
        if (host.contains("wikipedia.org")) return "WIKIPEDIA";
        if (host.contains("news")) return "NEWS";
        return "WEB";
    }

    /** ソース種別に対応するクリップ種別。 */
    public static String defaultClipType(String sourceCode) {
        return switch (sourceCode == null ? "" : sourceCode) {
            case "YOUTUBE" -> "VIDEO";
            case "GITHUB" -> "REPOSITORY";
            case "WIKIPEDIA" -> "ARTICLE";
            case "NEWS" -> "NEWS";
            case "LOCAL_FILE" -> "FILE";
            default -> "LINK";
        };
    }

    /** 表示用のホスト名（例: platform.openai.com）。 */
    public static String hostOf(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = new URI(url.trim());
            if (uri.getHost() != null) return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ignored) {
            // 下のフォールバックを使う
        }
        return "";
    }

    /** ローカルファイルの表示名（パスの最後の要素）。 */
    public static String localFileName(String url) {
        String path = url == null ? "" : url.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String youTubeVideoId(String url) {
        if (url == null) return null;
        try {
            return youTubeVideoId(new URI(url.trim()));
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String youTubeVideoId(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (host.endsWith("youtu.be")) {
            String id = path.replaceFirst("^/", "");
            return id.isEmpty() ? null : id.split("/")[0];
        }
        if (!host.contains("youtube.com")) return null;
        if (path.startsWith("/shorts/")) {
            String id = path.substring("/shorts/".length());
            return id.isEmpty() ? null : id.split("/")[0];
        }
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            if (pair.startsWith("v=")) {
                String id = pair.substring(2);
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }

    private static String filteredQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> kept = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) continue;
            kept.add(pair);
        }
        kept.sort(Comparator.naturalOrder());
        return String.join("&", kept);
    }

    private static String toFileUri(String path) {
        String normalized = path.replace('\\', '/');
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return ("file://" + normalized).replace(" ", "%20");
    }
}
