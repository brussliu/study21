package com.study21.user.classroom;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ClassroomSttHttpClient} の応答パース（セグメント抽出・話者・タイムスタンプ）のテスト。
 */
class ClassroomSttHttpClientTest {

    private final ClassroomSttHttpClient client = new ClassroomSttHttpClient(10);

    @Test
    void parsesPlainText() {
        List<ClassroomSttClient.Segment> segments = client.parseSegments("{\"text\":\"こんにちは\"}");
        assertEquals(1, segments.size());
        assertEquals("こんにちは", segments.get(0).text());
    }

    @Test
    void parsesSegmentsArray() {
        List<ClassroomSttClient.Segment> segments = client.parseSegments(
                "{\"segments\":[{\"start\":0.0,\"end\":2.0,\"text\":\"おはよう\",\"speaker\":\"講義\",\"language\":\"ja\"}]}");
        assertEquals(1, segments.size());
        assertEquals("おはよう", segments.get(0).text());
        assertEquals("講義", segments.get(0).speaker());
        assertEquals("ja", segments.get(0).language());
    }

    @Test
    void parsesEmptyResponse() {
        assertEquals(0, client.parseSegments("{}").size());
        assertEquals(0, client.parseSegments("").size());
    }
}
