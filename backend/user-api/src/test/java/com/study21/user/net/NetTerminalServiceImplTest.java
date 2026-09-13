package com.study21.user.net;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 端末コントロールの業務ルール。メッセージは 2.0 を引き継ぐ。
 *
 * 2.0 は保護者だけが操作できたが、2.1 は**当面ロールで分けない**
 * （ログインしていれば生徒・保護者とも使える。ユーザーの指定）。
 */
class NetTerminalServiceImplTest {

    private static final long GUARDIAN_ID = 3L;

    private NetTerminalMapper terminalMapper;
    private NetTerminalServiceImpl service;

    @BeforeEach
    void setUp() {
        terminalMapper = mock(NetTerminalMapper.class);
        service = new NetTerminalServiceImpl(terminalMapper);
    }

    private UserPrincipal guardian() {
        return new UserPrincipal(GUARDIAN_ID, "parent@example.com", "試験 保護者", AccountType.GUARDIAN);
    }

    private UserPrincipal student() {
        return new UserPrincipal(4L, "student@example.com", "試験 生徒", AccountType.STUDENT);
    }

    private NetTerminalEntity terminal(long id, String mode, int version) {
        NetTerminalEntity entity = new NetTerminalEntity();
        entity.setTerminalId(id);
        entity.setIpAddress("192.168.0.92");
        entity.setTerminalName("勉強用PC");
        entity.setTerminalMode(mode);
        entity.setStatus("1");
        entity.setVersion(version);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    @Test
    void studentsCanUseTerminalControlBecauseRolesAreNotSeparatedYet() {
        // 生徒でも一覧を読める
        when(terminalMapper.count(any(), any(), any())).thenReturn(1L);
        when(terminalMapper.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(terminal(1L, "T", 1)));
        assertThat(service.search(student(), null).items()).hasSize(1);

        // 生徒でもモードを変えられる（更新者はその生徒）
        when(terminalMapper.findById(1L)).thenReturn(terminal(1L, "T", 1));
        // 版を指定しない更新（画面のモード変更は版を送らない）
        when(terminalMapper.updateMode(1L, "B", null, 4L)).thenReturn(1);
        assertThat(service.updateMode(student(), 1L,
                new NetTerminalModels.ModeChangeRequest("B", null)).updatedCount()).isEqualTo(1);
        verify(terminalMapper).updateMode(1L, "B", null, 4L);
    }

    @Test
    void loginIsRequired() {
        assertThatThrownBy(() -> service.search(null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ログイン");

        assertThatThrownBy(() -> service.updateMode(null, 1L,
                new NetTerminalModels.ModeChangeRequest("B", null)))
                .isInstanceOf(ValidationException.class);

        verify(terminalMapper, never()).updateMode(anyLong(), any(), any(), any());
    }

    @Test
    void guardianCanListTerminals() {
        when(terminalMapper.count(isNull(), isNull(), isNull())).thenReturn(5L);
        when(terminalMapper.search(isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(terminal(1L, "T", 1)));

        NetTerminalModels.TerminalSearchResult result = service.search(guardian(),
                new NetTerminalService.NetTerminalSearchQuery(null, null, null, null, null));

        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).terminalMode()).isEqualTo("T");
    }

    private NetTerminalModels.TerminalSaveRequest save(String ip, String name, String mode, String status) {
        return new NetTerminalModels.TerminalSaveRequest(ip, name, mode, status, "検証用", null);
    }

    @Test
    void createRegistersANewTerminal() {
        when(terminalMapper.findActiveByIpAddress("192.168.0.50", null)).thenReturn(null);
        when(terminalMapper.insert(any(NetTerminalEntity.class))).thenReturn(1);

        NetTerminalModels.TerminalMutationResult result =
                service.create(guardian(), save("192.168.0.50", "リビングのPC", "T", "1"));

        assertThat(result.message()).contains("登録しました");
        assertThat(result.updatedCount()).isEqualTo(1);
        // 登録者・更新者は操作したアカウント
        verify(terminalMapper).insert(org.mockito.ArgumentMatchers.argThat(entity ->
                "192.168.0.50".equals(entity.getIpAddress())
                        && "リビングのPC".equals(entity.getTerminalName())
                        && "T".equals(entity.getTerminalMode())
                        && "1".equals(entity.getStatus())
                        && Long.valueOf(GUARDIAN_ID).equals(entity.getCreatedByAccountId())
                        && Long.valueOf(GUARDIAN_ID).equals(entity.getUpdatedByAccountId())));
    }

    @Test
    void createValidatesIpNameModeStatusAndDuplicates() {
        // IP の書式
        for (String bad : List.of("", "192.168.0", "192.168.0.256", "abc", "192.168.0.1.5", "1.2.3.4:5")) {
            assertThatThrownBy(() -> service.create(guardian(), save(bad, "PC", "T", "1")))
                    .as("IP=%s", bad)
                    .isInstanceOf(ValidationException.class);
        }
        // 端末名称・モード・状態
        assertThatThrownBy(() -> service.create(guardian(), save("192.168.0.51", "  ", "T", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("端末名称");
        assertThatThrownBy(() -> service.create(guardian(), save("192.168.0.51", "PC", "X", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("端末ステータス");
        assertThatThrownBy(() -> service.create(guardian(), save("192.168.0.51", "PC", "T", "9")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("状態");
        // 有効な端末で IP が重複
        when(terminalMapper.findActiveByIpAddress("192.168.0.52", null)).thenReturn(terminal(9L, "T", 1));
        assertThatThrownBy(() -> service.create(guardian(), save("192.168.0.52", "PC", "T", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に登録されています");
        verify(terminalMapper, never()).insert(any(NetTerminalEntity.class));
    }

    @Test
    void createAcceptsIpv6AndTrimsTheInput() {
        when(terminalMapper.findActiveByIpAddress("fe80::1", null)).thenReturn(null);
        when(terminalMapper.insert(any(NetTerminalEntity.class))).thenReturn(1);

        service.create(guardian(), save("  fe80::1  ", "  タブレット  ", "B", "1"));

        verify(terminalMapper).insert(org.mockito.ArgumentMatchers.argThat(entity ->
                "fe80::1".equals(entity.getIpAddress()) && "タブレット".equals(entity.getTerminalName())));
    }

    @Test
    void updateEditsTheTerminalWithOptimisticLock() {
        when(terminalMapper.findById(7L)).thenReturn(terminal(7L, "T", 3));
        when(terminalMapper.findActiveByIpAddress("192.168.0.60", 7L)).thenReturn(null);
        when(terminalMapper.update(7L, "192.168.0.60", "書斎のPC", "S", "0", "退役", 3, GUARDIAN_ID))
                .thenReturn(1);

        NetTerminalModels.TerminalMutationResult result = service.update(guardian(), 7L,
                new NetTerminalModels.TerminalSaveRequest("192.168.0.60", "書斎のPC", "S", "0", "退役", 3));

        assertThat(result.message()).contains("更新しました");
        verify(terminalMapper).update(7L, "192.168.0.60", "書斎のPC", "S", "0", "退役", 3, GUARDIAN_ID);
    }

    @Test
    void updateRejectsUnknownTerminalAndStaleVersion() {
        when(terminalMapper.findById(7L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(guardian(), 7L, save("192.168.0.60", "PC", "T", "1")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");

        when(terminalMapper.findById(7L)).thenReturn(terminal(7L, "T", 3));
        when(terminalMapper.findActiveByIpAddress("192.168.0.60", 7L)).thenReturn(null);
        when(terminalMapper.update(anyLong(), any(), any(), any(), any(), any(), any(), anyLong())).thenReturn(0);
        assertThatThrownBy(() -> service.update(guardian(), 7L, save("192.168.0.60", "PC", "T", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("他の操作で先に更新されました");
    }

    @Test
    void updateChecksTheIpAgainstActiveTerminalsButAllowsItself() {
        // 自分自身の IP のまま編集するのは重複ではない（exclude に自分の ID を渡す）
        when(terminalMapper.findById(7L)).thenReturn(terminal(7L, "T", 3));
        when(terminalMapper.findActiveByIpAddress("192.168.0.92", 7L)).thenReturn(null);
        when(terminalMapper.update(eq(7L), eq("192.168.0.92"), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(1);
        assertThat(service.update(guardian(), 7L, save("192.168.0.92", "同じIPのまま", "T", "1")).updatedCount())
                .isEqualTo(1);

        // 他の有効な端末と同じ IP に変えるのは拒否
        when(terminalMapper.findActiveByIpAddress("192.168.0.93", 7L)).thenReturn(terminal(8L, "T", 1));
        assertThatThrownBy(() -> service.update(guardian(), 7L, save("192.168.0.93", "重複", "T", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に登録されています");
    }

    @Test
    void updateModeValidatesIdModeAndExistence() {
        assertThatThrownBy(() -> service.updateMode(guardian(), 0L,
                new NetTerminalModels.ModeChangeRequest("B", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("端末IDが不正です。");

        assertThatThrownBy(() -> service.updateMode(guardian(), 1L,
                new NetTerminalModels.ModeChangeRequest("X", null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("端末ステータスが不正です。");

        when(terminalMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateMode(guardian(), 99L,
                new NetTerminalModels.ModeChangeRequest("B", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("更新対象の端末が見つかりません。");
    }

    @Test
    void updateModeUsesOptimisticLock() {
        when(terminalMapper.findById(1L)).thenReturn(terminal(1L, "T", 4));
        when(terminalMapper.updateMode(1L, "B", 4, GUARDIAN_ID)).thenReturn(1);

        NetTerminalModels.TerminalMutationResult result = service.updateMode(guardian(), 1L,
                new NetTerminalModels.ModeChangeRequest("b", 4));

        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("端末ステータスを更新しました。");
        verify(terminalMapper).updateMode(1L, "B", 4, GUARDIAN_ID);

        when(terminalMapper.updateMode(eq(2L), eq("B"), any(), any())).thenReturn(0);
        when(terminalMapper.findById(2L)).thenReturn(terminal(2L, "T", 9));
        assertThatThrownBy(() -> service.updateMode(guardian(), 2L,
                new NetTerminalModels.ModeChangeRequest("B", 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("他の保護者が先に更新しました");
    }

    @Test
    void bulkUpdateRequiresSelectionAndValidMode() {
        assertThatThrownBy(() -> service.updateModes(guardian(),
                new NetTerminalModels.BulkModeChangeRequest(List.of(), "B")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("更新対象の端末を選択してください。");

        assertThatThrownBy(() -> service.updateModes(guardian(),
                new NetTerminalModels.BulkModeChangeRequest(List.of(1L), "Z")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("端末ステータスが不正です。");

        verify(terminalMapper, never()).updateModes(anyList(), any(), any());
    }

    @Test
    void bulkUpdateReportsRequestedAndUpdatedCounts() {
        when(terminalMapper.updateModes(List.of(1L, 2L), "K", GUARDIAN_ID)).thenReturn(2);

        NetTerminalModels.TerminalMutationResult result = service.updateModes(guardian(),
                new NetTerminalModels.BulkModeChangeRequest(List.of(1L, 2L, 2L), "k"));

        assertThat(result.requestedCount()).isEqualTo(2); // 重複 ID は 1 つにまとめる
        assertThat(result.updatedCount()).isEqualTo(2);
        verify(terminalMapper).updateModes(List.of(1L, 2L), "K", GUARDIAN_ID);
    }

    @Test
    void searchValidatesModeAndStatusFilters() {
        assertThatThrownBy(() -> service.search(guardian(),
                new NetTerminalService.NetTerminalSearchQuery("X", null, null, null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.search(guardian(),
                new NetTerminalService.NetTerminalSearchQuery(null, "9", null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("状態が不正です。");
    }
}
