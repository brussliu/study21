package com.study21.user.japanese;

import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 2.0 の一覧条件・複数作成。各行は独立したトランザクションで保存する。 */
@Service
public class JapaneseTestManagementService {
    private final JapaneseService service;
    private final JpnTestMapper mapper;

    public JapaneseTestManagementService(JapaneseService service, JpnTestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> conditions(String book) {
        return Map.of("books", mapper.books(), "categories", mapper.categories(book));
    }

    @Transactional(readOnly = true)
    public JapaneseModels.TestListResult search(long accountId, String state, String type, String book,
                                               String from, String to, int page, int size) {
        validateRange(from, to);
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(1, page);
        long total = mapper.countRange(accountId, state, type, book, from, to);
        var rows = mapper.searchRange(accountId, state, type, book, from, to, safeSize, (safePage - 1) * safeSize)
                .stream().map(JapaneseServiceImpl::toTestRow).toList();
        var t = mapper.totals(accountId);
        return new JapaneseModels.TestListResult(rows, total, safePage, safeSize,
                (int) Math.ceil((double) total / safeSize),
                new JapaneseModels.TestTotals(t.tests(), t.completed(), t.running(), t.averageScoreValue(), t.totalActive()));
    }

    public record RowResult(int rowNo, Long testId, boolean skipped, String message) { }
    public record MultipleResult(int createdCount, int skippedCount, List<RowResult> results) { }

    public MultipleResult create(UserPrincipal user, List<JapaneseModels.TestCreateRequest> rows) {
        if (rows == null || rows.isEmpty() || rows.size() > 20)
            throw new ValidationException("1〜20行で指定してください。");
        // 入力不備では部分作成しない。候補がない行だけをスキップする。
        for (var row : rows) {
            if (row == null || row.testType() == null || !JapaneseModels.TEST_TYPES.contains(row.testType()))
                throw new ValidationException("テスト種別を指定してください。");
            if (row.questionCount() != null && (row.questionCount() < 0 || row.questionCount() > 100))
                throw new ValidationException("数量は0（全部）〜100で指定してください。");
            validateRange(row.categoryFrom(), row.categoryTo());
        }
        List<RowResult> results = new ArrayList<>();
        int created = 0;
        for (var row : rows) {
            try {
                var result = service.createTest(user, row);
                results.add(new RowResult(results.size() + 1, result.test().testId(), false, "作成しました。"));
                created++;
            } catch (ValidationException e) {
                results.add(new RowResult(results.size() + 1, null, true, e.getMessage()));
            }
        }
        return new MultipleResult(created, rows.size() - created, results);
    }

    private static void validateRange(String from, String to) {
        if (from != null && to != null && !from.isBlank() && !to.isBlank() && from.compareTo(to) > 0)
            throw new ValidationException("分類 From は分類 To 以前を指定してください。");
    }
}

