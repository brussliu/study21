package com.study21.user.classroom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoogleSttResponseParser} の応答解析（**ネットワーク不要**）。
 *
 * <p>実際の Google Speech-to-Text v1 の応答形状（{@code results[].alternatives[].transcript}）を
 * 貼り付けて検証する。</p>
 */
class GoogleSttResponseParserTest {

    /** Google v1 の実応答形状（複数 results・alternatives[0].transcript）。 */
    private static final String REAL_SHAPE = """
            {
              "results": [
                {
                  "alternatives": [
                    { "transcript": "今日は比例のグラフについて学びます", "confidence": 0.98 }
                  ]
                },
                {
                  "alternatives": [
                    { "transcript": "宿題として教科書の練習問題を出します", "confidence": 0.97 }
                  ]
                }
              ]
            }
            """;

    @Test
    void joinsResultsInOrder() {
        List<ClassroomSttClient.Segment> segments = GoogleSttResponseParser.parse(REAL_SHAPE);
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text())
                .isEqualTo("今日は比例のグラフについて学びます 宿題として教科書の練習問題を出します");
    }

    @Test
    void emptyResultsReturnsEmptyList() {
        assertThat(GoogleSttResponseParser.parse("{\"results\":[]}")).isEmpty();
    }

    @Test
    void blankOrNullReturnsEmptyList() {
        assertThat(GoogleSttResponseParser.parse("")).isEmpty();
        assertThat(GoogleSttResponseParser.parse(null)).isEmpty();
    }

    @Test
    void missingTranscriptIsSkipped() {
        List<ClassroomSttClient.Segment> segments =
                GoogleSttResponseParser.parse("{\"results\":[{\"alternatives\":[{}]}]}");
        assertThat(segments).isEmpty();
    }
}
