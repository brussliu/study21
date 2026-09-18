package com.study21.user.studymonitor;

import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学習状況モニターの判定結果の手動修正。
 *
 * <p>修正理由は**詳細画面では任意**（ユーザーの指定。2026-09-13）。
 * 一括変更の画面は理由を必須にしているが、サーバーは空の理由も受け付ける。</p>
 */
class StudyMonitorServiceImplTest {

    private StudyMonitorMapper mapper;
    private StudyMonitorServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(StudyMonitorMapper.class);
        service = new StudyMonitorServiceImpl(mapper, System.getProperty("java.io.tmpdir"));
    }

    private UserPrincipal user() {
        return new UserPrincipal(2L, "student@example.com", "試験 生徒", AccountType.STUDENT);
    }

    @Test
    void correctionWithoutReasonIsAccepted() {
        when(mapper.updateManualAnalysis(anyLong(), anyInt(), any(), any(), eq(2L))).thenReturn(1);

        var result = service.correctManually(user(), new StudyMonitorModels.ManualCorrectionRequest(
                List.of(new StudyMonitorModels.ManualUpdate(954, 1)), "AWAY", ""));

        assertThat(result.updatedCount()).isEqualTo(1);
        // 理由が空のときは NULL で残す（「理由なし」と分かるように）
        verify(mapper).updateManualAnalysis(eq(954L), eq(1), eq("AWAY"), isNull(), eq(2L));
    }

    @Test
    void correctionWithReasonStillWorks() {
        when(mapper.updateManualAnalysis(anyLong(), anyInt(), any(), any(), eq(2L))).thenReturn(1);

        service.correctManually(user(), new StudyMonitorModels.ManualCorrectionRequest(
                List.of(new StudyMonitorModels.ManualUpdate(954, 1)), "AWAY", "  椅子に誰も座っていない  "));

        verify(mapper).updateManualAnalysis(eq(954L), eq(1), eq("AWAY"), eq("椅子に誰も座っていない"), eq(2L));
    }

    @Test
    void reasonLongerThan2000CharactersIsRejected() {
        assertThatThrownBy(() -> service.correctManually(user(), new StudyMonitorModels.ManualCorrectionRequest(
                List.of(new StudyMonitorModels.ManualUpdate(954, 1)), "AWAY", "あ".repeat(2001))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2000文字以内");
    }

    @Test
    void analysisResultIsStillRequired() {
        assertThatThrownBy(() -> service.correctManually(user(), new StudyMonitorModels.ManualCorrectionRequest(
                List.of(new StudyMonitorModels.ManualUpdate(954, 1)), "  ", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("分析結果を選択してください");
    }
}
