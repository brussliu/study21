package com.study21.user.reading;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 読書管理の業務ルール（書籍の登録・修正・進捗、読書記録、標記）。
 *
 * 2.0 の規則をそのまま引き継いでいる:
 * ・一覧は 置頂 → 最後に読んだ順
 * ・読書記録を付けると 現在ページ・ステータス（総ページに達したら 読了）・
 *   最近/累計の読書時間・最終読書日時 が一度に更新される
 * ・累計標記件数 は標記の追加・削除のたびに数え直す
 */
class ReadingServiceImplTest {

    private static final long BOOK_ID = 42L;
    private static final long ACCOUNT_ID = 2L;
    /** 保護者の家庭（＝自分の生徒のアカウントID）。 */
    private static final long FAMILY_STUDENT_ID = 21L;
    /** 管理者のアカウントID（家庭を持たない）。 */
    private static final long ADMIN_ACCOUNT_ID = 31L;
    private static final long CATEGORY_ID = 7L;

    private ReadingBookMapper bookMapper;
    private ReadingRecordMapper recordMapper;
    private ReadingMarkMapper markMapper;
    private ReadingCategoryMapper categoryMapper;
    private ReadingFileMapper fileMapper;
    /** 家庭（保護者—生徒の紐付け）の解決に使う */
    private AccountMapper accountMapper;
    /** 実体ファイルはテストごとの一時ディレクトリに置く（リポジトリを汚さない） */
    @TempDir
    Path storageRoot;
    private ReadingFileStorage storage;
    private ReadingServiceImpl service;

    @BeforeEach
    void setUp() {
        bookMapper = mock(ReadingBookMapper.class);
        recordMapper = mock(ReadingRecordMapper.class);
        markMapper = mock(ReadingMarkMapper.class);
        categoryMapper = mock(ReadingCategoryMapper.class);
        fileMapper = mock(ReadingFileMapper.class);
        accountMapper = mock(AccountMapper.class);
        storage = new ReadingFileStorage(storageRoot.toString(), "");
        service = new ReadingServiceImpl(bookMapper, recordMapper, markMapper, categoryMapper, fileMapper,
                storage, accountMapper);

        // 保護者の家庭（＝自分の生徒のアカウントID）を解決できるようにする
        AccountEntity familyStudent = new AccountEntity();
        familyStudent.setAccountId(FAMILY_STUDENT_ID);
        when(accountMapper.findStudentByGuardianId(ACCOUNT_ID)).thenReturn(familyStudent);

        // MyBatis は採番した主キーをエンティティに書き戻す（useGeneratedKeys）。
        // モックでも同じ振る舞いにして、登録直後の参照が本番と同じ経路になるようにする。
        doAnswer(invocation -> {
            ReadingBookEntity entity = invocation.getArgument(0);
            entity.setBookId(BOOK_ID);
            return 1;
        }).when(bookMapper).insert(any());
        doAnswer(invocation -> {
            ReadingRecordEntity entity = invocation.getArgument(0);
            entity.setRecordId(77L);
            return 1;
        }).when(recordMapper).insert(any());
        doAnswer(invocation -> {
            ReadingMarkEntity entity = invocation.getArgument(0);
            entity.setMarkId(88L);
            return 1;
        }).when(markMapper).insert(any());
        doAnswer(invocation -> {
            ReadingCategoryEntity entity = invocation.getArgument(0);
            entity.setCategoryId(CATEGORY_ID);
            return 1;
        }).when(categoryMapper).insert(any());
    }

    /**
     * 本を登録・修正する人（保護者。2026-09-14 の決定で生徒は登録・修正できない）。
     * 家庭は {@link #FAMILY_STUDENT_ID} に解決される（AccountMapper をモックしている）。
     */
    private UserPrincipal guardian() {
        return new UserPrincipal(ACCOUNT_ID, "guardian@example.com", "試験 保護者", AccountType.GUARDIAN);
    }

    /** 読むだけの人（生徒）。家庭は自分のアカウント。 */
    private UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "student@example.com", "試験 生徒", AccountType.STUDENT);
    }

    private ReadingBookEntity book(int currentPage, int totalPages, int version) {
        ReadingBookEntity entity = new ReadingBookEntity();
        entity.setBookId(BOOK_ID);
        entity.setBookNo("ER-20260402-124745");
        entity.setSubject("英語");
        entity.setTitle("The Secret Garden");
        entity.setAuthor("Frances Hodgson Burnett");
        entity.setDifficulty("Elementary");
        entity.setStatus("読書中");
        entity.setTotalPages(totalPages);
        entity.setCurrentPage(currentPage);
        entity.setPinned(false);
        entity.setTags("Novel,Classic");
        entity.setRecentMinutes(10);
        entity.setTotalMinutes(120);
        entity.setMarkCount(3);
        entity.setVersion(version);
        // 2026-09-14 の追加: 公開範囲と所有する家庭。
        // この土台は「保護者が自分の家庭の本を扱う」経路を確かめるので FAMILY にする
        // （全体書籍＝GLOBAL は管理者だけが直せる）。
        entity.setScope(ReadingScope.SCOPE_FAMILY);
        entity.setOwnerFamilyId(FAMILY_STUDENT_ID);
        return entity;
    }

    /**
     * 本を登録・修正する人（管理者）。家庭を持たないので全体書籍だけが見える。
     * アカウントID は {@link #ADMIN_ACCOUNT_ID}。
     */
    private UserPrincipal admin() {
        return new UserPrincipal(ADMIN_ACCOUNT_ID, "admin@example.com", "試験 管理者", AccountType.ADMIN);
    }

    /** 全体書籍（管理者が作った本）。保護者は読めるが直せない。 */
    private ReadingBookEntity globalBook() {
        ReadingBookEntity entity = book(1, 100, 1);
        entity.setScope(ReadingScope.SCOPE_GLOBAL);
        entity.setOwnerFamilyId(null);
        return entity;
    }

    /** 別の家庭の本（FAMILY で持ち主が違う）。自分の家庭からは見えない。 */
    private ReadingBookEntity otherFamilyBook() {
        ReadingBookEntity entity = book(1, 100, 1);
        entity.setScope(ReadingScope.SCOPE_FAMILY);
        entity.setOwnerFamilyId(FAMILY_STUDENT_ID + 1);
        return entity;
    }

    private ReadingModels.BookSaveRequest saveRequest(int totalPages, Integer currentPage, Integer version) {
        return new ReadingModels.BookSaveRequest("The Secret Garden", "Frances Hodgson Burnett",
                "Elementary", "未着手", totalPages, currentPage, false, List.of("Novel"), "概要", null, version,
                null, null);
    }

    // ------------------------------------------------------------ 登録・修正

    @Test
    void createsBookWithGeneratedNoAndDefaults() {
        when(bookMapper.findByNo(any())).thenReturn(null);
        when(bookMapper.findById(anyLong())).thenReturn(book(1, 100, 1));

        service.createBook(guardian(), saveRequest(100, null, null));

        ArgumentCaptor<ReadingBookEntity> captor = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper).insert(captor.capture());
        ReadingBookEntity saved = captor.getValue();
        assertThat(saved.getBookNo()).matches("ER-\\d{8}-\\d{6}");
        assertThat(saved.getSubject()).isEqualTo("英語");
        assertThat(saved.getCurrentPage()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("未着手");
        assertThat(saved.getTags()).isEqualTo("Novel");
        assertThat(saved.getCreatedBy()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void rejectsUnknownDifficultyOnCreate() {
        assertThatThrownBy(() -> service.createBook(guardian(),
                new ReadingModels.BookSaveRequest("本", "著者", "Beginner", "未着手", 10, null, null,
                        List.of(), null, null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("難易度");
        verify(bookMapper, never()).insert(any());
    }

    @Test
    void updateClampsCurrentPageToTotalPages() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(180, 200, 3));
        when(bookMapper.update(any())).thenReturn(1);

        service.updateBook(guardian(), BOOK_ID, saveRequest(100, 180, 3));

        ArgumentCaptor<ReadingBookEntity> captor = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper).update(captor.capture());
        // 総ページ数を 100 に縮めたので 現在ページ も 100 に丸める
        assertThat(captor.getValue().getCurrentPage()).isEqualTo(100);
        assertThat(captor.getValue().getTotalPages()).isEqualTo(100);
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }

    @Test
    void updateConflictsWhenVersionIsStale() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 5));
        when(bookMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateBook(guardian(), BOOK_ID, saveRequest(100, 10, 4)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void togglesPinnedAndStatus() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 2));
        when(bookMapper.updatePinned(eq(BOOK_ID), eq(true), eq(ACCOUNT_ID), eq(2))).thenReturn(1);
        when(bookMapper.updateStatus(eq(BOOK_ID), eq("一時停止"), eq(ACCOUNT_ID), eq(2))).thenReturn(1);

        assertThat(service.setPinned(guardian(), BOOK_ID, true, null).message()).contains("置頂にしました");
        assertThat(service.setStatus(guardian(), BOOK_ID, "一時停止", 2).message()).contains("一時停止");
    }

    @Test
    void rejectsUnknownStatus() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 2));

        assertThatThrownBy(() -> service.setStatus(guardian(), BOOK_ID, "読んでる", null))
                .isInstanceOf(ValidationException.class);
        verify(bookMapper, never()).updateStatus(anyLong(), any(), any(), anyInt());
    }

    @Test
    void deleteFailsWhenBookIsMissing() {
        when(bookMapper.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteBook(guardian(), 404L))
                .isInstanceOf(NotFoundException.class);
        verify(bookMapper, never()).delete(anyLong());
    }

    // ------------------------------------------------------------ 読書記録

    @Test
    void recordUpdatesProgressAndAccumulatesMinutes() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(recordMapper.findById(anyLong())).thenReturn(new ReadingRecordEntity());

        service.createRecord(guardian(), BOOK_ID, new ReadingModels.RecordSaveRequest(
                null, 9, 15, 30, 2, "今日はここまで"));

        ArgumentCaptor<ReadingRecordEntity> record = ArgumentCaptor.forClass(ReadingRecordEntity.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getPageStart()).isEqualTo(9);
        assertThat(record.getValue().getPageEnd()).isEqualTo(15);
        assertThat(record.getValue().getMinutes()).isEqualTo(30);
        assertThat(record.getValue().getCreatedBy()).isEqualTo(ACCOUNT_ID);

        // 現在ページ = 終了ページ、ステータスは 読書中（総ページに未達）、累計は加算（SQL 側）
        verify(bookMapper).updateAfterRecord(eq(BOOK_ID), eq(15), eq("読書中"), eq(30), any(), eq(ACCOUNT_ID));
    }

    @Test
    void recordOnLastPageMarksBookAsFinished() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(90, 100, 1));
        when(recordMapper.findById(anyLong())).thenReturn(new ReadingRecordEntity());

        service.createRecord(guardian(), BOOK_ID, new ReadingModels.RecordSaveRequest(
                null, 90, 100, 20, 0, null));

        verify(bookMapper).updateAfterRecord(eq(BOOK_ID), eq(100), eq("読了"), eq(20), any(), eq(ACCOUNT_ID));
    }

    @Test
    void recordClampsPagesToTotalPages() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(recordMapper.findById(anyLong())).thenReturn(new ReadingRecordEntity());

        service.createRecord(guardian(), BOOK_ID, new ReadingModels.RecordSaveRequest(
                "2026-09-13T21:00:00+09:00", 5, 500, 10, 0, null));

        ArgumentCaptor<ReadingRecordEntity> record = ArgumentCaptor.forClass(ReadingRecordEntity.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getPageEnd()).isEqualTo(100);
        assertThat(record.getValue().getReadAt()).isNotNull();
    }

    @Test
    void recordUsesCurrentPageWhenPageIsOmitted() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(37, 100, 1));
        when(recordMapper.findById(anyLong())).thenReturn(new ReadingRecordEntity());

        service.createRecord(guardian(), BOOK_ID, new ReadingModels.RecordSaveRequest(
                null, null, null, null, null, null));

        ArgumentCaptor<ReadingRecordEntity> record = ArgumentCaptor.forClass(ReadingRecordEntity.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getPageEnd()).isEqualTo(37);
        assertThat(record.getValue().getPageStart()).isEqualTo(37);
        assertThat(record.getValue().getMinutes()).isZero();
    }

    /**
     * 読書履歴の絞り込み。日付は `LocalDate` に直して Mapper へ渡し、`dateTo` は
     * **翌日 0 時より前**（＝その日いっぱい）にする。
     */
    @Test
    void recordHistoryPassesDateAndPageFiltersToMapper() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(28, 159, 2));
        when(recordMapper.count(any(), any(), any(), any(), any())).thenReturn(0L);
        when(recordMapper.list(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.listRecords(guardian(), BOOK_ID, "2026-04-08", "2026-08-05", 28, 1, 20);

        verify(recordMapper).count(eq(BOOK_ID), eq(LocalDate.of(2026, 4, 8)), eq(LocalDate.of(2026, 8, 6)),
                eq(28), eq(FAMILY_STUDENT_ID));
        verify(recordMapper).list(eq(BOOK_ID), eq(LocalDate.of(2026, 4, 8)), eq(LocalDate.of(2026, 8, 6)),
                eq(28), eq(FAMILY_STUDENT_ID), eq(20), eq(0));
    }

    /** 片方だけの指定と、指定なし（既存の呼び方）も同じ経路で通る。 */
    @Test
    void recordHistoryAcceptsPartialFilters() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(28, 159, 2));
        when(recordMapper.count(any(), any(), any(), any(), any())).thenReturn(0L);
        when(recordMapper.list(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.listRecords(guardian(), BOOK_ID, "2026-04-08", null, null, 1, 20);
        verify(recordMapper).count(eq(BOOK_ID), eq(LocalDate.of(2026, 4, 8)), isNull(), isNull(),
                eq(FAMILY_STUDENT_ID));

        service.listRecords(guardian(), null, null, "2026-08-05", null, 1, 20);
        verify(recordMapper).count(isNull(), isNull(), eq(LocalDate.of(2026, 8, 6)), isNull(),
                eq(FAMILY_STUDENT_ID));

        service.listRecords(guardian(), null, null, null, null, 1, 20);
        verify(recordMapper).count(isNull(), isNull(), isNull(), isNull(), eq(FAMILY_STUDENT_ID));
    }

    /** 形式が不正な日付・1 未満のページ番号は 400 相当（Mapper は呼ばない）。 */
    @Test
    void recordHistoryRejectsInvalidFilters() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(28, 159, 2));
        assertThatThrownBy(() -> service.listRecords(guardian(), BOOK_ID, "2026-13-40", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("開始日");
        assertThatThrownBy(() -> service.listRecords(guardian(), BOOK_ID, "abc", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(() -> service.listRecords(guardian(), BOOK_ID, null, "2026/04/08", null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("終了日");
        assertThatThrownBy(() -> service.listRecords(guardian(), BOOK_ID, null, null, 0, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ページ番号");
        assertThatThrownBy(() -> service.listRecords(guardian(), BOOK_ID, null, null, -5, 1, 20))
                .isInstanceOf(ValidationException.class);

        verify(recordMapper, never()).count(any(), any(), any(), any(), any());
        verify(recordMapper, never()).list(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void deletingRecordRecalculatesBookTotals() {        ReadingRecordEntity record = new ReadingRecordEntity();
        record.setRecordId(9L);
        record.setBookId(BOOK_ID);
        when(recordMapper.findById(9L)).thenReturn(record);
        // 見える本の記録だけを消せる（見えない本は 404）
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(28, 159, 2));

        service.deleteRecord(guardian(), 9L);

        verify(recordMapper).delete(9L);
        verify(bookMapper).refreshRecordSummary(BOOK_ID, ACCOUNT_ID);
    }

    // ---------------------------------------------------------------- 標記

    @Test
    void addsVocabularyMarkAndRefreshesCount() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(markMapper.nextOrderNo(BOOK_ID, 28)).thenReturn(4);
        when(markMapper.findById(anyLong())).thenReturn(new ReadingMarkEntity());

        service.createMark(guardian(), BOOK_ID, new ReadingModels.MarkSaveRequest(
                28, "vocabulary", "plume", "プルーム", "#7a4de8",
                null, null, null, null, null));

        ArgumentCaptor<ReadingMarkEntity> mark = ArgumentCaptor.forClass(ReadingMarkEntity.class);
        verify(markMapper).insert(mark.capture());
        assertThat(mark.getValue().getOrderNo()).isEqualTo(4);
        assertThat(mark.getValue().getMarkType()).isEqualTo("vocabulary");
        verify(bookMapper).refreshMarkCount(BOOK_ID, ACCOUNT_ID);
    }

    @Test
    void rejectsMarkOutsideBookPages() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));

        assertThatThrownBy(() -> service.createMark(guardian(), BOOK_ID,
                new ReadingModels.MarkSaveRequest(101, "memo", "メモ", "本文", null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("総ページ数");
        verify(markMapper, never()).insert(any());
    }

    @Test
    void rejectsMarkWithoutContent() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));

        assertThatThrownBy(() -> service.createMark(guardian(), BOOK_ID,
                new ReadingModels.MarkSaveRequest(5, "memo", "  ", null, null, null, null, null, null, null)))
                .isInstanceOf(ValidationException.class);
    }

    /**
     * 2.1 の閲覧画面は本文 PDF を pdf.js で表示するので、手書き（pen）も受け付ける。
     * 点列は 描画データ（TEXT）に文字列のまま入り、座標も一緒に保存される。
     */
    @Test
    void acceptsPenMarkWithDrawingDataAndCoordinates() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(markMapper.nextOrderNo(BOOK_ID, 5)).thenReturn(1);
        when(markMapper.findById(anyLong())).thenReturn(new ReadingMarkEntity());

        service.createMark(guardian(), BOOK_ID, new ReadingModels.MarkSaveRequest(
                5, "pen", null, null, "#000000",
                new BigDecimal("0.100000"), new BigDecimal("0.200000"),
                new BigDecimal("0.300000"), new BigDecimal("0.400000"),
                "{\"kind\":\"pen\",\"points\":[{\"x\":0.1,\"y\":0.2}]}"));

        ArgumentCaptor<ReadingMarkEntity> mark = ArgumentCaptor.forClass(ReadingMarkEntity.class);
        verify(markMapper).insert(mark.capture());
        assertThat(mark.getValue().getMarkType()).isEqualTo("pen");
        assertThat(mark.getValue().getDrawingData()).contains("\"kind\":\"pen\"");
        assertThat(mark.getValue().getPositionX()).isEqualByComparingTo("0.100000");
    }

    /** 座標はページ内の正規化値（0〜1）。DDL の CHECK と同じ範囲をサービス側でも弾く。 */
    @Test
    void rejectsMarkCoordinateOutsidePage() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));

        assertThatThrownBy(() -> service.createMark(guardian(), BOOK_ID,
                new ReadingModels.MarkSaveRequest(5, "highlight", "本文", null, "#ffff00",
                        new BigDecimal("1.200000"), null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("位置X");
        verify(markMapper, never()).insert(any());
    }

    @Test
    void deletesMarkAndRefreshesCount() {
        ReadingMarkEntity mark = new ReadingMarkEntity();
        mark.setMarkId(3L);
        mark.setBookId(BOOK_ID);
        when(markMapper.findById(3L)).thenReturn(mark);
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));

        service.deleteMark(guardian(), 3L);

        verify(markMapper).delete(3L);
        verify(bookMapper).refreshMarkCount(BOOK_ID, ACCOUNT_ID);
    }

    @Test
    void resetMarksAndProgressClearsMarksRecordsAndProgress() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(markMapper.deleteByBook(BOOK_ID)).thenReturn(3);
        when(recordMapper.deleteByBook(BOOK_ID)).thenReturn(5);

        ReadingModels.BookMutationResult result = service.resetMarksAndProgress(guardian(), BOOK_ID);

        // 標記・読書記録の削除と進捗のリセットを 1 回の操作でまとめて行う（片方だけ残さない）
        verify(markMapper).deleteByBook(BOOK_ID);
        verify(recordMapper).deleteByBook(BOOK_ID);
        verify(bookMapper).refreshMarkCount(BOOK_ID, ACCOUNT_ID);
        verify(bookMapper).resetProgress(BOOK_ID, ACCOUNT_ID);
        assertThat(result.message()).contains("標記 3 件").contains("読書記録 5 件").contains("進捗");
    }

    @Test
    void resetMarksAndProgressWithoutMarksAndRecordsStillResetsProgress() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(markMapper.deleteByBook(BOOK_ID)).thenReturn(0);
        when(recordMapper.deleteByBook(BOOK_ID)).thenReturn(0);

        ReadingModels.BookMutationResult result = service.resetMarksAndProgress(guardian(), BOOK_ID);

        verify(recordMapper).deleteByBook(BOOK_ID);
        verify(bookMapper).resetProgress(BOOK_ID, ACCOUNT_ID);
        assertThat(result.message()).contains("標記 0 件").contains("読書記録 0 件");
    }

    // ------------------------------------- ロールと公開範囲（2026-09-14 の決定）

    /** 生徒は登録・修正・削除・置頂ができない（読むことと自分の本棚だけ。決定 Q9）。 */
    @Test
    void studentCannotManageBooks() {
        UserPrincipal student = student();
        when(bookMapper.findById(BOOK_ID)).thenReturn(globalBook());

        assertThatThrownBy(() -> service.createBook(student, saveRequest(100, null, null)))
                .isInstanceOf(ReadingAccessException.class)
                .satisfies(ex -> assertThat(((ReadingAccessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateBook(student, BOOK_ID, saveRequest(100, 10, 1)))
                .isInstanceOf(ReadingAccessException.class);
        assertThatThrownBy(() -> service.deleteBook(student, BOOK_ID))
                .isInstanceOf(ReadingAccessException.class);
        assertThatThrownBy(() -> service.setPinned(student, BOOK_ID, true, null))
                .isInstanceOf(ReadingAccessException.class);
        assertThatThrownBy(() -> service.createCategory(student,
                new ReadingModels.CategorySaveRequest("分類", null, null)))
                .isInstanceOf(ReadingAccessException.class);
        verify(bookMapper, never()).insert(any());
        verify(bookMapper, never()).delete(anyLong());
    }

    /** 保護者は全体書籍を直せない（読めるだけ）。家庭の本（自分の家庭）だけが直せる。 */
    @Test
    void guardianCannotEditGlobalBooks() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(globalBook());

        assertThatThrownBy(() -> service.deleteBook(guardian(), BOOK_ID))
                .isInstanceOf(ReadingAccessException.class)
                .hasMessageContaining("自分の家庭");
        assertThatThrownBy(() -> service.setPinned(guardian(), BOOK_ID, true, null))
                .isInstanceOf(ReadingAccessException.class);
        verify(bookMapper, never()).delete(anyLong());
    }

    /** 他家庭の本は「無い」ものとして扱う（404。存在を漏らさない）。 */
    @Test
    void otherFamilyBookIsInvisible() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(otherFamilyBook());

        assertThatThrownBy(() -> service.detail(guardian(), BOOK_ID, null, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateBook(guardian(), BOOK_ID, saveRequest(100, 10, 1)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.addToShelf(guardian(), BOOK_ID))
                .isInstanceOf(NotFoundException.class);
    }

    /** 管理者は全体書籍だけを扱う（家庭の本は見えない＝404、家庭の本は作れない＝403）。 */
    @Test
    void adminOnlySeesGlobalBooks() {
        when(bookMapper.findByNo(any())).thenReturn(null);
        ReadingBookEntity global = globalBook();
        when(bookMapper.findById(anyLong())).thenReturn(global);

        ReadingModels.BookMutationResult created =
                service.createBook(admin(), saveRequest(100, null, null));
        assertThat(created.book().scope()).isEqualTo("GLOBAL");
        assertThat(created.book().ownerFamilyId()).isNull();

        when(bookMapper.findById(BOOK_ID)).thenReturn(otherFamilyBook());
        assertThatThrownBy(() -> service.detail(admin(), BOOK_ID, null, null))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> service.createBook(admin(), new ReadingModels.BookSaveRequest(
                "家庭の本", "著者", "Starter", "未着手", 10, null, false, List.of(), null, null, null, null,
                null, "FAMILY")))
                .isInstanceOf(ReadingAccessException.class);
    }

    /** 一覧はロールに応じた家庭（管理者は null）を Mapper へ渡す。 */
    @Test
    void searchPassesRoleScopeToMapper() {
        when(bookMapper.totals(any(), any(), any(), anyBoolean())).thenReturn(new ReadingShelfTotals());
        when(bookMapper.count(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(0L);
        when(bookMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(List.of());
        when(fileMapper.findByKind(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of());

        service.searchBooks(admin(), null, null, null, null, null, null, null, "GLOBAL", null, 1, 20);

        verify(bookMapper).count(any(), any(), any(), any(), any(), any(), any(), eq(ADMIN_ACCOUNT_ID),
                isNull(), eq("GLOBAL"), eq(false));
    }

    /** 見えない本は【自分の本棚】にも入れられない（404）。 */
    @Test
    void cannotShelfInvisibleBook() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(otherFamilyBook());

        assertThatThrownBy(() -> service.addToShelf(guardian(), BOOK_ID))
                .isInstanceOf(NotFoundException.class);
        verify(bookMapper, never()).insertShelf(anyLong(), anyLong(), any());
    }

    /**
     * 【本棚に入れる/外す】は**自分の行だけ**を触る（他人のアカウントID は受け取らない）。
     * 入れる・外すは同じ呼び方で何度でも通る（冪等）。
     */
    @Test
    void shelfAddAndRemoveTouchOnlyMyRow() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));

        ReadingModels.BookMutationResult added = service.addToShelf(guardian(), BOOK_ID);
        assertThat(added.book().inMyShelf()).isTrue();
        assertThat(added.message()).contains("本棚に入れました");
        verify(bookMapper).insertShelf(BOOK_ID, ACCOUNT_ID, ACCOUNT_ID);

        // 冪等（すでに入っていても同じ呼び方で 200。行は増えない＝ON CONFLICT DO NOTHING）
        assertThat(service.addToShelf(guardian(), BOOK_ID).book().inMyShelf()).isTrue();
        verify(bookMapper, times(2)).insertShelf(BOOK_ID, ACCOUNT_ID, ACCOUNT_ID);

        ReadingModels.BookMutationResult removed = service.removeFromShelf(guardian(), BOOK_ID);
        assertThat(removed.book().inMyShelf()).isFalse();
        assertThat(removed.message()).contains("本棚から外しました");
        verify(bookMapper).deleteShelf(BOOK_ID, ACCOUNT_ID);
    }

    // ------------------------------------------------------------ 一覧・詳細

    @Test
    void searchValidatesAndClampsPaging() {
        when(bookMapper.totals(any(), any(), any(), anyBoolean())).thenReturn(new ReadingShelfTotals());
        when(bookMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(List.of(book(1, 10, 1)));
        when(bookMapper.count(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(1L);

        ReadingModels.BookListResult result = service.searchBooks(guardian(), null, null, "読書中", null, null, null, null, null, null, 0, 9999);

        assertThat(result.size()).isEqualTo(ReadingModels.MAX_SIZE);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).readPercent()).isEqualTo(10);
        assertThat(result.items().get(0).tags()).containsExactly("Novel", "Classic");
    }

    @Test
    void searchRejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> service.searchBooks(guardian(), null, null, "よんだ", null, null, null, null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
    }

    /**
     * 本棚のサマリ。`pdfCount` は「行があり、実体もストレージにある冊数」なので、
     * 移行データ（行はあるが実体が無い）は数えない。
     */
    @Test
    void shelfTotalsCountsAvailablePdfsAndUncategorized() throws Exception {
        ReadingShelfTotals totals = new ReadingShelfTotals();
        totals.setBookCount(6L);
        totals.setUncategorizedCount(2L);
        when(bookMapper.totals(any(), any(), any(), anyBoolean())).thenReturn(totals);
        when(bookMapper.count(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(0L);
        when(bookMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(List.of());

        // 6 冊ぶんの PDF 行（実体があるのは 2 冊だけ）
        ReadingFileEntity withFile = fileEntity(1L, "PDF", "ER-1-aaaaaaaa.pdf", "books/ER-1/");
        Path physical = storageRoot.resolve(withFile.getStoredPath()).resolve(withFile.getStoredName());
        Files.createDirectories(physical.getParent());
        Files.writeString(physical, "%PDF");
        ReadingFileEntity withoutFile = fileEntity(2L, "PDF", "20260402-124745-x.pdf",
                "file/ENGLISH_READING/202604/");
        when(fileMapper.findByKind(eq("PDF"), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(withFile, withoutFile));

        ReadingModels.ShelfTotals shelf = service.searchBooks(guardian(), null, null, null, null, null, null, null, null, null, 1, 20).totals();

        assertThat(shelf.bookCount()).isEqualTo(6);
        assertThat(shelf.uncategorizedCount()).isEqualTo(2);
        assertThat(shelf.pdfCount()).as("実体がある 1 冊だけ数える").isEqualTo(1);
    }

    // ---------------------------------------------------------------- 言語

    /**
     * 言語（中国語 / 英語 / 日本語）。省略した新規登録は '英語'、
     * 知らない値は 400（既存の難易度・ステータスと同じ作り）。
     */
    @Test
    void validatesAndDefaultsLanguage() {
        when(bookMapper.findByNo(any())).thenReturn(null);
        when(bookMapper.findById(anyLong())).thenReturn(book(1, 100, 1));

        service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 10, null, false, List.of(), null, null, null, null, null));
        ArgumentCaptor<ReadingBookEntity> created = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper).insert(created.capture());
        assertThat(created.getValue().getLanguage()).as("省略時は既定の英語").isEqualTo("英語");

        service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "射鵰英雄伝", "金庸", "Upper", "未着手", 500, null, false, List.of(), null, null, null, null, "中国語"));
        ArgumentCaptor<ReadingBookEntity> chinese = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper, times(2)).insert(chinese.capture());
        assertThat(chinese.getValue().getLanguage()).isEqualTo("中国語");

        assertThatThrownBy(() -> service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 10, null, false, List.of(), null, null, null, null, "フランス語")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("言語");
        verify(bookMapper, times(2)).insert(any());
    }

    /** 修正で言語を送らなければ今の値のまま。 */
    @Test
    void updateKeepsLanguageWhenOmitted() {
        ReadingBookEntity current = book(10, 100, 2);
        current.setLanguage("中国語");
        when(bookMapper.findById(BOOK_ID)).thenReturn(current);
        when(bookMapper.update(any())).thenReturn(1);

        service.updateBook(guardian(), BOOK_ID, new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 100, 10, false, List.of(), null, null, 2, null, null));

        ArgumentCaptor<ReadingBookEntity> updated = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper).update(updated.capture());
        assertThat(updated.getValue().getLanguage()).isEqualTo("中国語");

        service.updateBook(guardian(), BOOK_ID, new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 100, 10, false, List.of(), null, null, 2, null, "日本語"));
        ArgumentCaptor<ReadingBookEntity> changed = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper, times(2)).update(changed.capture());
        assertThat(changed.getValue().getLanguage()).isEqualTo("日本語");
    }

    /** 一覧の言語絞り込みも難易度・ステータスと同じ検証を通り、Mapper へそのまま渡る。 */
    @Test
    void searchFiltersByLanguageAndRejectsUnknownValue() {
        when(bookMapper.totals(any(), any(), any(), anyBoolean())).thenReturn(new ReadingShelfTotals());
        when(bookMapper.count(any(), any(), any(), any(), any(), any(), eq("中国語"), any(), any(), any(),
                anyBoolean())).thenReturn(1L);
        when(bookMapper.search(any(), any(), any(), any(), any(), any(), eq("中国語"), any(), any(), any(),
                anyBoolean(), anyInt(), anyInt())).thenReturn(List.of(book(1, 10, 1)));

        ReadingModels.BookListResult result =
                service.searchBooks(guardian(), null, null, null, null, null, null, "中国語", null, null, 1, 20);

        assertThat(result.items()).hasSize(1);
        verify(bookMapper).count(any(), any(), any(), any(), any(), any(), eq("中国語"), any(), any(), any(),
                anyBoolean());

        assertThatThrownBy(() -> service.searchBooks(guardian(), null, null, null, null, null, null, "フランス語", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("言語");
    }

    @Test
    void detailReturnsBookRecordsAndMarks() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(28, 159, 2));
        when(recordMapper.list(eq(BOOK_ID), isNull(), isNull(), isNull(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new ReadingRecordEntity()));
        when(markMapper.list(eq(BOOK_ID), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(new ReadingMarkEntity()));
        when(markMapper.markedPages(BOOK_ID)).thenReturn(List.of(24, 25, 28));

        ReadingModels.BookDetail detail = service.detail(guardian(), BOOK_ID, null, null);

        assertThat(detail.book().title()).isEqualTo("The Secret Garden");
        assertThat(detail.records()).hasSize(1);
        assertThat(detail.marks()).hasSize(1);
        assertThat(detail.markedPages()).containsExactly(24, 25, 28);
    }

    @Test
    void detailFailsWhenBookIsMissing() {
        when(bookMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(guardian(), 999L, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMarksReportsCountsByType() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(markMapper.count(eq(BOOK_ID), any(), any())).thenReturn(0L);
        when(markMapper.count(BOOK_ID, null, "vocabulary")).thenReturn(3L);
        when(markMapper.count(BOOK_ID, null, "highlight")).thenReturn(1L);
        when(markMapper.count(BOOK_ID, null, "memo")).thenReturn(2L);
        when(markMapper.count(BOOK_ID, null, "underline")).thenReturn(0L);

        ReadingModels.MarkListResult result = service.listMarks(guardian(), BOOK_ID, 5, "vocabulary", 1, 50);

        assertThat(result.vocabularyCount()).isEqualTo(3);
        assertThat(result.highlightCount()).isEqualTo(1);
        assertThat(result.memoCount()).isEqualTo(2);
    }

    @Test
    void timestampFallsBackToNowWhenUnreadable() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(10, 100, 1));
        when(recordMapper.findById(anyLong())).thenReturn(new ReadingRecordEntity());

        service.createRecord(guardian(), BOOK_ID, new ReadingModels.RecordSaveRequest(
                "きのう", 1, 1, 5, 0, null));

        ArgumentCaptor<ReadingRecordEntity> record = ArgumentCaptor.forClass(ReadingRecordEntity.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getReadAt())
                .isAfter(Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));
    }

    // ------------------------------------------------------------ 分類（マスタ）

    private ReadingCategoryEntity category(long id, String name, int order) {
        ReadingCategoryEntity entity = new ReadingCategoryEntity();
        entity.setCategoryId(id);
        entity.setName(name);
        entity.setDisplayOrder(order);
        // 2026-09-14 の追加: 分類は家庭ごと。この土台は保護者の家庭の分類を作る
        entity.setOwnerFamilyId(FAMILY_STUDENT_ID);
        return entity;
    }

    /** 登録は前後の空白を落として名前を付け、表示順を省略したら末尾に置く。 */
    @Test
    void createsCategoryAtEndWithTrimmedName() {
        when(categoryMapper.findByName("英語 小説", null, FAMILY_STUDENT_ID)).thenReturn(null);
        when(categoryMapper.nextDisplayOrder(FAMILY_STUDENT_ID)).thenReturn(4);
        when(categoryMapper.findById(CATEGORY_ID, FAMILY_STUDENT_ID))
                .thenReturn(category(CATEGORY_ID, "英語 小説", 4));

        ReadingModels.CategoryMutationResult result = service.createCategory(guardian(),
                new ReadingModels.CategorySaveRequest("  英語 小説  ", null, " 物語 "));

        ArgumentCaptor<ReadingCategoryEntity> captor = ArgumentCaptor.forClass(ReadingCategoryEntity.class);
        verify(categoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("英語 小説");
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(4);
        assertThat(captor.getValue().getDescription()).isEqualTo("物語");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACCOUNT_ID);
        assertThat(result.category().name()).isEqualTo("英語 小説");
        assertThat(result.message()).contains("英語 小説");
    }

    /** 名前の重複は 400（画面が「同じ名前があります」を出せるように）。 */
    @Test
    void rejectsDuplicateCategoryName() {
        when(categoryMapper.findByName("中国語", null, FAMILY_STUDENT_ID))
                .thenReturn(category(3L, "中国語", 3));

        assertThatThrownBy(() -> service.createCategory(guardian(),
                new ReadingModels.CategorySaveRequest("中国語", null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("中国語");
        verify(categoryMapper, never()).insert(any());
    }

    /** 空白だけの名前・51 文字の名前は 400（DDL は NOT NULL + 50 文字）。 */
    @Test
    void rejectsBlankOrTooLongCategoryName() {
        assertThatThrownBy(() -> service.createCategory(guardian(),
                new ReadingModels.CategorySaveRequest("   ", null, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createCategory(guardian(),
                new ReadingModels.CategorySaveRequest("あ".repeat(51), null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("50文字");
        verify(categoryMapper, never()).insert(any());
    }

    /** 改名・並べ替え（PUT）は同じ行を更新する。自分自身の名前は重複と見なさない。 */
    @Test
    void renamesAndReordersCategory() {
        when(categoryMapper.findById(5L, FAMILY_STUDENT_ID)).thenReturn(category(5L, "英語 小説", 1));
        when(categoryMapper.findByName("英語 物語", 5L, FAMILY_STUDENT_ID)).thenReturn(null);
        when(categoryMapper.findById(5L, FAMILY_STUDENT_ID)).thenReturn(category(5L, "英語 物語", 3));

        ReadingModels.CategoryMutationResult result = service.updateCategory(guardian(), 5L,
                new ReadingModels.CategorySaveRequest("英語 物語", 3, "説明"));

        ArgumentCaptor<ReadingCategoryEntity> captor = ArgumentCaptor.forClass(ReadingCategoryEntity.class);
        verify(categoryMapper).update(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(5L);
        assertThat(captor.getValue().getName()).isEqualTo("英語 物語");
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(3);
        assertThat(result.category().displayOrder()).isEqualTo(3);
    }

    @Test
    void rejectsCategoryUpdateWhenMissing() {
        when(categoryMapper.findById(404L, FAMILY_STUDENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.updateCategory(guardian(), 404L,
                new ReadingModels.CategorySaveRequest("名", 0, null)))
                .isInstanceOf(NotFoundException.class);
    }

    /** 削除しても本は消えず、冊数をメッセージに出す（未分類に戻る）。 */
    @Test
    void deletingCategoryKeepsBooksAndReportsCount() {
        when(categoryMapper.findById(1L, FAMILY_STUDENT_ID)).thenReturn(category(1L, "英語 小説", 1));
        when(categoryMapper.countBooks(1L, FAMILY_STUDENT_ID)).thenReturn(3L);

        ReadingModels.SimpleResult result = service.deleteCategory(guardian(), 1L);

        verify(categoryMapper).delete(1L);
        verify(bookMapper, never()).delete(anyLong());
        assertThat(result.count()).isEqualTo(3);
        assertThat(result.message()).contains("3 冊は未分類になりました");
    }

    @Test
    void deletingEmptyCategoryDoesNotMentionBooks() {
        when(categoryMapper.findById(2L, FAMILY_STUDENT_ID)).thenReturn(category(2L, "中国語", 2));
        when(categoryMapper.countBooks(2L, FAMILY_STUDENT_ID)).thenReturn(0L);

        ReadingModels.SimpleResult result = service.deleteCategory(guardian(), 2L);

        assertThat(result.count()).isZero();
        assertThat(result.message()).doesNotContain("未分類");
    }

    /** 書籍の分類ID は存在するものだけ。0 や未登録の ID は 400。 */
    @Test
    void rejectsUnknownCategoryOnBookSave() {
        when(categoryMapper.findById(99L, FAMILY_STUDENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 10, null, false, List.of(), null, null, null, 99L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("分類");
        assertThatThrownBy(() -> service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "本", "著者", "Starter", "未着手", 10, null, false, List.of(), null, null, null, 0L, null)))
                .isInstanceOf(ValidationException.class);
        verify(bookMapper, never()).insert(any());
    }

    @Test
    void savesBookWithCategory() {
        when(categoryMapper.findById(3L, FAMILY_STUDENT_ID)).thenReturn(category(3L, "中国語", 3));
        when(bookMapper.findByNo(any())).thenReturn(null);
        when(bookMapper.findById(anyLong())).thenReturn(book(1, 100, 1));

        service.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "射鵰英雄伝", "金庸", "Upper", "未着手", 500, null, false, List.of(), null, null, null, 3L, null));

        ArgumentCaptor<ReadingBookEntity> captor = ArgumentCaptor.forClass(ReadingBookEntity.class);
        verify(bookMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(3L);
    }

    // ------------------------------------------------------ 本文 PDF・表紙画像

    private ReadingFileEntity fileEntity(long fileId, String kind, String storedName, String storedPath) {
        ReadingFileEntity entity = new ReadingFileEntity();
        entity.setFileId(fileId);
        entity.setBookId(BOOK_ID);
        entity.setFileKind(kind);
        entity.setStoredName(storedName);
        entity.setStoredPath(storedPath);
        entity.setOriginalName("original.pdf");
        return entity;
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4 test".getBytes());
    }

    @Test
    void uploadsPdfIntoBookFolderAndUpdatesTotalPages() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(300, 400, 1));
        when(fileMapper.find(BOOK_ID, "PDF")).thenReturn(null);

        service.uploadPdf(guardian(), BOOK_ID, pdf("Harry Potter.pdf"), 272);

        ArgumentCaptor<ReadingFileEntity> captor = ArgumentCaptor.forClass(ReadingFileEntity.class);
        verify(fileMapper).insert(captor.capture());
        ReadingFileEntity saved = captor.getValue();
        assertThat(saved.getStoredPath()).isEqualTo("books/ER-20260402-124745/");
        assertThat(saved.getStoredName()).startsWith("ER-20260402-124745-").endsWith(".pdf");
        assertThat(saved.getMimeType()).isEqualTo("application/pdf");
        assertThat(saved.getOriginalName()).isEqualTo("Harry Potter.pdf");
        assertThat(saved.getPageCount()).isEqualTo(272);
        assertThat(Files.isRegularFile(storageRoot.resolve(saved.getStoredPath()).resolve(saved.getStoredName())))
                .isTrue();
        // 画面（pdf.js）が数えた総ページ数を反映し、現在ページは丸められる（SQL 側で LEAST）
        verify(bookMapper).updateTotalPages(BOOK_ID, 272, ACCOUNT_ID);
    }

    /** 差し替えは同じ行を更新し、古い実体を消す（実体を消してから入れ替える）。 */
    @Test
    void replacingPdfUpdatesRowAndDeletesOldFile() throws Exception {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));
        ReadingFileEntity existing = fileEntity(9L, "PDF", "ER-20260402-124745-aaaaaaaa.pdf",
                "books/ER-20260402-124745/");
        when(fileMapper.find(BOOK_ID, "PDF")).thenReturn(existing);
        Path old = storageRoot.resolve(existing.getStoredPath()).resolve(existing.getStoredName());
        Files.createDirectories(old.getParent());
        Files.writeString(old, "old");

        ReadingModels.BookMutationResult result = service.uploadPdf(guardian(), BOOK_ID, pdf("new.pdf"), null);

        verify(fileMapper).update(existing);
        verify(fileMapper, never()).insert(any());
        assertThat(Files.exists(old)).as("古い実体は残さない").isFalse();
        assertThat(result.message()).contains("差し替え");
    }

    @Test
    void rejectsNonPdfUpload() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));

        // 拡張子が PDF でない
        assertThatThrownBy(() -> service.uploadPdf(guardian(), BOOK_ID,
                new MockMultipartFile("file", "scan.png", "application/pdf", "x".getBytes()), null))
                .isInstanceOf(ReadingApiException.class);
        // 拡張子は PDF だが Content-Type が違う
        assertThatThrownBy(() -> service.uploadPdf(guardian(), BOOK_ID,
                new MockMultipartFile("file", "scan.pdf", "image/png", "x".getBytes()), null))
                .isInstanceOf(ReadingApiException.class);
        verify(fileMapper, never()).insert(any());
    }

    @Test
    void deletesPdfRowAndPhysicalFile() throws Exception {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));
        ReadingFileEntity existing = fileEntity(9L, "PDF", "ER-20260402-124745-aaaaaaaa.pdf",
                "books/ER-20260402-124745/");
        when(fileMapper.find(BOOK_ID, "PDF")).thenReturn(existing);
        Path file = storageRoot.resolve(existing.getStoredPath()).resolve(existing.getStoredName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pdf");

        ReadingModels.SimpleResult result = service.deletePdf(guardian(), BOOK_ID);

        verify(fileMapper).delete(9L);
        assertThat(Files.exists(file)).isFalse();
        assertThat(result.count()).isEqualTo(1);
    }

    /** 行が無い・実体が無いときは 404（画面は「PDF 未登録」を出す）。 */
    @Test
    void downloadFailsWhenPdfIsNotRegistered() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));
        when(fileMapper.find(BOOK_ID, "PDF")).thenReturn(null);

        assertThatThrownBy(() -> service.downloadPdf(guardian(), BOOK_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("登録されていません");

        // 2.0 から引き継いだ行（実体は 2.0 サーバにあり未取得）
        ReadingFileEntity migrated = fileEntity(11L, "PDF", "20260402-124745-x.pdf",
                "file/ENGLISH_READING/202604/");
        when(fileMapper.find(BOOK_ID, "PDF")).thenReturn(migrated);
        assertThatThrownBy(() -> service.downloadPdf(guardian(), BOOK_ID))
                .isInstanceOf(ReadingApiException.class)
                .satisfies(ex -> assertThat(((ReadingApiException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** 書籍を消すときは実体ファイルも消す（legacy ルートの実体は消さない）。 */
    @Test
    void deletingBookRemovesStoredFiles() throws Exception {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));
        ReadingFileEntity stored = fileEntity(9L, "PDF", "ER-20260402-124745-aaaaaaaa.pdf",
                "books/ER-20260402-124745/");
        ReadingFileEntity legacy = fileEntity(10L, "COVER", "legacy-cover.png",
                "file/ENGLISH_READING/202604/");
        when(fileMapper.findByBook(BOOK_ID)).thenReturn(List.of(stored, legacy));
        Path file = storageRoot.resolve(stored.getStoredPath()).resolve(stored.getStoredName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, "pdf");

        service.deleteBook(guardian(), BOOK_ID);

        verify(bookMapper).delete(BOOK_ID);
        assertThat(Files.exists(file)).as("2.1 が保存した実体は消す").isFalse();
    }

    /** 表紙は画像のみ。PDF は弾く。 */
    @Test
    void rejectsPdfAsCover() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));

        assertThatThrownBy(() -> service.uploadCover(guardian(), BOOK_ID, pdf("cover.pdf")))
                .isInstanceOf(ReadingApiException.class);
        verify(fileMapper, never()).insert(any());
    }

    @Test
    void uploadsCoverImage() {
        when(bookMapper.findById(BOOK_ID)).thenReturn(book(1, 100, 1));
        when(fileMapper.find(BOOK_ID, "COVER")).thenReturn(null);

        service.uploadCover(guardian(), BOOK_ID,
                new MockMultipartFile("file", "cover.png", "image/png", "png".getBytes()));

        ArgumentCaptor<ReadingFileEntity> captor = ArgumentCaptor.forClass(ReadingFileEntity.class);
        verify(fileMapper).insert(captor.capture());
        assertThat(captor.getValue().getFileKind()).isEqualTo("COVER");
        assertThat(captor.getValue().getStoredName()).endsWith(".png");
        assertThat(captor.getValue().getMimeType()).isEqualTo("image/png");
    }
}
