package com.study21.user.linkclip;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.study21.common.core.exception.ApiException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * リンククリップ登録のテスト。
 *
 * <p>公開インタフェースは {@link LinkClipService#create}（REST では POST /api/user/link-clips）。
 * 保護者が「お子さまのリンククリップにも登録する」を選んだとき、紐づく生徒アカウントにも
 * 同じ内容のクリップを 1 件登録する（所有者は別なので、あとから各自で編集できる）。</p>
 */
class LinkClipServiceTest {

    private static final long GUARDIAN_ID = 10L;
    private static final long STUDENT_ID = 20L;

    private LinkClipMapper mapper;
    private LinkClipMetadataFetcher metadataFetcher;
    private AccountMapper accountMapper;
    private LinkClipService service;

    @BeforeEach
    void setUp() {
        mapper = mock(LinkClipMapper.class);
        metadataFetcher = mock(LinkClipMetadataFetcher.class);
        accountMapper = mock(AccountMapper.class);
        service = new LinkClipService(mapper, metadataFetcher, accountMapper);

        when(metadataFetcher.fetch(anyString())).thenReturn(
                new LinkClipMetadataFetcher.Metadata("example.com", "タイトル", null, null, null, null, null));

        // insert は useGeneratedKeys で採番されるため、テストでは所有者ごとの固定 ID を振る
        when(mapper.insert(any(LinkClipEntity.class))).thenAnswer(invocation -> {
            LinkClipEntity clip = invocation.getArgument(0);
            clip.setLinkClipId(clip.getOwnerAccountId() * 100);
            return 1;
        });

        // 応答（rowOf）は再取得するため、findById にも応答させる
        when(mapper.findById(anyLong(), anyLong())).thenAnswer(invocation -> {
            long ownerId = invocation.getArgument(0);
            long linkClipId = invocation.getArgument(1);
            LinkClipEntity clip = new LinkClipEntity();
            clip.setLinkClipId(linkClipId);
            clip.setOwnerAccountId(ownerId);
            clip.setFolderCode("INBOX");
            clip.setSourceCode("WEB");
            clip.setClipType("LINK");
            clip.setPageTitle("タイトル");
            clip.setUrl("https://example.com/a");
            clip.setNormalizedUrl("https://example.com/a");
            return clip;
        });
        when(mapper.findTagsByClipIds(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void createsOnlyGuardianClipWhenFlagIsAbsent() {
        service.create(guardian(), request(null));

        ArgumentCaptor<LinkClipEntity> captor = ArgumentCaptor.forClass(LinkClipEntity.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getOwnerAccountId()).isEqualTo(GUARDIAN_ID);
        verify(accountMapper, never()).findStudentByGuardianId(anyLong());
    }

    @Test
    void createsStudentClipWithSameContentWhenFlagIsTrue() {
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(student());

        service.create(guardian(), request(true));

        ArgumentCaptor<LinkClipEntity> captor = ArgumentCaptor.forClass(LinkClipEntity.class);
        verify(mapper, times(2)).insert(captor.capture());
        List<LinkClipEntity> inserts = captor.getAllValues();

        assertThat(inserts).extracting(LinkClipEntity::getOwnerAccountId)
                .containsExactly(GUARDIAN_ID, STUDENT_ID);
        assertThat(inserts).allSatisfy(clip -> {
            assertThat(clip.getUrl()).isEqualTo("https://example.com/a");
            assertThat(clip.getPageTitle()).isEqualTo("タイトル");
            assertThat(clip.getFolderCode()).isEqualTo("INBOX");
            assertThat(clip.getSourceCode()).isEqualTo("WEB");
            assertThat(clip.getClipType()).isEqualTo("LINK");
        });
        // 内容はコピーするので、メタ情報の取得は 1 回で済む
        verify(metadataFetcher, times(1)).fetch(anyString());
        // タグもお子さま側に付く（所有者はお子さま）
        verify(mapper).insertTag(anyLong(), eq("英語"), eq(0), eq(STUDENT_ID));
    }

    @Test
    void failsWholeSaveWhenStudentIsMissing() {
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.create(guardian(), request(true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("お子さま");

        // 中途半端な状態を作らない（保護者側も登録しない）
        verify(mapper, never()).insert(any(LinkClipEntity.class));
    }

    @Test
    void rejectsStudentRequestWithTheFlag() {
        assertThatThrownBy(() -> service.create(studentPrincipal(), request(true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("保護者");

        verify(mapper, never()).insert(any(LinkClipEntity.class));
    }

    private UserPrincipal guardian() {
        return new UserPrincipal(GUARDIAN_ID, "parent@example.com", "保護者", AccountType.GUARDIAN);
    }

    private UserPrincipal studentPrincipal() {
        return new UserPrincipal(STUDENT_ID, "student@example.com", "生徒", AccountType.STUDENT);
    }

    private AccountEntity student() {
        AccountEntity entity = new AccountEntity();
        entity.setAccountId(STUDENT_ID);
        entity.setLoginId("student@example.com");
        entity.setAccountType("STUDENT");
        entity.setGuardianId(GUARDIAN_ID);
        return entity;
    }

    private LinkClipModels.SaveRequest request(Boolean alsoForStudent) {
        return new LinkClipModels.SaveRequest(
                "https://example.com/a", "タイトル", "example.com", "INBOX", "WEB", "LINK",
                null, null, "メモ", null, null, null, null, false, false, List.of("英語"), alsoForStudent);
    }
}
