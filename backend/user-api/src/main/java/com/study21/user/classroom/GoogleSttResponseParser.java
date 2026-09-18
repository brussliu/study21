package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Google Cloud Speech-to-Text v1 の応答（{@code results[].alternatives[0].transcript}）を
 * 転写セグメントに変換する。
 *
 * <p>複数の {@code results} は結果順に空白 1 つで連結して 1 セグメントにする（`confidence` は
 * 使わない）。空の {@code results} は空リストを返し、呼び出し側が「聞き取れませんでした」に
 * 丸める（既存の空応答の経路と同じ）。</p>
 */
public final class GoogleSttResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoogleSttResponseParser() {
    }

    /** 応答 JSON から転写セグメント（1 件）を取り出す。無ければ空リスト。 */
    public static List<ClassroomSttClient.Segment> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }
            StringBuilder transcript = new StringBuilder();
            for (JsonNode result : results) {
                JsonNode alternative = result.path("alternatives").path(0);
                String text = alternative.path("transcript").isTextual()
                        ? alternative.path("transcript").asText().trim() : null;
                if (text != null && !text.isEmpty()) {
                    if (transcript.length() > 0) {
                        transcript.append(' ');
                    }
                    transcript.append(text);
                }
            }
            if (transcript.length() == 0) {
                return List.of();
            }
            return List.of(new ClassroomSttClient.Segment(transcript.toString(), null, null, null, null));
        } catch (IOException cause) {
            return List.of();
        }
    }
}
