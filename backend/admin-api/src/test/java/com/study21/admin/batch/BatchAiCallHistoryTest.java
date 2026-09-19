package com.study21.admin.batch;

import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI呼出履歴（BAT_AI呼出履歴情報）の照会。
 *
 * <p>2.0 の履歴管理画面（history.jsp）の「AI呼出履歴」タブに当たる。
 * 一覧では本文（プロンプト・レスポンス）を返さず、詳細でだけ返すのが要点。</p>
 */
class BatchAiCallHistoryTest {

    private BatchExecutionMapper executionMapper;
    private AiCallLogMapper aiCallLogMapper;
    private BatchServiceImpl service;

    @BeforeEach
    void setUp() {
        aiCallLogMapper = mock(AiCallLogMapper.class);
        executionMapper = mock(BatchExecutionMapper.class);
        service = new BatchServiceImpl(mock(BatchTaskRegistry.class), mock(SettingsService.class),
                executionMapper, mock(BatchControlMapper.class), aiCallLogMapper, List.of(), List.of());
    }

    private AiCallLogEntity entity(long callId) {
        AiCallLogEntity entity = new AiCallLogEntity();
        entity.setCallId(callId);
        entity.setExecutionId(1234L);
        entity.setBatchCode("batL03");
        entity.setProcessKey("SNAP-0001");
        entity.setLanguage("ja");
        entity.setAiType("qwen");
        entity.setModelName("qwen3-vl-flash");
        entity.setCallUrl("https://example.test/v1/chat/completions");
        entity.setHttpStatus(200);
        entity.setStartTime(Timestamp.valueOf("2026-09-12 10:00:00"));
        entity.setEndTime(Timestamp.valueOf("2026-09-12 10:00:02"));
        entity.setDurationMs(2000);
        entity.setResult("SUCCESS");
        entity.setInputTokens(1000);
        entity.setOutputTokens(200);
        entity.setTotalTokens(1200);
        return entity;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsPagedRowsWithoutBodies() {
        when(aiCallLogMapper.countCalls(any(), any(), any(), any(), any(), any())).thenReturn(66_800L);
        when(aiCallLogMapper.searchCalls(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(1L)));

        Map<String, Object> result = service.aiCalls(null, null, null, null, null, null, 1, 20);

        assertThat(result.get("totalElements")).isEqualTo(66_800L);
        assertThat(result.get("totalPages")).isEqualTo(3340);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0))
                .containsEntry("callId", 1L)
                .containsEntry("batchCode", "batL03")
                .containsEntry("aiType", "qwen")
                .containsEntry("result", "SUCCESS")
                .containsEntry("resultLabel", "成功")
                .containsEntry("totalTokens", 1200)
                // 一覧では本文を返さない（1 行が 200KB 超になるため）
                .doesNotContainKeys("prompt", "response");
    }

    @Test
    void listPassesFiltersToTheMapper() {
        when(aiCallLogMapper.countCalls(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(aiCallLogMapper.searchCalls(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.aiCalls("batL03", "qwen", "failure", "snapshot",
                "2026-09-01T00:00", "2026-09-12T23:59", 2, 50);

        // 結果は大文字化して渡す（画面は failure のような小文字も送れる）
        verify(aiCallLogMapper).countCalls(eq("batL03"), eq("qwen"), eq("FAILURE"), eq("snapshot"),
                eq("2026-09-01T00:00"), eq("2026-09-12T23:59"));
        // 2 ページ目は offset = size * (page - 1)
        verify(aiCallLogMapper).searchCalls(eq("batL03"), eq("qwen"), eq("FAILURE"), eq("snapshot"),
                eq("2026-09-01T00:00"), eq("2026-09-12T23:59"), eq(50), eq(50));
    }

    @Test
    void listRejectsAnUnknownResultCode() {
        assertThatThrownBy(() -> service.aiCalls(null, null, "成功", null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SUCCESS / FAILURE");
    }

    @Test
    void listClampsSizeAndPage() {
        when(aiCallLogMapper.countCalls(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(aiCallLogMapper.searchCalls(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        Map<String, Object> result = service.aiCalls(null, null, null, null, null, null, -5, 1000);

        assertThat(result.get("page")).isEqualTo(1);
        assertThat(result.get("size")).isEqualTo(100);
        verify(aiCallLogMapper).searchCalls(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(100), eq(0));
    }

    @Test
    void detailReturnsPromptAndResponse() {
        AiCallLogEntity entity = entity(9L);
        entity.setPrompt("単語の意味を教えてください");
        entity.setResponse("{\"translation\":\"...\"}");
        when(aiCallLogMapper.findById(9L)).thenReturn(entity);

        Map<String, Object> detail = service.aiCallDetail(9L);

        assertThat(detail)
                .containsEntry("callId", 9L)
                .containsEntry("prompt", "単語の意味を教えてください")
                .containsEntry("response", "{\"translation\":\"...\"}")
                .containsEntry("bodyTruncated", false);
    }

    @Test
    void detailTruncatesHugeBodies() {
        AiCallLogEntity entity = entity(10L);
        entity.setResponse("x".repeat(250_000));
        when(aiCallLogMapper.findById(10L)).thenReturn(entity);

        Map<String, Object> detail = service.aiCallDetail(10L);

        assertThat((String) detail.get("response")).hasSize(200_000);
        assertThat(detail).containsEntry("bodyTruncated", true).containsEntry("bodyLimit", 200_000);
    }

    @Test
    void detailRejectsAnUnknownId() {
        when(aiCallLogMapper.findById(any(Long.class))).thenReturn(null);

        assertThatThrownBy(() -> service.aiCallDetail(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("AI呼出履歴");
    }

    @Test
    void filtersReturnValuesFromRealData() {
        when(aiCallLogMapper.findDistinctAiTypes()).thenReturn(List.of("bigmodel", "deepseek", "qwen"));
        when(aiCallLogMapper.findDistinctBatchCodes()).thenReturn(List.of("batC04", "batL03"));
        when(aiCallLogMapper.findDistinctModels()).thenReturn(List.of("qwen3-vl-flash"));

        Map<String, Object> filters = service.aiCallFilters();

        assertThat(filters.get("aiTypes")).isEqualTo(List.of("bigmodel", "deepseek", "qwen"));
        assertThat(filters.get("batchCodes")).isEqualTo(List.of("batC04", "batL03"));
        assertThat(filters.get("models")).isEqualTo(List.of("qwen3-vl-flash"));
        assertThat(filters.get("results")).isEqualTo(List.of("SUCCESS", "FAILURE"));
    }

    @Test
    void noBodiesAreReadWhenListing() {
        when(aiCallLogMapper.countCalls(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(aiCallLogMapper.searchCalls(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(1L)));

        service.aiCalls(null, null, null, null, null, null, 1, 20);

        // findById（本文を読む）は一覧では呼ばない
        verify(aiCallLogMapper, org.mockito.Mockito.never()).findById(1L);
    }

    @Test
    void keywordSearchIsNotAppliedWhenBlank() {
        when(aiCallLogMapper.countCalls(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(aiCallLogMapper.searchCalls(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.aiCalls("  ", " ", "", "   ", " ", "", 1, 20);

        // 空白だけの条件は「指定なし」（null）として扱う。countCalls は 1 回だけ呼ぶ
        verify(aiCallLogMapper).countCalls(isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }
}
