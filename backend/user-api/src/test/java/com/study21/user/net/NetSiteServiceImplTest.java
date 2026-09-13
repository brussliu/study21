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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * サイト管理の業務ルール（2.0 の SiteServiceImpl の挙動を引き継いでいること）。
 */
class NetSiteServiceImplTest {

    private static final long ACCOUNT_ID = 3L;

    private NetSiteMapper siteMapper;
    private NetSiteServiceImpl service;

    @BeforeEach
    void setUp() {
        siteMapper = mock(NetSiteMapper.class);
        service = new NetSiteServiceImpl(siteMapper);
    }

    private UserPrincipal user() {
        return new UserPrincipal(ACCOUNT_ID, "parent@example.com", "試験 保護者", AccountType.GUARDIAN);
    }

    private NetSiteModels.SiteSaveRequest request() {
        return new NetSiteModels.SiteSaveRequest(
                " 例のサイト ", "https://www.Example.com:8443/learning?x=1", "study", null, "learning", null, " メモ ", null);
    }

    private NetSiteEntity entity(long siteId, String approvalStatus, String status, int version) {
        NetSiteEntity entity = new NetSiteEntity();
        entity.setSiteId(siteId);
        entity.setSiteName("サイト");
        entity.setSiteUrl("example.com");
        entity.setHostName("example.com");
        entity.setKindCode("STUDY");
        entity.setJudgeMethodCode("SUFFIX");
        entity.setCategoryCode("OTHER");
        entity.setApprovalStatus(approvalStatus);
        entity.setStatus(status);
        entity.setVersion(version);
        entity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    @Test
    void normalizeHostStripsSchemePortPathAndWww() {
        assertThat(NetSiteServiceImpl.normalizeHost("https://www.Example.com:8443/learning?x=1"))
                .isEqualTo("example.com");
        assertThat(NetSiteServiceImpl.normalizeHost("  YouTube.COM  ")).isEqualTo("youtube.com");
        assertThat(NetSiteServiceImpl.normalizeHost("http://192.168.0.100")).isEqualTo("192.168.0.100");
        assertThat(NetSiteServiceImpl.normalizeHost("example.com")).isEqualTo("example.com");
    }

    @Test
    void createRegistersPendingAndNormalizesValues() {
        when(siteMapper.insert(any(NetSiteEntity.class))).thenAnswer(invocation -> {
            NetSiteEntity inserted = invocation.getArgument(0);
            inserted.setSiteId(500L);
            return 1;
        });
        when(siteMapper.findById(500L)).thenReturn(entity(500L, "PENDING", "1", 1));

        NetSiteModels.SiteMutationResult result = service.create(user(), request());

        assertThat(result.message()).isEqualTo("サイトを登録しました。");
        assertThat(result.row().approvalStatus()).isEqualTo("PENDING");
        var captor = org.mockito.ArgumentCaptor.forClass(NetSiteEntity.class);
        verify(siteMapper).insert(captor.capture());
        NetSiteEntity inserted = captor.getValue();
        assertThat(inserted.getSiteName()).isEqualTo("例のサイト");
        assertThat(inserted.getHostName()).isEqualTo("example.com");
        assertThat(inserted.getKindCode()).isEqualTo("STUDY");
        assertThat(inserted.getCategoryCode()).isEqualTo("LEARNING");
        // 分類が OTHER 以外なら分類名称は保存しない
        assertThat(inserted.getCategoryName()).isNull();
        assertThat(inserted.getJudgeMethodCode()).isEqualTo("SUFFIX");
        assertThat(inserted.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(inserted.getStatus()).isEqualTo("1");
        assertThat(inserted.getNote()).isEqualTo("メモ");
        assertThat(inserted.getCreatedByAccountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void createKeepsCategoryNameOnlyForOther() {
        when(siteMapper.insert(any(NetSiteEntity.class))).thenAnswer(invocation -> {
            NetSiteEntity inserted = invocation.getArgument(0);
            inserted.setSiteId(501L);
            return 1;
        });
        when(siteMapper.findById(501L)).thenReturn(entity(501L, "PENDING", "1", 1));

        service.create(user(), new NetSiteModels.SiteSaveRequest(
                "英会話", "eikaiwa.example.com", "STUDY", "PREFIX", "OTHER", " 英会話 ", null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(NetSiteEntity.class);
        verify(siteMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategoryName()).isEqualTo("英会話");
        assertThat(captor.getValue().getJudgeMethodCode()).isEqualTo("PREFIX");
    }

    @Test
    void createRejectsUnknownCodesAndBlankValues() {
        assertThatThrownBy(() -> service.create(user(), new NetSiteModels.SiteSaveRequest(
                "x", "x.example.com", "HOBBY", null, "OTHER", null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("区分");

        assertThatThrownBy(() -> service.create(user(), new NetSiteModels.SiteSaveRequest(
                "x", "x.example.com", "STUDY", null, "GAME", null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("分類");

        assertThatThrownBy(() -> service.create(user(), new NetSiteModels.SiteSaveRequest(
                "x", "  ", "STUDY", null, "OTHER", null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("サイトURL");

        assertThatThrownBy(() -> service.create(user(), new NetSiteModels.SiteSaveRequest(
                "  ", "x.example.com", "STUDY", null, "OTHER", null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("サイト名称");

        verify(siteMapper, never()).insert(any(NetSiteEntity.class));
    }

    @Test
    void updateRequiresExistingSiteAndMatchingVersion() {
        when(siteMapper.findById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(user(), 9L, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("対象サイトが存在しません。");

        when(siteMapper.findById(10L)).thenReturn(entity(10L, "APPROVED", "1", 3));
        when(siteMapper.update(any(NetSiteEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(user(), 10L, request()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("他の管理者が先に更新しました");
    }

    @Test
    void updateSendsCurrentVersionAndKeepsStatus() {
        when(siteMapper.findById(10L))
                .thenReturn(entity(10L, "APPROVED", "0", 3), entity(10L, "PENDING", "0", 4));
        when(siteMapper.update(any(NetSiteEntity.class))).thenReturn(1);

        service.update(user(), 10L, request());

        var captor = org.mockito.ArgumentCaptor.forClass(NetSiteEntity.class);
        verify(siteMapper).update(captor.capture());
        // 画面が見ていたバージョンを照合に使う（未指定なら現在値）
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        // 編集しても有効／無効は変えない（未承認に戻るだけ）
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
    }

    @Test
    void updateUsesVersionSentByTheScreen() {
        when(siteMapper.findById(11L)).thenReturn(entity(11L, "APPROVED", "1", 7));
        when(siteMapper.update(any(NetSiteEntity.class))).thenReturn(1);
        when(siteMapper.findById(11L)).thenReturn(entity(11L, "PENDING", "1", 8));

        service.update(user(), 11L, new NetSiteModels.SiteSaveRequest(
                "x", "x.example.com", "STUDY", "SUFFIX", "OTHER", null, null, 5));

        var captor = org.mockito.ArgumentCaptor.forClass(NetSiteEntity.class);
        verify(siteMapper).update(captor.capture());
        // 画面が持っていたバージョン 5 で照合する（サーバー側の現在値 7 ではなく）
        assertThat(captor.getValue().getVersion()).isEqualTo(5);
    }

    @Test
    void approveStoresApproverAndTimestamp() {
        when(siteMapper.findById(10L))
                .thenReturn(entity(10L, "PENDING", "1", 2), entity(10L, "APPROVED", "1", 3));

        NetSiteModels.SiteMutationResult result = service.approve(user(), 10L);

        assertThat(result.message()).isEqualTo("サイトを承認しました。");
        verify(siteMapper).approve(eq(10L), eq(ACCOUNT_ID), any(Timestamp.class), eq(ACCOUNT_ID));
    }

    @Test
    void approveRejectsMissingSite() {
        when(siteMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.approve(user(), 99L))
                .isInstanceOf(NotFoundException.class);
        verify(siteMapper, never()).approve(anyLong(), any(), any(), any());
    }

    @Test
    void deleteRejectsMissingSite() {
        when(siteMapper.delete(99L)).thenReturn(0);
        assertThatThrownBy(() -> service.delete(user(), 99L))
                .isInstanceOf(NotFoundException.class);

        when(siteMapper.delete(10L)).thenReturn(1);
        assertThat(service.delete(user(), 10L).message()).isEqualTo("サイトを削除しました。");
    }

    @Test
    void rejectRequiresExistingSiteAndClearsApproval() {
        when(siteMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.reject(user(), 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("対象サイトが存在しません。");

        when(siteMapper.findById(10L)).thenReturn(entity(10L, "APPROVED", "1", 3));
        when(siteMapper.findById(10L)).thenReturn(entity(10L, "REJECTED", "1", 4));

        NetSiteModels.SiteMutationResult result = service.reject(user(), 10L);

        assertThat(result.message()).isEqualTo("サイトを却下しました。");
        assertThat(result.row().approvalStatus()).isEqualTo("REJECTED");
        verify(siteMapper).reject(10L, ACCOUNT_ID);
    }

    @Test
    void studentCanApproveBecauseRolesAreNotSeparatedYet() {
        // 権限は当面区別しない（保護者・生徒のどちらでも承認できる）
        UserPrincipal student = new UserPrincipal(4L, "student@example.com", "試験 生徒", AccountType.STUDENT);
        when(siteMapper.findById(10L)).thenReturn(entity(10L, "PENDING", "1", 2));
        when(siteMapper.findById(10L)).thenReturn(entity(10L, "APPROVED", "1", 3));

        service.approve(student, 10L);

        verify(siteMapper).approve(eq(10L), eq(4L), any(Timestamp.class), eq(4L));
    }

    @Test
    void searchClampsPageAndValidatesSort() {
        when(siteMapper.count(any(), any(), any(), any(), any(), any())).thenReturn(175L);
        when(siteMapper.search(any(), any(), any(), any(), any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(1L, "APPROVED", "1", 1)));

        NetSiteModels.SiteSearchResult result = service.search(new NetSiteService.NetSiteSearchQuery(
                "study", null, null, "approved", "1", " youtube ", "siteName", "asc", 99, 15));

        assertThat(result.totalElements()).isEqualTo(175);
        assertThat(result.totalPages()).isEqualTo(12);
        // 要求ページが総ページを超えたら最後のページに丸める（2.0 と同じ）
        assertThat(result.page()).isEqualTo(12);
        assertThat(result.items()).hasSize(1);
        verify(siteMapper).search(eq("STUDY"), isNull(), isNull(), eq("APPROVED"), eq("1"), eq("youtube"),
                eq("\"サイト名称\""), eq("ASC"), eq(15), eq(165));

        assertThatThrownBy(() -> service.search(new NetSiteService.NetSiteSearchQuery(
                null, null, null, null, null, null, "password", null, 1, 15)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("並び替え");
    }

    @Test
    void siteUrlSortUsesTheReversedHostKey() {
        when(siteMapper.count(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(siteMapper.search(any(), any(), any(), any(), any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(entity(1L, "APPROVED", "1", 1)));

        service.search(new NetSiteService.NetSiteSearchQuery(
                null, null, null, null, null, null, "siteUrl", "asc", 1, 15));

        // 「サイトURL」をそのまま並べると accounts.google.com と google.com が離れる。
        // ホスト名のラベルを逆順にしたキー（com.google / com.google.accounts）で並べる。
        verify(siteMapper).search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                argThat(column -> column.contains("string_agg")
                        && column.contains("ORDER BY label.pos DESC")
                        && column.contains("ホスト名")
                        && column.startsWith("COALESCE(")),
                eq("ASC"), eq(15), eq(0));
    }

    @Test
    void judgeMethodCanBeFiltered() {
        when(siteMapper.count(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(siteMapper.search(any(), any(), any(), any(), any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        service.search(new NetSiteService.NetSiteSearchQuery(
                null, "exact", null, null, null, null, null, null, 1, 15));

        // 判定方法は大文字化して渡す（画面の「判定方法」の絞り込み）
        verify(siteMapper).count(isNull(), eq("EXACT"), isNull(), isNull(), isNull(), isNull());
    }
}
