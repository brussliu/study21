package com.study21.admin.englishessay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI の添削応答を <b>2.0 と同一のレポート形</b>（{@code 添削結果JSON}）へ変換し、検証する。
 *
 * <p>画面（{@code EssayReportPanel}）はこの形をそのまま描く。設定のプロンプトが求める
 * AI の応答は「4 観点オブジェクト + 日中の feedback + corrections + modelAnswer」なので、
 * ここで画面の形（{@code japanese} / {@code chinese} の 2 言語 + 総合得点）へ寄せる。</p>
 *
 * <p><b>不変条件</b>（満たさなければ「不正」を返し、呼び出し側が再試行する）:</p>
 * <ol>
 *   <li>見出し（{@code titleJa} / {@code titleZh}）が空でない</li>
 *   <li>4 観点（content / organization / vocabulary / grammar）が揃い、点数が数値</li>
 *   <li>tags（強み）が日本語・中国語とも 3 件以上</li>
 *   <li>改善後の作文例が 20 語以上</li>
 * </ol>
 *
 * <p>点は級の満点へ丸める（AI が範囲外を返しても画面に壊れた点を出さない）。
 * 合計は<b>観点の合計</b>にする（AI の {@code totalScore} と食い違っても、画面の内訳と一致させる）。</p>
 */
final class EnglishEssayGradingReport {

    static final String STATUS = "AI_GRADED";
    static final String VERSION = "eiken-ai-v1";

    /** 観点の並び（画面の 4 観点。AI の英語キー → 日本語／中国語の見出し）。 */
    private static final List<String> RUBRIC_KEYS_EN =
            List.of("content", "organization", "vocabulary", "grammar");
    private static final List<String> RUBRIC_KEYS_JA = List.of("内容", "構成", "語彙", "文法");
    private static final List<String> RUBRIC_KEYS_ZH = List.of("内容", "结构", "词汇", "语法");

    /** tags の必要件数（画面は 3 件を並べる）。 */
    private static final int MIN_TAGS = 3;

    /** 改善後の作文例の最小語数。 */
    private static final int MIN_MODEL_ANSWER_WORDS = 20;

    /** 見出し（1 文）の上限文字数。 */
    private static final int HEADLINE_MAX = 60;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EnglishEssayGradingReport() {
    }

    /**
     * 変換の結果。
     *
     * @param success      true なら {@code reportJson} をそのまま保存してよい
     * @param reportJson   2.0 と同一の形の JSON
     * @param score        総合得点（観点の合計）
     * @param maxScore     満点（GRADE1 = 32／PRE1・GRADE2 = 16）
     */
    record Result(boolean success, String errorCode, String errorMessage,
                  String reportJson, int score, int maxScore) {

        static Result failure(String errorCode, String errorMessage) {
            return new Result(false, errorCode, errorMessage, null, 0, 0);
        }
    }

    /**
     * AI の応答を検証してレポートへ変換する。
     *
     * @param rawContent AI の応答（封筒を剥がした中身。JSON 文字列）
     * @param level      そのときの級（履歴の写し。AI の {@code level} は信用しない）
     * @param wordCount  システムが数えた語数（履歴の写し）
     */
    static Result build(String rawContent, EnglishEssayLevel level, int wordCount) {
        if (rawContent == null || rawContent.isBlank()) {
            return Result.failure("EMPTY_RESPONSE", "AI の応答が空です。");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(rawContent);
        } catch (Exception cause) {
            return Result.failure("INVALID_RESPONSE", "AI の応答が JSON ではありません。");
        }
        if (!root.isObject()) {
            return Result.failure("INVALID_RESPONSE", "AI の応答が JSON オブジェクトではありません。");
        }

        String titleJa = textOf(root.path("titleJa"));
        String titleZh = textOf(root.path("titleZh"));
        if (titleJa.isEmpty() || titleZh.isEmpty()) {
            return Result.failure("INVALID_RESPONSE",
                    "AI の応答に見出し（titleJa / titleZh）がありません（作文の題を必ず生成させてください）。");
        }

        JsonNode rubric = root.path("rubric");
        if (!rubric.isObject()) {
            return Result.failure("INVALID_RESPONSE", "AI の応答に 4 観点（rubric）がありません。");
        }
        int rubricMax = level.rubricMax();
        int score = 0;
        List<Map<String, Object>> rubricJa = new ArrayList<>();
        List<Map<String, Object>> rubricZh = new ArrayList<>();
        for (int index = 0; index < RUBRIC_KEYS_EN.size(); index += 1) {
            String key = RUBRIC_KEYS_EN.get(index);
            JsonNode item = rubric.path(key);
            if (!item.isObject()) {
                return Result.failure("INVALID_RESPONSE",
                        "AI の応答に観点「" + RUBRIC_KEYS_JA.get(index) + "」がありません。");
            }
            JsonNode rawScore = item.path("score");
            if (!rawScore.isNumber()) {
                return Result.failure("INVALID_RESPONSE",
                        "AI の応答の観点「" + RUBRIC_KEYS_JA.get(index) + "」の点数が数値ではありません。");
            }
            int value = clamp(rawScore.asInt(), 0, rubricMax);
            score += value;
            rubricJa.add(rubricItem(RUBRIC_KEYS_JA.get(index), value, rubricMax, textOf(item.path("reasonJa"))));
            rubricZh.add(rubricItem(RUBRIC_KEYS_ZH.get(index), value, rubricMax, textOf(item.path("reasonZh"))));
        }

        JsonNode feedbackJa = root.path("feedbackJa");
        JsonNode feedbackZh = root.path("feedbackZh");
        List<String> tagsJa = stringsOf(feedbackJa.path("strengths"));
        List<String> tagsZh = stringsOf(feedbackZh.path("strengths"));
        if (tagsJa.size() < MIN_TAGS || tagsZh.size() < MIN_TAGS) {
            return Result.failure("INVALID_RESPONSE",
                    "AI の応答の tags（強み）が 3 件に足りません（日本語 " + tagsJa.size()
                            + " 件 / 中国語 " + tagsZh.size() + " 件）。");
        }

        String modelAnswer = textOf(root.path("modelAnswer"));
        if (wordCountOf(modelAnswer) < MIN_MODEL_ANSWER_WORDS) {
            return Result.failure("INVALID_RESPONSE",
                    "AI の応答の改善後の作文例が 20 語に足りません（" + wordCountOf(modelAnswer) + " 語）。");
        }

        List<Map<String, Object>> correctionsJa = new ArrayList<>();
        List<Map<String, Object>> correctionsZh = new ArrayList<>();
        JsonNode corrections = root.path("corrections");
        if (corrections.isArray()) {
            for (JsonNode correction : corrections) {
                String original = textOf(correction.path("original"));
                String corrected = textOf(correction.path("corrected"));
                // 原文と修正後が揃った行だけ残す（片方だけの行は画面に意味が無い）
                if (original.isEmpty() || corrected.isEmpty()) {
                    continue;
                }
                correctionsJa.add(correctionOf(original, corrected,
                        textOf(correction.path("categoryJa")), textOf(correction.path("reasonJa"))));
                correctionsZh.add(correctionOf(original, corrected,
                        textOf(correction.path("categoryZh")), textOf(correction.path("reasonZh"))));
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", STATUS);
        report.put("version", VERSION);
        report.put("level", level.name());
        report.put("titleJa", titleJa);
        report.put("titleZh", titleZh);
        report.put("wordCount", wordCount);
        report.put("score", score);
        report.put("maxScore", level.maxScore());
        report.put("rubricMax", rubricMax);
        report.put("modelAnswer", modelAnswer);
        report.put("wordRequirement", wordRequirementOf(
                mapOf(root.path("wordRequirement")), level));
        report.put("taskRequirements", taskRequirementsOf(root.path("taskRequirements")));
        report.put("warnings", stringsOf(root.path("warnings")));
        report.put("createdAt", Instant.now().toString());
        report.put("japanese", sideOf(
                headlineOf(feedbackJa.path("headline"), feedbackJa.path("summary"), titleJa),
                textOf(feedbackJa.path("summary")),
                tagsJa.stream().limit(MIN_TAGS).toList(),
                rubricJa, correctionsJa,
                textOf(feedbackJa.path("nextAdvice")),
                textOf(root.path("disclaimerJa"))));
        report.put("chinese", sideOf(
                headlineOf(feedbackZh.path("headline"), feedbackZh.path("summary"), titleZh),
                textOf(feedbackZh.path("summary")),
                tagsZh.stream().limit(MIN_TAGS).toList(),
                rubricZh, correctionsZh,
                textOf(feedbackZh.path("nextAdvice")),
                textOf(root.path("disclaimerZh"))));

        try {
            return new Result(true, null, null, MAPPER.writeValueAsString(report), score, level.maxScore());
        } catch (Exception cause) {
            return Result.failure("INTERNAL_ERROR", "添削結果の組み立てに失敗しました。");
        }
    }

    /**
     * 字数条件を画面の 1 行（{@code 120〜150語} など）にする。
     *
     * <p>設問文から AI が読んだ条件が正（級の目安で固定しない）。読めなかった
     * （{@code not_specified} / {@code unclear}）ときはその旨を出し、条件そのものが
     * 無いときだけ級の目安に落とす。</p>
     */
    static String wordRequirementOf(Map<String, ?> requirement, EnglishEssayLevel level) {
        if (requirement == null) {
            return level.wordRequirement();
        }
        String type = stringOf(requirement.get("type"));
        Integer minimum = intOf(requirement.get("minimum"));
        Integer maximum = intOf(requirement.get("maximum"));
        Integer target = intOf(requirement.get("target"));
        return switch (type) {
            case "range" -> minimum != null && maximum != null
                    ? minimum + "〜" + maximum + "語" : level.wordRequirement();
            case "minimum" -> minimum != null ? minimum + "語以上" : level.wordRequirement();
            case "maximum" -> maximum != null ? maximum + "語以内" : level.wordRequirement();
            case "approximately" -> target != null ? "約" + target + "語" : level.wordRequirement();
            case "not_specified" -> "指定なし";
            case "unclear" -> "不明";
            default -> level.wordRequirement();
        };
    }

    /** 英単語の数（2.0 と同じ数え方＝空白区切り）。 */
    static int wordCountOf(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    /** 設問が求めていること（採点の観点として画面に出す）。 */
    private static List<String> taskRequirementsOf(JsonNode node) {
        List<String> requirements = new ArrayList<>();
        if (node.isObject()) {
            for (String point : stringsOf(node.path("requiredPoints"))) {
                requirements.add(point);
            }
            if (node.path("requiredReasonCount").isNumber()) {
                requirements.add("理由を " + node.path("requiredReasonCount").asInt() + " つ以上述べる");
            }
            if (node.path("requiredPointCount").isNumber()) {
                requirements.add("POINTS を " + node.path("requiredPointCount").asInt() + " つ扱う");
            }
            for (String other : stringsOf(node.path("otherRequirements"))) {
                requirements.add(other);
            }
        }
        return requirements;
    }

    /** レポートの片側（日本語／中国語で同じ形）。 */
    private static Map<String, Object> sideOf(String title, String summary, List<String> tags,
                                             List<Map<String, Object>> rubric,
                                             List<Map<String, Object>> corrections,
                                             String advice, String notice) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("title", title);
        side.put("summary", summary);
        side.put("tags", tags);
        side.put("rubric", rubric);
        side.put("corrections", corrections);
        side.put("advice", advice);
        side.put("notice", notice);
        return side;
    }

    private static Map<String, Object> rubricItem(String key, int score, int maxScore, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("score", score);
        item.put("maxScore", maxScore);
        item.put("note", note);
        return item;
    }

    private static Map<String, Object> correctionOf(String original, String corrected,
                                                    String category, String reason) {
        Map<String, Object> correction = new LinkedHashMap<>();
        correction.put("original", original);
        correction.put("corrected", corrected);
        correction.put("category", category);
        correction.put("reason", reason);
        return correction;
    }

    /**
     * レポートの見出し（1 文）。
     *
     * <p>設定のプロンプトは見出しそのものを求めていないので、AI が {@code headline} を返せば
     * それを使い、無ければ総評の<b>最初の 1 文</b>を借りる（画面が空の見出しを出さないように）。
     * どちらも無ければ作文の題にする。</p>
     */
    private static String headlineOf(JsonNode headline, JsonNode summary, String fallback) {
        String value = textOf(headline);
        if (!value.isEmpty()) {
            return value.length() <= HEADLINE_MAX ? value : value.substring(0, HEADLINE_MAX);
        }
        String first = firstSentenceOf(textOf(summary));
        if (!first.isEmpty()) {
            return first;
        }
        return fallback;
    }

    /** 最初の 1 文（「。」または「.」まで）。 */
    private static String firstSentenceOf(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "";
        }
        int ja = value.indexOf('。');
        int en = value.indexOf('.');
        int end;
        if (ja < 0) {
            end = en;
        } else if (en < 0) {
            end = ja;
        } else {
            end = Math.min(ja, en);
        }
        String sentence = end < 0 ? value : value.substring(0, end);
        sentence = sentence.trim();
        return sentence.length() <= HEADLINE_MAX ? sentence : sentence.substring(0, HEADLINE_MAX);
    }

    /** 文字列の配列だけを取り出す（空・空白は落とす）。 */
    private static List<String> stringsOf(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = textOf(item);
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    /** JSON のノードを素の Map にする（{@link #wordRequirementOf} が Map を取るため）。 */
    private static Map<String, Object> mapOf(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        try {
            return MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception cause) {
            return null;
        }
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && text.trim().matches("-?\\d+")) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
