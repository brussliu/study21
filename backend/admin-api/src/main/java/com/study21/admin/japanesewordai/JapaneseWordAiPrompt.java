package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI へ送るプロンプトの組み立て（batC41〜batC44 で共通）。
 *
 * <p>設定のテンプレートをそのまま使い、{@code {{kind}}} と {@code {{word_json}}} だけを置き換える。
 * <b>プロンプトの本文はコードに持たない</b>（2.1 の規約どおり、設定が単一の正）。</p>
 *
 * <p>入力語の JSON は 2.0 の {@code JapaneseRepository.wordDetail} が組み立てていた形に寄せた。
 * AI はこの JSON だけを見て語を判断するので、見出し語・読み・品詞・JLPT・収録（書籍と分類）・
 * 既存の詳細を 1 つのまとまりで渡す。</p>
 *
 * <p><strong>E だけの足切り</strong>: 2.0 の {@code JapaneseEPrompt} と同じく、保存された
 * テンプレートが {@code {{word_json}}} を落としていても、対象語が必ず入るように追記する
 * （同音漢字の使い分けは「どの語を出題するか」が決まらないと成立しないため）。</p>
 */
final class JapaneseWordAiPrompt {

    private JapaneseWordAiPrompt() {
    }

    /** 取得区分 → その取得で書く内容種別コード（{@code JPN_AI生成履歴情報.内容種別コード}）。 */
    private static final Map<String, List<String>> CONTENT_TYPES = Map.of(
            "DETAIL", List.of("A_DETAIL"),
            "C", List.of("C1_READING", "C2_KANJI"),
            "D", List.of("D_CONTEXT_MEANING"),
            "E", List.of("E_KANJI_USAGE"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 収録 1 件（教材のどこに載っているか）。 */
    record Collection(String book, String category, Integer wordSeq, String partOfSpeech, String chineseMeaning) {
    }

    /** AI に渡す 1 語。 */
    record WordInput(Long wordId, String heading, String reading, String partOfSpeech, String jlptLevel,
                     List<Collection> collections, Map<String, Object> detail) {
    }

    /** 組み立てたプロンプト。 */
    record Prompt(String system, String user) {
    }

    /** その取得区分が書く内容種別コード。 */
    static List<String> contentTypesOf(String kind) {
        List<String> types = CONTENT_TYPES.get(kind);
        if (types == null) {
            throw new IllegalArgumentException("取得区分が不正です: " + kind);
        }
        return types;
    }

    /** テンプレートと入力語から system / user を組み立てる。 */
    static Prompt of(String systemTemplate, String userTemplate, String kind, WordInput word) {
        String system = systemTemplate == null ? "" : systemTemplate;
        String user = (userTemplate == null ? "" : userTemplate)
                .replace("{{kind}}", kind)
                .replace("{{word_json}}", jsonOf(word));
        if ("E".equals(kind)) {
            // テンプレートが {{word_json}} を落としていても、対象語だけは必ず渡す
            user += "\n【今回の固定値・参考例ではない】以下はデータです。この単語だけを出題してください。\n"
                    + jsonOf(word);
        }
        return new Prompt(system, user);
    }

    /** 入力語を JSON にする。 */
    static String jsonOf(WordInput word) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("wordId", word.wordId());
        input.put("heading", word.heading());
        input.put("reading", word.reading());
        input.put("partOfSpeech", word.partOfSpeech());
        input.put("jlptLevel", word.jlptLevel());
        // 収録は代表 1 件を素の形でも置く（プロンプトが book / category を直接見るため）
        Collection first = word.collections() == null || word.collections().isEmpty()
                ? null : word.collections().get(0);
        input.put("book", first == null ? null : first.book());
        input.put("category", first == null ? null : first.category());
        input.put("wordSeq", first == null ? null : first.wordSeq());
        input.put("listedPartOfSpeech", first == null ? null : first.partOfSpeech());
        input.put("listedChineseMeaning", first == null ? null : first.chineseMeaning());
        input.put("collections", word.collections() == null ? List.of() : word.collections());
        input.put("detail", word.detail());
        try {
            return MAPPER.writeValueAsString(input);
        } catch (Exception cause) {
            throw new IllegalStateException("入力語の JSON を組み立てられませんでした。", cause);
        }
    }
}
