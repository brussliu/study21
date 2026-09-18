package com.study21.admin.classroomai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * E2E・検証用のスタブ（`study21.classroom-ai.stub=true` のときだけ使う）。
 *
 * <p>外部ネットワークに依存せずに「AI が授業ノートを生成した」ことにして、batC61/batC62 →
 * 画面のポーリングまでを通しで確かめるためのもの。応答は本番と同じ OpenAI 互換の本文にして、
 * 抽出・検証の経路もそのまま通す。**本番の配備では stub を有効にしない**（既定は false）。</p>
 */
@Component
@ConditionalOnProperty(name = "study21.classroom-ai.stub", havingValue = "true")
public class ClassroomAiStubClient implements ClassroomAiClient {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiStubClient.class);

    /** 固定の応答（テーマ／学習内容／先生の重点／宿題 の 4 キー）。 */
    private static final String CONTENT = """
            {"テーマ":"比例のグラフ","学習内容":"比例定数と直線の傾きの関係","先生の重点":"比例定数が正のときは右上がりになること","宿題":"教科書の練習問題"}
            """;

    @Override
    public AiResponse call(AiRequest request) {
        log.info("classroom ai stub call. provider={} model={} promptLength={}",
                request.provider(), request.model(),
                request.userPrompt() == null ? 0 : request.userPrompt().length());
        StringBuilder body = new StringBuilder("{\"choices\":[{\"message\":{\"content\":");
        body.append(quote(CONTENT.trim()));
        body.append("}}],\"usage\":{\"prompt_tokens\":600,\"completion_tokens\":80,\"total_tokens\":680}}");
        return AiResponse.success(200, body.toString());
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.append('"').toString();
    }
}
