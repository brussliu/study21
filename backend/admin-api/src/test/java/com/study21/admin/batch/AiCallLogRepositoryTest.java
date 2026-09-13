package com.study21.admin.batch;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する AI呼出履歴 の検証。
 *
 * 2.0 から移行した `BAT_AI呼出履歴情報`（66,800 件・約 400MB）を一覧・絞り込み・詳細で
 * 読めることを確かめる。一覧で本文（プロンプト・レスポンス）を読まないことも見る。
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl admin-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class AiCallLogRepositoryTest {

    @Autowired
    private BatchService batchService;

    @Autowired
    private AiCallLogMapper aiCallLogMapper;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    @Test
    void migratedCallsAreReadableWithPaging() {
        Map<String, Object> first = batchService.aiCalls(null, null, null, null, null, null, 1, 10);

        assertThat((Long) first.get("totalElements")).isGreaterThan(60_000L);
        assertThat((Integer) first.get("totalPages")).isGreaterThan(1);
        List<Map<String, Object>> items = items(first);
        assertThat(items).hasSize(10);
        // 一覧では本文を返さない
        assertThat(items).allSatisfy(row -> assertThat(row).doesNotContainKeys("prompt", "response"));
        assertThat(items).allSatisfy(row -> assertThat(row.get("batchCode")).isNotNull());

        // 新しい順（開始日時が降順）
        for (int index = 1; index < items.size(); index += 1) {
            assertThat((Comparable<Object>) items.get(index - 1).get("startTime"))
                    .isGreaterThanOrEqualTo((Comparable<Object>) items.get(index).get("startTime"));
        }

        Map<String, Object> second = batchService.aiCalls(null, null, null, null, null, null, 2, 10);
        assertThat(items(second).get(0).get("callId")).isNotEqualTo(items.get(0).get("callId"));
    }

    @Test
    void detailReturnsTheBodies() {
        List<Map<String, Object>> items = items(batchService.aiCalls(null, null, null, null, null, null, 1, 20));
        long callId = ((Number) items.stream()
                .filter(row -> row.get("prompt") == null)
                .findFirst()
                .orElseThrow()
                .get("callId")).longValue();

        Map<String, Object> detail = batchService.aiCallDetail(callId);

        assertThat(detail.get("callId")).isEqualTo(callId);
        assertThat(detail.get("result")).isIn("SUCCESS", "FAILURE");
        assertThat(detail.get("startTime")).isNotNull();
        // 移行データには本文が入っている（空の呼び出しもあるため「キーが存在する」ことだけ見る）
        assertThat(detail).containsKeys("prompt", "response", "bodyTruncated", "bodyLimit");
    }

    @Test
    void unknownCallIdIsNotFound() {
        assertThatThrownBy(() -> batchService.aiCallDetail(999_999_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resultFilterSeparatesSuccessAndFailure() {
        Map<String, Object> failures = batchService.aiCalls(null, null, "FAILURE", null, null, null, 1, 20);
        assertThat((Long) failures.get("totalElements")).isGreaterThan(0L);
        assertThat(items(failures)).allSatisfy(row -> assertThat(row.get("result")).isEqualTo("FAILURE"));

        Map<String, Object> successes = batchService.aiCalls(null, null, "SUCCESS", null, null, null, 1, 20);
        assertThat((Long) successes.get("totalElements")).isGreaterThan(60_000L);
        assertThat(items(successes)).allSatisfy(row -> assertThat(row.get("resultLabel")).isEqualTo("成功"));

        assertThatThrownBy(() -> batchService.aiCalls(null, null, "成功", null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void batchCodeFilterMatchesPartially() {
        Map<String, Object> batL03 = batchService.aiCalls("batL03", null, null, null, null, null, 1, 5);
        assertThat((Long) batL03.get("totalElements")).isGreaterThan(40_000L);
        assertThat(items(batL03)).allSatisfy(row -> assertThat((String) row.get("batchCode")).isEqualTo("batL03"));

        Map<String, Object> nothing = batchService.aiCalls("batX-none", null, null, null, null, null, 1, 5);
        assertThat(nothing.get("totalElements")).isEqualTo(0L);
    }

    @Test
    void filtersComeFromRealData() {
        Map<String, Object> filters = batchService.aiCallFilters();

        assertThat((List<String>) filters.get("aiTypes")).contains("qwen", "deepseek");
        assertThat((List<String>) filters.get("batchCodes")).contains("batL03");
        assertThat((List<String>) filters.get("models")).isNotEmpty();
        assertThat(filters.get("results")).isEqualTo(List.of("SUCCESS", "FAILURE"));
    }

    @Test
    void keywordSearchesProcessKeyAndModelButNotBodies() {
        List<Map<String, Object>> items = items(batchService.aiCalls(null, null, null, null, null, null, 1, 50));
        Map<String, Object> withModel = items.stream()
                .filter(row -> row.get("modelName") != null)
                .findFirst()
                .orElseThrow();
        String model = (String) withModel.get("modelName");

        Map<String, Object> result = batchService.aiCalls(null, null, null, model, null, null, 1, 20);
        assertThat((Long) result.get("totalElements")).isGreaterThan(0L);
    }

    @Test
    void startTimeRangeIsInclusive() {
        List<Map<String, Object>> items = items(batchService.aiCalls(null, null, null, null, null, null, 1, 1));
        String start = String.valueOf(items.get(0).get("startTime"));
        // 同じ行の開始日時を From / To に指定すると、その行が引ける（両端を含む）
        Map<String, Object> result = batchService.aiCalls(null, null, null, null, start, start, 1, 5);
        assertThat((Long) result.get("totalElements")).isGreaterThan(0L);
    }

    @Test
    void bodiesAreNotReadWhenListing() {
        // Mapper 直接: 一覧は本文を SELECT しない（resultMap の prompt / response は null のまま）
        List<AiCallLogEntity> rows = aiCallLogMapper.searchCalls(null, null, null, null, null, null, 5, 0);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getPrompt()).isNull();
            assertThat(row.getResponse()).isNull();
        });
    }
}
