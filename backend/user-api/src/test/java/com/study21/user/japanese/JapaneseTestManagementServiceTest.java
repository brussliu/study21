package com.study21.user.japanese;

import com.study21.common.core.exception.ValidationException;
import com.study21.user.security.UserPrincipal;
import com.study21.user.account.AccountType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JapaneseTestManagementServiceTest {
    private final JapaneseService tests = mock(JapaneseService.class);
    private final JpnTestMapper mapper = mock(JpnTestMapper.class);
    private final JapaneseTestManagementService service = new JapaneseTestManagementService(tests, mapper);
    private final UserPrincipal user = new UserPrincipal(2L, "tester", "検証", AccountType.STUDENT);

    private JapaneseModels.TestCreateRequest row(String type, String from, String to) {
        return new JapaneseModels.TestCreateRequest(type, null, "教材", from, to, "NORMAL", "ALL", 0);
    }

    @Test
    void invalidRangeIsRejectedBeforeCreatingAnyRows() {
        assertThatThrownBy(() -> service.create(user, List.of(row("A", "01", "02"), row("B", "02", "01"))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(tests);
    }

    @Test
    void emptyCandidateRowDoesNotDiscardOtherResults() {
        var first = row("A", "01", "02");
        var second = row("B", "01", "02");
        var entity = new JpnTestEntity(); entity.setTestId(77L);
        when(tests.createTest(user, first)).thenReturn(new JapaneseModels.TestDetailResult(JapaneseServiceImpl.toTestRow(entity), List.of()));
        when(tests.createTest(user, second)).thenThrow(new ValidationException("条件に合う問題がありません。"));
        var result = service.create(user, List.of(first, second));
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.results().get(1).rowNo()).isEqualTo(2);
        assertThat(result.results().get(1).message()).contains("問題がありません");
    }

    @Test
    void rangeSearchKeepsAccountAndPaginationInBothQueries() {
        when(mapper.countRange(2L, "RUNNING", "B", "教材", "01", "02")).thenReturn(17L);
        when(mapper.searchRange(2L, "RUNNING", "B", "教材", "01", "02", 15, 15)).thenReturn(List.of());
        when(mapper.totals(2L)).thenReturn(new JpnTotalsEntity());
        var result = service.search(2L, "RUNNING", "B", "教材", "01", "02", 2, 15);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(mapper).searchRange(2L, "RUNNING", "B", "教材", "01", "02", 15, 15);
    }

    @Test
    void noRowsOrTooManyRowsAreRejected() {
        assertThatThrownBy(() -> service.create(user, List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(user, java.util.Collections.nCopies(21, row("A", null, null))))
                .isInstanceOf(ValidationException.class);
        verify(tests, never()).createTest(any(), any());
    }
}
