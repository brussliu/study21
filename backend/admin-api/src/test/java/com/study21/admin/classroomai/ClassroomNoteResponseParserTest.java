package com.study21.admin.classroomai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassroomNoteResponseParser} の応答 JSON 検証のテスト。
 */
class ClassroomNoteResponseParserTest {

    @Test
    void parsesValidJson() {
        ClassroomNoteResponseParser.ParseResult result = ClassroomNoteResponseParser.parse(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"テーマ\\\":\\\"比例\\\"}\"}}]}");
        assertTrue(result.isSuccess());
        assertNotNull(result.noteJson());
    }

    @Test
    void parsesFencedJson() {
        ClassroomNoteResponseParser.ParseResult result = ClassroomNoteResponseParser.parse(
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"テーマ\\\":\\\"比例\\\"}\\n```\"}}]}");
        assertTrue(result.isSuccess());
        assertNotNull(result.noteJson());
    }

    @Test
    void rejectsNonJson() {
        ClassroomNoteResponseParser.ParseResult result = ClassroomNoteResponseParser.parse(
                "{\"choices\":[{\"message\":{\"content\":\"JSON ではありません\"}}]}");
        assertFalse(result.isSuccess());
        assertEquals("INVALID_JSON", result.errorCode());
    }

    @Test
    void rejectsEmptyResponse() {
        ClassroomNoteResponseParser.ParseResult result = ClassroomNoteResponseParser.parse(
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}");
        assertFalse(result.isSuccess());
        assertEquals("EMPTY_RESPONSE", result.errorCode());
    }
}
