package com.study21.admin.geometryai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * E2E・検証用のスタブ（`study21.geometry-ai.stub=true` のときだけ使う）。
 *
 * <p>外部ネットワークに依存せずに「AI が答えた」ことにして、batC52 → batC53 → 画面の
 * ポーリング → 保存までを通しで確かめるためのもの。応答は本番と同じ OpenAI 互換の本文にして、
 * 抽出・検証の経路もそのまま通す（設計 §9.4）。**本番の配備では stub を有効にしない**。</p>
 */
@Component
@ConditionalOnProperty(name = "study21.geometry-ai.stub", havingValue = "true")
public class GeometryAiStubClient implements GeometryAiClient {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiStubClient.class);

    /** 固定の応答（三角形 ABC を作るコマンド） */
    private static final String CONTENT = """
            説明: 画像から三角形 ABC を読み取りました。
            {"分類":"FIGURE","図形種":"TRIANGLE","図形名":"三角形ABC","タグ":["三角形","作図"],"メモ":"画像から作成（検証用の固定応答）","認識":"AB=5, BC=4","コマンド":["A = (0, 0)","B = (5, 0)","C = (0, 4)","Polygon(A, B, C)","Text(\\"三角形ABC\\", (2, -1))"]}
            """;

    @Override
    public AiResponse call(AiRequest request) {
        log.info("geometry ai stub call. provider={} model={} promptLength={} imageBytes={}",
                request.provider(), request.model(),
                request.userPrompt() == null ? 0 : request.userPrompt().length(),
                request.image() == null ? 0 : request.image().length);
        // OpenAI 互換の本文（content に固定の文字列を入れる）
        StringBuilder body = new StringBuilder("{\"choices\":[{\"message\":{\"content\":");
        body.append(quote(CONTENT));
        body.append("}}],\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":180,\"total_tokens\":1380}}");
        return AiResponse.success(200, body.toString());
    }

    /** JSON の文字列として安全に埋め込む（改行・引用符をエスケープする）。 */
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
