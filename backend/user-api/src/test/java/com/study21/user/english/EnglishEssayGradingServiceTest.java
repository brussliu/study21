package com.study21.user.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 英作文の**添削の受付**（user-api が唯一の入口）の認可と、**画像の上限を配る API** の検証。
 *
 * <p>確かめる公開の振る舞い:</p>
 * <ol>
 *   <li>受付は**自分の作文だけ**通る。他人・存在しない・削除済みは 404 で、**admin-api を呼ばない**
 *       （呼ぶと他人の作文に添削を積めて、AI 費用を使わせられる）</li>
 *   <li>`round` を省略したら**番号を作らず**そのまま admin-api に任せる（採番の規則を 2 か所に置かない）</li>
 *   <li>admin-api が失敗したら、**日本語の理由**をそのまま返す（500 にしない）</li>
 *   <li>上限は設定から読む。読めないときだけ既定（8 枚 / 10MB）へ落とす</li>
 * </ol>
 */
class EnglishEssayGradingServiceTest {

    private static final long ACCOUNT_ID = 2L;
    private static final long OTHER_ACCOUNT_ID = 99L;
    private static final long ESSAY_ID = 501L;

    @TempDir
    Path tempDir;

    private EnglishEssayMapper mapper;
    private EnglishEssaySettingMapper settingMapper;
    private EnglishEssayAiAdminClient aiAdminClient;
    private EnglishEssayServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(EnglishEssayMapper.class);
        settingMapper = mock(EnglishEssaySettingMapper.class);
        aiAdminClient = mock(EnglishEssayAiAdminClient.class);
        service = new EnglishEssayServiceImpl(mapper,
                new EnglishEssayStorage(tempDir.resolve("english-essay-root").toString()),
                new EnglishEssaySettings(settingMapper), aiAdminClient, new ObjectMapper());
    }

    private static UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "s-e2e@example.com", "検証 生徒", AccountType.STUDENT);
    }

    private static UserPrincipal otherStudent() {
        return new UserPrincipal(OTHER_ACCOUNT_ID, "s-other@example.com", "他人 生徒", AccountType.STUDENT);
    }

    private static EnglishEssayEntity essay() {
        EnglishEssayEntity entity = new EnglishEssayEntity();
        entity.setEssayId(ESSAY_ID);
        entity.setAccountId(ACCOUNT_ID);
        entity.setLevel("GRADE1");
        entity.setTitle("環境問題について");
        entity.setQuestionText("Do you think ...?");
        entity.setEssayText("I have a dream today");
        entity.setWordCount(5);
        entity.setStateCode("A");
        entity.setVersion(1);
        return entity;
    }

    /* ---------------------------------------------------------------- 受付 */

    @Test
    @DisplayName("受付: 自分の作文なら admin-api に渡し、受付けた回と案内をそのまま返す")
    void acceptsOwnEssay() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(aiAdminClient.acceptGrading(ESSAY_ID, 2)).thenReturn(
                new EnglishEssayAiAdminClient.Accepted(7001L, 2, "英検基準AI添削を受付けました（第 2 回）。"));

        EnglishEssayModels.GradingAccepted accepted = service.acceptGrading(student(), ESSAY_ID, 2);

        assertThat(accepted.gradingId()).isEqualTo(7001L);
        assertThat(accepted.round()).isEqualTo(2);
        assertThat(accepted.message()).contains("受付けました");
        // **自分のアカウント**で所有を引いている（他人の作文は 1 行も返らない）
        verify(mapper).findById(ESSAY_ID, ACCOUNT_ID);
        verify(aiAdminClient).acceptGrading(ESSAY_ID, 2);
    }

    @Test
    @DisplayName("受付: 他人の作文は 404 で、admin-api を呼ばない")
    void rejectsOtherAccountsEssay() {
        when(mapper.findById(ESSAY_ID, OTHER_ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.acceptGrading(otherStudent(), ESSAY_ID, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");

        verify(mapper).findById(ESSAY_ID, OTHER_ACCOUNT_ID);
        verify(aiAdminClient, never()).acceptGrading(anyLong(), any());
    }

    @Test
    @DisplayName("受付: 存在しない・削除済みの作文も 404（admin-api を呼ばない）")
    void rejectsMissingOrDeletedEssay() {
        // 削除済み（状態コード='X'）は SQL の条件で 1 行も返らない＝サービスは 404 にする
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.acceptGrading(student(), ESSAY_ID, 3))
                .isInstanceOf(NotFoundException.class);

        verify(aiAdminClient, never()).acceptGrading(anyLong(), any());
    }

    @Test
    @DisplayName("受付: round を省略したら番号を作らず admin-api に任せる（null のまま渡す）")
    void letsAdminApiChooseNextRound() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(aiAdminClient.acceptGrading(ESSAY_ID, null)).thenReturn(
                new EnglishEssayAiAdminClient.Accepted(7002L, 3, "英検基準AI添削を受付けました（第 3 回）。"));

        EnglishEssayModels.GradingAccepted accepted = service.acceptGrading(student(), ESSAY_ID, null);

        verify(aiAdminClient).acceptGrading(ESSAY_ID, null);
        // 何回目かは admin-api の応答から読む（こちらで「今までの最大 + 1」を数えない）
        assertThat(accepted.round()).isEqualTo(3);
    }

    @Test
    @DisplayName("受付: admin-api が失敗したら日本語の理由のまま返す（500 にしない）")
    void keepsJapaneseReasonWhenAdminApiFails() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(aiAdminClient.acceptGrading(ESSAY_ID, null)).thenThrow(
                new EnglishEssayAiAdminClient.EnglishEssayCallException(
                        "設問文と作文本文を先に確定してください。", false));

        assertThatThrownBy(() -> service.acceptGrading(student(), ESSAY_ID, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("設問文と作文本文を先に確定してください。");
    }

    @Test
    @DisplayName("受付: 合言葉が未設定でも黙って成功にしない（日本語の理由で断る）")
    void reportsMissingInternalToken() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(aiAdminClient.acceptGrading(ESSAY_ID, null)).thenThrow(
                new EnglishEssayAiAdminClient.EnglishEssayCallException(
                        "サービス間の認証が設定されていません（STUDY21_INTERNAL_TOKEN）。", false));

        assertThatThrownBy(() -> service.acceptGrading(student(), ESSAY_ID, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("STUDY21_INTERNAL_TOKEN");
    }

    /* ---------------------------------------------------------------- 上限 */

    @Test
    @DisplayName("上限: 設定（枚数・MB）をそのまま返す")
    void readsLimitsFromSettings() {
        when(settingMapper.findByKeys(anyString(), anyList())).thenReturn(List.of(
                setting("ENGLISH_ESSAY_MAX_IMAGES", "4"),
                setting("ENGLISH_ESSAY_MAX_IMAGE_MB", "3")));

        EnglishEssayModels.ImageLimits limits = service.limits();

        assertThat(limits.maxImages()).isEqualTo(4);
        assertThat(limits.maxImageMb()).isEqualTo(3);
    }

    @Test
    @DisplayName("上限: 設定が無い・値が不正なら既定（8 枚 / 10MB）")
    void fallsBackToDefaultsWhenNotConfigured() {
        when(settingMapper.findByKeys(anyString(), anyList())).thenReturn(List.of());
        assertThat(service.limits().maxImages()).isEqualTo(8);
        assertThat(service.limits().maxImageMb()).isEqualTo(10);

        // 0 以下・数値でない値も既定へ（画面の事前チェックを止めない）
        when(settingMapper.findByKeys(anyString(), anyList())).thenReturn(List.of(
                setting("ENGLISH_ESSAY_MAX_IMAGES", "0"),
                setting("ENGLISH_ESSAY_MAX_IMAGE_MB", "たくさん")));
        assertThat(service.limits().maxImages()).isEqualTo(8);
        assertThat(service.limits().maxImageMb()).isEqualTo(10);
    }

    @Test
    @DisplayName("上限: 設定を読めないとき（DB が落ちている等）も既定を返す（画面を止めない）")
    void fallsBackToDefaultsWhenSettingsUnreadable() {
        when(settingMapper.findByKeys(anyString(), anyList()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB が落ちている"));

        EnglishEssayModels.ImageLimits limits = service.limits();

        assertThat(limits.maxImages()).isEqualTo(8);
        assertThat(limits.maxImageMb()).isEqualTo(10);
    }

    private static EnglishEssaySettingEntity setting(String key, String value) {
        EnglishEssaySettingEntity entity = new EnglishEssaySettingEntity();
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        return entity;
    }
}
