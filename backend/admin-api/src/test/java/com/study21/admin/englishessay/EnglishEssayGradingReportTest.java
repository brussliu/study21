package com.study21.admin.englishessay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI の添削応答（設定のプロンプトが定める形）を、<b>2.0 と同一のレポート形</b>へ変換し検証する。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>保存する {@code 添削結果JSON} の形が 2.0 と同じ（画面がそのまま描ける）</li>
 *   <li>配点は級で決まる（GRADE1 = 各 8／PRE1・GRADE2 = 各 4）</li>
 *   <li>不変条件: 見出し（題）・4 観点・tags 3 件・改善例 20 語以上</li>
 *   <li>壊れた応答は「不正」として理由を返す（作り手が再試行を判断できる）</li>
 * </ol>
 */
class EnglishEssayGradingReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI の応答（設定のプロンプトが求める形）を組み立てる。 */
    private static Map<String, Object> response() {
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("content", rubricItem(3, "設問に答えている。"));
        rubric.put("organization", rubricItem(3, "段落の流れが良い。"));
        rubric.put("vocabulary", rubricItem(2, "平易な語が多い。"));
        rubric.put("grammar", rubricItem(3, "概ね正確。"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("level", "PRE1");
        response.put("titleJa", "読書と動画の学習効果");
        response.put("titleZh", "读书与视频的学习效果");
        response.put("wordCount", 130);
        Map<String, Object> wordRequirement = new LinkedHashMap<>();
        wordRequirement.put("type", "range");
        wordRequirement.put("minimum", 120);
        wordRequirement.put("maximum", 150);
        wordRequirement.put("target", null);
        wordRequirement.put("sourceText", "Write 120-150 words.");
        wordRequirement.put("compliant", true);
        wordRequirement.put("commentJa", "条件を満たしています。");
        wordRequirement.put("commentZh", "符合条件。");
        response.put("wordRequirement", wordRequirement);
        response.put("taskRequirements", Map.of(
                "requiredReasonCount", 2, "requiredPointCount", 2,
                "requiredPoints", List.of("賛成か反対かを示す"),
                "otherRequirements", List.of("結論を述べる"),
                "compliant", true, "commentJa", "", "commentZh", ""));
        response.put("rubric", rubric);
        response.put("totalScore", 11);
        response.put("maxScore", 16);
        response.put("corrections", List.of(
                Map.of("original", "I think", "corrected", "I believe",
                        "categoryJa", "表現", "categoryZh", "表达",
                        "reasonJa", "繰り返しを避ける。", "reasonZh", "避免重复。"),
                Map.of("original", "", "corrected", "壊れた行",
                        "categoryJa", "文法", "categoryZh", "语法",
                        "reasonJa", "原文が空。", "reasonZh", "原文为空。")));
        Map<String, Object> feedbackJa = new LinkedHashMap<>();
        feedbackJa.put("summary", "主張は伝わります。理由をもう 1 つ足すと説得力が増します。");
        feedbackJa.put("strengths", List.of("設問適合", "理由の提示", "つながり", "余分な 4 つ目"));
        feedbackJa.put("improvements", List.of("語彙を増やす"));
        feedbackJa.put("nextAdvice", "次は理由を 2 つ書きましょう。");
        Map<String, Object> feedbackZh = new LinkedHashMap<>();
        feedbackZh.put("summary", "主张明确。再补一个理由会更有说服力。");
        feedbackZh.put("strengths", List.of("切题", "理由陈述", "衔接"));
        feedbackZh.put("improvements", List.of("增加词汇"));
        feedbackZh.put("nextAdvice", "下次写两个理由。");
        response.put("feedbackJa", feedbackJa);
        response.put("feedbackZh", feedbackZh);
        response.put("modelAnswer", "I believe that reading books is better for learning because "
                + "we can think deeply and concentrate on the content for a long time. "
                + "In conclusion, reading is useful for our study.");
        response.put("modelAnswerWordCount", 30);
        response.put("warnings", List.of("語数が目安より少なめです。"));
        response.put("disclaimerJa", "本結果は公式採点ではありません。");
        response.put("disclaimerZh", "本结果并非官方评分。");
        return response;
    }

    private static Map<String, Object> rubricItem(int score, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score", score);
        item.put("maxScore", 4);
        item.put("reasonJa", reason);
        item.put("reasonZh", "中文理由");
        return item;
    }

    private static String json(Map<String, Object> value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private static JsonNode report(EnglishEssayGradingReport.Result result) {
        try {
            return MAPPER.readTree(result.reportJson());
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    @Test
    @DisplayName("2.0 と同一の形へ変換する（画面がそのまま描ける）")
    void buildsTwoPointZeroShape() {
        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(response()), EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isTrue();
        assertThat(result.score()).isEqualTo(11);
        assertThat(result.maxScore()).isEqualTo(16);

        JsonNode json = report(result);
        assertThat(json.path("status").asText()).isEqualTo("AI_GRADED");
        assertThat(json.path("version").asText()).isEqualTo("eiken-ai-v1");
        assertThat(json.path("level").asText()).isEqualTo("PRE1");
        assertThat(json.path("titleJa").asText()).isEqualTo("読書と動画の学習効果");
        assertThat(json.path("titleZh").asText()).isEqualTo("读书与视频的学习效果");
        assertThat(json.path("wordCount").asInt()).isEqualTo(130);
        assertThat(json.path("score").asInt()).isEqualTo(11);
        assertThat(json.path("maxScore").asInt()).isEqualTo(16);
        assertThat(json.path("rubricMax").asInt()).isEqualTo(4);
        assertThat(json.path("modelAnswer").asText()).contains("I believe that reading books");
        assertThat(json.path("wordRequirement").asText()).isEqualTo("120〜150語");
        assertThat(json.path("createdAt").asText()).isNotBlank();
        assertThat(json.path("warnings").size()).isEqualTo(1);

        JsonNode japanese = json.path("japanese");
        assertThat(japanese.path("summary").asText()).contains("主張は伝わります");
        assertThat(japanese.path("title").asText()).isEqualTo("主張は伝わります");
        assertThat(japanese.path("tags").size()).isEqualTo(3);
        assertThat(japanese.path("tags").get(0).asText()).isEqualTo("設問適合");
        assertThat(japanese.path("rubric").size()).isEqualTo(4);
        assertThat(japanese.path("rubric").get(0).path("key").asText()).isEqualTo("内容");
        assertThat(japanese.path("rubric").get(0).path("score").asInt()).isEqualTo(3);
        assertThat(japanese.path("rubric").get(0).path("maxScore").asInt()).isEqualTo(4);
        assertThat(japanese.path("rubric").get(0).path("note").asText()).isEqualTo("設問に答えている。");
        assertThat(japanese.path("rubric").get(1).path("key").asText()).isEqualTo("構成");
        assertThat(japanese.path("rubric").get(2).path("key").asText()).isEqualTo("語彙");
        assertThat(japanese.path("rubric").get(3).path("key").asText()).isEqualTo("文法");
        assertThat(japanese.path("corrections").size()).isEqualTo(1);
        assertThat(japanese.path("corrections").get(0).path("original").asText()).isEqualTo("I think");
        assertThat(japanese.path("corrections").get(0).path("corrected").asText()).isEqualTo("I believe");
        assertThat(japanese.path("corrections").get(0).path("category").asText()).isEqualTo("表現");
        assertThat(japanese.path("corrections").get(0).path("reason").asText()).isEqualTo("繰り返しを避ける。");
        assertThat(japanese.path("advice").asText()).isEqualTo("次は理由を 2 つ書きましょう。");
        assertThat(japanese.path("notice").asText()).isEqualTo("本結果は公式採点ではありません。");

        JsonNode chinese = json.path("chinese");
        assertThat(chinese.path("tags").size()).isEqualTo(3);
        assertThat(chinese.path("rubric").size()).isEqualTo(4);
        assertThat(chinese.path("rubric").get(0).path("note").asText()).isEqualTo("中文理由");
        assertThat(chinese.path("corrections").get(0).path("category").asText()).isEqualTo("表达");
        assertThat(chinese.path("corrections").get(0).path("reason").asText()).isEqualTo("避免重复。");
        assertThat(chinese.path("advice").asText()).isEqualTo("下次写两个理由。");
        assertThat(chinese.path("notice").asText()).isEqualTo("本结果并非官方评分。");

        JsonNode taskRequirements = json.path("taskRequirements");
        assertThat(taskRequirements.size()).isEqualTo(4);
        assertThat(taskRequirements.get(0).asText()).isEqualTo("賛成か反対かを示す");
        assertThat(taskRequirements.get(1).asText()).contains("理由を 2 つ");
        assertThat(taskRequirements.get(2).asText()).contains("POINTS を 2 つ");
        assertThat(taskRequirements.get(3).asText()).isEqualTo("結論を述べる");
    }

    @Test
    @DisplayName("GRADE1 は各観点 8 点・満点 32（得点は観点の合計）")
    void usesGradeOneRubric() {
        Map<String, Object> value = response();
        value.put("level", "GRADE1");
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("content", rubricItem(7, "a"));
        rubric.put("organization", rubricItem(6, "b"));
        rubric.put("vocabulary", rubricItem(5, "c"));
        rubric.put("grammar", rubricItem(8, "d"));
        value.put("rubric", rubric);

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.GRADE1, 210);

        assertThat(result.success()).isTrue();
        assertThat(result.score()).isEqualTo(26);
        assertThat(result.maxScore()).isEqualTo(32);
        JsonNode json = report(result);
        assertThat(json.path("rubricMax").asInt()).isEqualTo(8);
        assertThat(json.path("japanese").path("rubric").get(0).path("maxScore").asInt()).isEqualTo(8);
    }

    @Test
    @DisplayName("観点の点が範囲外なら 0〜満点に丸める（画面が壊れた点を出さない）")
    void clampsRubricScore() {
        Map<String, Object> value = response();
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("content", rubricItem(99, "a"));
        rubric.put("organization", rubricItem(-3, "b"));
        rubric.put("vocabulary", rubricItem(2, "c"));
        rubric.put("grammar", rubricItem(2, "d"));
        value.put("rubric", rubric);

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        assertThat(result.score()).isEqualTo(8);
        JsonNode json = report(result);
        assertThat(json.path("japanese").path("rubric").get(0).path("score").asInt()).isEqualTo(4);
        assertThat(json.path("japanese").path("rubric").get(1).path("score").asInt()).isZero();
    }

    @Test
    @DisplayName("題（見出し）が空なら不正")
    void rejectsBlankTitle() {
        Map<String, Object> value = response();
        value.put("titleJa", "  ");

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("見出し");
    }

    @Test
    @DisplayName("4 観点が揃っていなければ不正")
    void rejectsMissingRubric() {
        Map<String, Object> value = response();
        Map<String, Object> rubric = new LinkedHashMap<>();
        rubric.put("content", rubricItem(3, "a"));
        rubric.put("organization", rubricItem(3, "b"));
        value.put("rubric", rubric);

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("観点");
    }

    @Test
    @DisplayName("tags が 3 件に満たなければ不正")
    void rejectsTooFewTags() {
        Map<String, Object> value = response();
        value.put("feedbackJa", Map.of("summary", "s", "strengths", List.of("1", "2"),
                "improvements", List.of(), "nextAdvice", "a"));

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("3 件");
    }

    @Test
    @DisplayName("改善後の作文例が 20 語に満たなければ不正")
    void rejectsShortModelAnswer() {
        Map<String, Object> value = response();
        value.put("modelAnswer", "This is too short.");

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("20 語");
    }

    @Test
    @DisplayName("JSON として読めない応答は不正")
    void rejectsBrokenJson() {
        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                "not json", EnglishEssayLevel.PRE1, 130);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_RESPONSE");
    }

    @Test
    @DisplayName("字数条件は AI の抽出結果を文字列にする（無ければ級の目安）")
    void formatsWordRequirement() {
        assertThat(EnglishEssayGradingReport.wordRequirementOf(
                Map.of("type", "minimum", "minimum", 100), EnglishEssayLevel.PRE1))
                .isEqualTo("100語以上");
        assertThat(EnglishEssayGradingReport.wordRequirementOf(
                Map.of("type", "approximately", "target", 200), EnglishEssayLevel.GRADE1))
                .isEqualTo("約200語");
        assertThat(EnglishEssayGradingReport.wordRequirementOf(
                Map.of("type", "not_specified"), EnglishEssayLevel.PRE1))
                .isEqualTo("指定なし");
        assertThat(EnglishEssayGradingReport.wordRequirementOf(null, EnglishEssayLevel.GRADE2))
                .isEqualTo("80〜100語");
    }

    @Test
    @DisplayName("修正候補は原文と修正後が揃った行だけ残す")
    void dropsBrokenCorrections() {
        Map<String, Object> value = response();
        List<Object> corrections = new ArrayList<>();
        corrections.add(Map.of("original", "a lot of", "corrected", "numerous",
                "categoryJa", "語彙", "categoryZh", "词汇", "reasonJa", "書き言葉。", "reasonZh", "书面语。"));
        corrections.add(Map.of("original", "x", "corrected", "", "categoryJa", "文法", "categoryZh", "语法",
                "reasonJa", "修正後が空。", "reasonZh", "修正后为空。"));
        value.put("corrections", corrections);

        EnglishEssayGradingReport.Result result = EnglishEssayGradingReport.build(
                json(value), EnglishEssayLevel.PRE1, 130);

        JsonNode json = report(result);
        assertThat(json.path("japanese").path("corrections").size()).isEqualTo(1);
        assertThat(json.path("japanese").path("corrections").get(0).path("original").asText())
                .isEqualTo("a lot of");
    }
}
