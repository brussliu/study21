package com.study21.user.reading;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する読書管理の検証。
 *
 * 書籍管理・書籍閲覧が使う経路（サービス層）で、
 * 「2.0 から移行した本棚が読める」「登録 → 読書記録 → 標記 → 削除」が通ることを確かめる。
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ReadingRepositoryTest {

    @Autowired
    private ReadingService readingService;

    /** 移行したファイル行（実体は 2.0 側で未取得）を直接確かめるために使う */
    @Autowired
    private ReadingFileMapper fileMapper;

    /** 総ページ数の更新（PDF を上げたとき）を確かめるために使う */
    @Autowired
    private ReadingBookMapper bookMapper;

    /** 語彙辞書キャッシュの upsert を確かめるために使う */
    @Autowired
    private ReadingDictionaryMapper dictionaryMapper;

    /**
     * 本を登録・修正する人（保護者）。家庭（＝自分の生徒）は `ACC_アカウント` の
     * 保護者—生徒の紐付けから解決される（アカウントID 2 の生徒が家族学生ID）。
     */
    private UserPrincipal guardian() {
        return new UserPrincipal(1L, "bruss.ji.liu@gmail.com", "試験 保護者", AccountType.GUARDIAN);
    }

    /** 読むだけの人（生徒）。家庭は自分のアカウント。 */
    private UserPrincipal student() {
        return new UserPrincipal(2L, "ricky.jingze@gmail.com", "試験 生徒", AccountType.STUDENT);
    }

    /**
     * **別の家庭**の生徒（アカウントID 4 = `teststudent@gmail.com` ／ 保護者は 3）。
     * 他家庭の分離（自分の家庭の本が見えないこと）を確かめるために使う。
     */
    private UserPrincipal otherFamilyStudent() {
        return new UserPrincipal(4L, "teststudent@gmail.com", "試験 生徒2", AccountType.STUDENT);
    }

    /** **別の家庭**の保護者（アカウントID 3 ／ 生徒は 4）。家庭ごとの分類の検査に使う。 */
    private UserPrincipal otherGuardian() {
        return new UserPrincipal(3L, "testparent@gmail.com", "試験 保護者2", AccountType.GUARDIAN);
    }

    /** 管理者（全体書籍だけが見える。家庭を持たない）。 */
    private UserPrincipal admin() {
        return new UserPrincipal(1L, "admin@study21.example", "システム 管理者", AccountType.ADMIN);
    }

    /** その人が見える本の ID（一覧 1 ページぶん）。 */
    private java.util.Set<Long> visibleBookIds(UserPrincipal user) {
        return readingService.searchBooks(user, null, null, null, null, null, null, null, null, null, 1, 100)
                .items().stream().map(ReadingModels.BookRow::bookId).collect(Collectors.toSet());
    }

    /** その人が見える本のうち、公開範囲で絞った ID（GLOBAL / FAMILY）。 */
    private java.util.Set<Long> visibleBookIdsOfScope(UserPrincipal user, String scope) {
        return readingService.searchBooks(user, null, null, null, null, null, null, null, scope, null, 1, 200)
                .items().stream().map(ReadingModels.BookRow::bookId).collect(Collectors.toSet());
    }

    /** その人の【自分の本棚】の本の ID。 */
    private java.util.Set<Long> myShelfIds(UserPrincipal user) {
        return readingService.searchBooks(user, null, null, null, null, null, null, null, null, "MINE", 1, 200)
                .items().stream().map(ReadingModels.BookRow::bookId).collect(Collectors.toSet());
    }

    @Test
    void migratedShelfIsReadable() {
        ReadingModels.BookListResult shelf = readingService.searchBooks(guardian(), null, null, null, null, null, null, null, null, null, 1, 100);

        // 2.0 から移行した 6 冊（置頂の 2 冊が先頭）
        assertThat(shelf.items()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(shelf.totals().bookCount()).isGreaterThanOrEqualTo(6);
        assertThat(shelf.totals().markCount()).isGreaterThanOrEqualTo(800);
        assertThat(shelf.items().get(0).pinned()).isTrue();
        assertThat(shelf.items()).allSatisfy(item -> {
            assertThat(item.bookNo()).startsWith("ER-");
            assertThat(item.title()).isNotBlank();
            assertThat(item.author()).isNotBlank();
            assertThat(item.totalPages()).isGreaterThanOrEqualTo(1);
            assertThat(item.currentPage()).isBetween(1, item.totalPages());
            assertThat(item.readPercent()).isBetween(0, 100);
        });

        // 移行した 1 冊（Harry Potter）の中身が見える
        ReadingModels.BookRow harry = shelf.items().stream()
                .filter(item -> item.title().contains("Harry Potter and the Sorcerers Stone"))
                .findFirst()
                .orElseThrow();
        assertThat(harry.difficulty()).isEqualTo("Starter");
        assertThat(harry.tags()).contains("Novel");
        assertThat(harry.totalPages()).isEqualTo(272);

        ReadingModels.BookDetail detail = readingService.detail(guardian(), harry.bookId(), null, null);
        assertThat(detail.book().title()).isEqualTo(harry.title());
        assertThat(detail.records()).isNotEmpty();
        assertThat(detail.marks()).isNotEmpty();
        assertThat(detail.markedPages()).isNotEmpty();
        assertThat(detail.marks()).allSatisfy(mark -> {
            assertThat(mark.pageNo()).isGreaterThanOrEqualTo(1);
            assertThat(mark.markType()).isIn(ReadingModels.MARK_TYPES);
        });
    }

    @Test
    void migratedVocabularyCanBeFilteredByType() {
        ReadingModels.BookRow target = readingService.searchBooks(guardian(), "Raya", null, null, null, null, null, null, null, null, 1, 10)
                .items().stream().findFirst().orElseThrow();

        ReadingModels.MarkListResult vocabulary = readingService.listMarks(guardian(), target.bookId(), null, "vocabulary", 1, 100);

        assertThat(vocabulary.totalElements()).isGreaterThan(0);
        assertThat(vocabulary.vocabularyCount()).isGreaterThan(0);
        assertThat(vocabulary.items()).allSatisfy(mark -> {
            assertThat(mark.markType()).isEqualTo("vocabulary");
            assertThat(mark.targetText()).isNotBlank();
        });
    }

    @Test
    void createRecordMarkAndDeleteBook() {
        UserPrincipal user = guardian();
        long created = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "E2E Repository Test", "Test Author", "Starter", "未着手", 20, null, false,
                List.of("Test"), "テスト用", null, null, null, null)).book().bookId();

        ReadingModels.BookRow book = readingService.detail(guardian(), created, null, null).book();
        assertThat(book.bookNo()).startsWith("ER-");
        assertThat(book.status()).isEqualTo("未着手");
        assertThat(book.currentPage()).isEqualTo(1);
        assertThat(book.tags()).containsExactly("Test");

        // 読書記録（1 → 10 ページ・25 分）
        ReadingModels.RecordMutationResult recorded = readingService.createRecord(user, created,
                new ReadingModels.RecordSaveRequest(null, 1, 10, 25, 0, "テスト"));
        assertThat(recorded.book().currentPage()).isEqualTo(10);
        assertThat(recorded.book().status()).isEqualTo("読書中");
        assertThat(recorded.book().recentMinutes()).isEqualTo(25);
        assertThat(recorded.book().totalMinutes()).isEqualTo(25);
        assertThat(recorded.book().lastReadAt()).isNotNull();

        // 標記（語彙）
        ReadingModels.MarkMutationResult marked = readingService.createMark(user, created,
                new ReadingModels.MarkSaveRequest(9, "vocabulary", "intricate", "複雑な", "#7a4de8",
                        null, null, null, null, null));
        assertThat(marked.book().markCount()).isEqualTo(1);
        assertThat(readingService.listMarks(guardian(), created, 9, null, 1, 50).totalElements()).isEqualTo(1);

        // 最後のページまで読むと 読了
        ReadingModels.RecordMutationResult finished = readingService.createRecord(user, created,
                new ReadingModels.RecordSaveRequest(null, 10, 20, 15, 0, null));
        assertThat(finished.book().status()).isEqualTo("読了");
        assertThat(finished.book().totalMinutes()).isEqualTo(40);

        // 標記の削除で累計標記件数が戻る
        readingService.deleteMark(user, marked.mark().markId());
        assertThat(readingService.detail(guardian(), created, null, null).book().markCount()).isZero();

        // 楽観的ロック: 古いバージョンでの更新は 409
        assertThatThrownBy(() -> readingService.updateBook(user, created, new ReadingModels.BookSaveRequest(
                "E2E Repository Test", "Test Author", "Starter", "読了", 20, 20, false, List.of(), null, null, 1,
                null, null)))
                .isInstanceOf(ConflictException.class);

        // 履歴を消すと累計が計算し直される
        readingService.deleteRecord(user, recorded.record().recordId());
        ReadingModels.BookRow afterDelete = readingService.detail(guardian(), created, null, null).book();
        assertThat(afterDelete.totalMinutes()).isEqualTo(15);

        // 書籍を削除すると記録・標記も消える
        readingService.deleteBook(user, created);
        assertThatThrownBy(() -> readingService.detail(guardian(), created, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------ 標記クリア

    /**
     * 画面の【標記クリア】（`DELETE /books/{bookId}/marks`）が、
     * **標記・読書記録（履歴）の全削除と読書の進捗のリセットを 1 回の操作でまとめて**行うことを
     * 実 DB で確かめる（2026-09-14 の指示で読書記録も消すことにした）。
     *
     * <p>記録も消すので、リセット後の累計読書時間・最終読書日時と残った記録が食い違うことはない。
     * 進捗（％）は 現在ページ / 総ページ から導出するので、現在ページを 1 に戻すと
     * 10 ページの本では 10% になる（0 にはならない）。</p>
     */
    @Test
    void resetMarksAndProgressClearsMarksRecordsAndProgress() {
        UserPrincipal user = guardian();
        long bookId = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "標記クリアのテスト", "Test Author", "Starter", "未着手", 10, null, false,
                List.of(), null, null, null, null, null)).book().bookId();

        // 進捗を作る（1 → 4 ページ・25 分。ステータスは 読書中 になる）
        ReadingModels.RecordMutationResult recorded = readingService.createRecord(user, bookId,
                new ReadingModels.RecordSaveRequest(null, 1, 4, 25, 0, "標記クリア前"));
        assertThat(recorded.book().currentPage()).isEqualTo(4);
        assertThat(recorded.book().status()).isEqualTo("読書中");
        assertThat(recorded.book().lastReadAt()).isNotNull();
        // もう 1 件足しておく（記録が「全部」消えることを見る）
        readingService.createRecord(user, bookId,
                new ReadingModels.RecordSaveRequest(null, 4, 6, 10, 0, "2 件目"));
        assertThat(readingService.listRecords(guardian(), bookId, null, null, null, 1, 100).totalElements()).isEqualTo(2);

        // 標記を 2 件付ける
        readingService.createMark(user, bookId, new ReadingModels.MarkSaveRequest(2, "memo", null, "メモ1", "#f5c542",
                null, null, null, null, null));
        readingService.createMark(user, bookId, new ReadingModels.MarkSaveRequest(3, "highlight", "text", null, "#f5c542",
                null, null, null, null, null));
        assertThat(readingService.detail(guardian(), bookId, null, null).book().markCount()).isEqualTo(2);

        // 【標記クリア】＝標記・読書記録の全削除＋読書の進捗のリセット（1 回の操作）
        ReadingModels.BookMutationResult result = readingService.resetMarksAndProgress(user, bookId);

        ReadingModels.BookRow book = result.book();
        assertThat(book.markCount()).isZero();
        assertThat(book.currentPage()).isEqualTo(1);
        assertThat(book.status()).isEqualTo("未着手");
        assertThat(book.recentMinutes()).isZero();
        assertThat(book.totalMinutes()).isZero();
        assertThat(book.lastReadAt()).isNull();
        assertThat(book.readPercent()).isEqualTo(10);
        assertThat(result.message()).contains("標記 2 件").contains("読書記録 2 件").contains("進捗");

        // 標記は一覧からも詳細からも消えている
        assertThat(readingService.listMarks(guardian(), bookId, null, null, 1, 50).totalElements()).isZero();
        assertThat(readingService.detail(guardian(), bookId, null, null).marks()).isEmpty();

        // 読書記録（履歴）も消えている
        ReadingModels.RecordListResult records = readingService.listRecords(guardian(), bookId, null, null, null, 1, 100);
        assertThat(records.totalElements()).isZero();
        assertThat(records.items()).isEmpty();
    }

    // ------------------------------------------------------------ 分類（マスタ）

    /**
     * 移行で入った分類・言語とファイル行の確認（`MIG_RED_読書_分類_20260913.sql` /
     * `MIG_RED_読書_言語_20260913.sql`）。
     *
     * 分類は**ジャンルだけ**（小説 5 冊 / 雑誌・ガイド 1 冊）で、言語は `言語` 列で持つ
     * （中国語 1 冊＝射鵰英雄伝 / 英語 5 冊）。ファイル行は PDF 6・COVER 6 で、
     * **実体は 2.0 サーバにあり未取得**なので `pdfAvailable` / `coverAvailable` は false
     * （画面は「PDF 未登録」を出す）。
     */
    @Test
    void migratedBooksHaveCategoriesLanguagesAndFileRows() {
        ReadingModels.CategoryListResult categories = readingService.listCategories(guardian());

        assertThat(categories.items()).extracting(ReadingModels.CategoryRow::name)
                .contains("小説", "雑誌・ガイド");
        Map<String, Long> counts = categories.items().stream()
                .collect(Collectors.toMap(ReadingModels.CategoryRow::name, ReadingModels.CategoryRow::bookCount));
        assertThat(counts)
                .containsEntry("小説", 5L)
                .containsEntry("雑誌・ガイド", 1L);

        // 表示順（棚の並び）どおりに返る
        List<Integer> orders = categories.items().stream().map(ReadingModels.CategoryRow::displayOrder).toList();
        assertThat(orders).isSorted();

        // 分類での絞り込みが冊数と一致する
        long novel = categories.items().stream()
                .filter(item -> item.name().equals("小説")).findFirst().orElseThrow().categoryId();
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, novel, null, null, null, 1, 100).items()).hasSize(5);
        long guide = categories.items().stream()
                .filter(item -> item.name().equals("雑誌・ガイド")).findFirst().orElseThrow().categoryId();
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, guide, null, null, null, 1, 100).items()).hasSize(1);

        // 移行で全冊に分類が付いているので未分類は 0 冊（categoryId=0）
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, 0L, null, null, null, 1, 100).items()).isEmpty();

        // 言語は分類とは別の軸（中国語 1 冊＝射鵰英雄伝 / 英語 5 冊）
        List<ReadingModels.BookRow> chineseBooks =
                readingService.searchBooks(guardian(), null, null, null, null, null, null, "中国語", null, null, 1, 100).items();
        assertThat(chineseBooks).hasSize(1);
        assertThat(chineseBooks.get(0).title()).contains("射鵰英雄伝");
        assertThat(chineseBooks.get(0).language()).isEqualTo("中国語");
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, null, "英語", null, null, 1, 100).items())
                .hasSize(5);
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, null, "日本語", null, null, 1, 100).items())
                .as("日本語の本はまだ無い").isEmpty();

        // 全冊に PDF 行と COVER 行がある。実体が無いので available は false
        List<ReadingModels.BookRow> books =
                readingService.searchBooks(guardian(), null, null, null, null, null, null, null, null, null, 1, 100).items();
        long pdfRows = 0;
        long coverRows = 0;
        for (ReadingModels.BookRow book : books) {
            ReadingFileEntity pdf = fileMapper.find(book.bookId(), ReadingModels.FILE_KIND_PDF);
            ReadingFileEntity cover = fileMapper.find(book.bookId(), ReadingModels.FILE_KIND_COVER);
            assertThat(book.hasPdf()).isEqualTo(pdf != null);
            assertThat(book.hasCover()).isEqualTo(cover != null);
            assertThat(book.pdfAvailable()).as("実体は 2.0 側にあるので 2.1 からは読めない").isFalse();
            assertThat(book.coverAvailable()).isFalse();
            assertThat(book.language()).isIn(ReadingModels.LANGUAGES);
            if (pdf != null) {
                pdfRows++;
                assertThat(pdf.getStoredPath()).startsWith("file/ENGLISH_READING/");
                assertThat(book.pdfOriginalName()).isNotBlank();
            }
            if (cover != null) {
                coverRows++;
            }
        }
        assertThat(pdfRows).isEqualTo(books.size());
        assertThat(coverRows).isEqualTo(books.size());
        assertThat(pdfRows).isGreaterThanOrEqualTo(6);

        // 本棚のサマリ: 冊数・分類つき・PDF 登録済み（実体があるもの）
        ReadingModels.ShelfTotals totals = readingService.searchBooks(guardian(), null, null, null, null, null, null, null, null, null, 1, 100)
                .totals();
        assertThat(totals.bookCount()).isEqualTo(books.size());
        assertThat(totals.uncategorizedCount()).as("移行で全冊に分類が付いている").isZero();
        assertThat(totals.pdfCount()).as("実体が無いので 0 冊").isZero();
    }

    /** 言語は登録・修正で往復し、不正な値は 400（DB の CHECK と同じ 3 種類）。 */
    @Test
    void bookLanguageRoundTripsAndRejectsUnknownValues() {
        UserPrincipal user = guardian();

        // 省略した新規登録は既定の '英語'
        long defaulted = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "言語テスト（既定）", "Test Author", "Starter", "未着手", 10, null, false, List.of(), null, null,
                null, null, null)).book().bookId();
        assertThat(readingService.detail(guardian(), defaulted, null, null).book().language()).isEqualTo("英語");

        // 中国語で登録でき、一覧・詳細・絞り込みで同じ値が返る
        long chinese = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "言語テスト（中国語）", "Test Author", "Starter", "未着手", 10, null, false, List.of(), null, null,
                null, null, "中国語")).book().bookId();
        ReadingModels.BookRow created = readingService.detail(guardian(), chinese, null, null).book();
        assertThat(created.language()).isEqualTo("中国語");
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, null, "中国語", null, null, 1, 100).items())
                .extracting(ReadingModels.BookRow::bookId).contains(chinese);

        // 修正で日本語に変えられる（言語を送らない修正では今の値のまま）
        readingService.updateBook(user, chinese, new ReadingModels.BookSaveRequest(
                "言語テスト（中国語）", "Test Author", "Starter", "未着手", 10, 1, false, List.of(), null, null,
                null, null, "日本語"));
        assertThat(readingService.detail(guardian(), chinese, null, null).book().language()).isEqualTo("日本語");
        readingService.updateBook(user, chinese, new ReadingModels.BookSaveRequest(
                "言語テスト（中国語）", "Test Author", "Starter", "未着手", 10, 1, false, List.of(), null, null,
                null, null, null));
        assertThat(readingService.detail(guardian(), chinese, null, null).book().language()).isEqualTo("日本語");

        // 知らない言語は 400（サービス側で先に弾く）
        assertThatThrownBy(() -> readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "言語テスト（不正）", "Test Author", "Starter", "未着手", 10, null, false, List.of(), null, null,
                null, null, "フランス語")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("言語");
        assertThatThrownBy(() -> readingService.searchBooks(guardian(), null, null, null, null, null, null, "フランス語", null, null, 1, 10))
                .isInstanceOf(ValidationException.class);
    }

    /** 分類の登録 → 改名 → 並べ替え → 削除。**削除しても本は消えず未分類に戻る**。 */
    @Test
    void categoryLifecycleKeepsBooksAndReturnsThemToUnclassified() {
        UserPrincipal user = guardian();

        // 登録（前後の空白は落とす。表示順を省略したら末尾）
        ReadingModels.CategoryMutationResult created = readingService.createCategory(user,
                new ReadingModels.CategorySaveRequest("  E2E 分類  ", null, " テスト用 "));
        long categoryId = created.category().categoryId();
        assertThat(created.category().name()).isEqualTo("E2E 分類");
        assertThat(created.category().description()).isEqualTo("テスト用");
        assertThat(created.category().displayOrder()).isGreaterThan(0);

        // 名前の重複は 400 相当
        assertThatThrownBy(() -> readingService.createCategory(user,
                new ReadingModels.CategorySaveRequest("E2E 分類", 9, null)))
                .isInstanceOf(ValidationException.class);

        // 改名と並べ替え（表示順 0 で先頭へ）
        ReadingModels.CategoryMutationResult renamed = readingService.updateCategory(user, categoryId,
                new ReadingModels.CategorySaveRequest("E2E 分類（改名）", 0, "説明を更新"));
        assertThat(renamed.category().name()).isEqualTo("E2E 分類（改名）");
        assertThat(readingService.listCategories(guardian()).items().get(0).categoryId()).isEqualTo(categoryId);

        // 分類を付けて本を登録する
        long bookId = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "分類テストの本", "Test Author", "Starter", "未着手", 30, null, false, List.of(), null, null, null,
                categoryId, null)).book().bookId();
        ReadingModels.BookRow categorized = readingService.detail(guardian(), bookId, null, null).book();
        assertThat(categorized.categoryId()).isEqualTo(categoryId);
        assertThat(categorized.categoryName()).isEqualTo("E2E 分類（改名）");
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, categoryId, null, null, null, 1, 100).items())
                .extracting(ReadingModels.BookRow::bookId).containsExactly(bookId);

        // 削除: 冊数をメッセージに出し、本は未分類に戻る（消えない）
        ReadingModels.SimpleResult deleted = readingService.deleteCategory(user, categoryId);
        assertThat(deleted.count()).isEqualTo(1);
        assertThat(deleted.message()).contains("1 冊は未分類になりました");

        ReadingModels.BookRow unclassified = readingService.detail(guardian(), bookId, null, null).book();
        assertThat(unclassified.categoryId()).isNull();
        assertThat(unclassified.categoryName()).isNull();
        assertThat(unclassified.title()).isEqualTo("分類テストの本");
        assertThat(readingService.searchBooks(guardian(), null, null, null, null, null, 0L, null, null, null, 1, 100).items())
                .extracting(ReadingModels.BookRow::bookId).contains(bookId);

        // 分類そのものは消えている
        assertThat(readingService.listCategories(guardian()).items())
                .extracting(ReadingModels.CategoryRow::categoryId).doesNotContain(categoryId);
    }

    // ---------------------------------------------------------------- 標記

    /** 座標（正規化 0〜1）と描画データが、登録 → 取得で同じ値のまま往復する。 */
    @Test
    void markCoordinatesAreStoredAndReturned() {
        UserPrincipal user = guardian();
        ReadingModels.BookRow target = readingService.searchBooks(guardian(), "Raya", null, null, null, null, null, null, null, null, 1, 10)
                .items().stream().findFirst().orElseThrow();

        ReadingModels.MarkMutationResult created = readingService.createMark(user, target.bookId(),
                new ReadingModels.MarkSaveRequest(1, "pen", null, null, "#123456",
                        new BigDecimal("0.123456"), new BigDecimal("0.500000"),
                        new BigDecimal("0.250000"), new BigDecimal("0.010000"),
                        "{\"kind\":\"pen\",\"points\":[{\"x\":0.1,\"y\":0.5}]}"));

        ReadingModels.MarkRow mark = created.mark();
        assertThat(mark.markType()).isEqualTo("pen");
        assertThat(mark.positionX()).isEqualByComparingTo("0.123456");
        assertThat(mark.positionY()).isEqualByComparingTo("0.500000");
        assertThat(mark.width()).isEqualByComparingTo("0.250000");
        assertThat(mark.height()).isEqualByComparingTo("0.010000");
        assertThat(mark.drawingData()).contains("\"kind\":\"pen\"");

        // 取得（一覧・詳細）でも同じ値。NUMERIC(10,6) をそのまま返す
        ReadingModels.MarkRow loaded = readingService.listMarks(guardian(), target.bookId(), 1, "pen", 1, 50).items().stream()
                .filter(item -> item.markId() == mark.markId()).findFirst().orElseThrow();
        assertThat(loaded.positionX()).isEqualByComparingTo("0.123456");
        assertThat(loaded.height()).isEqualByComparingTo("0.010000");
        assertThat(loaded.drawingData()).isEqualTo("{\"kind\":\"pen\",\"points\":[{\"x\":0.1,\"y\":0.5}]}");

        // ページ指定の詳細でも座標つきで返る
        assertThat(readingService.detail(guardian(), target.bookId(), 1, null).marks())
                .filteredOn(item -> item.markId() == mark.markId())
                .singleElement()
                .satisfies(item -> assertThat(item.positionY()).isEqualByComparingTo("0.500000"));
    }

    // ------------------------------------------------------ 本文 PDF・表紙の行

    /**
     * `RED_書籍ファイル情報` の行の往復（登録 → 取得 → 差し替え → 削除）を実 DB で確かめる。
     *
     * <p>実体ファイルの出入りは {@code ReadingFileStorageTest}（一時ディレクトリ）が受け持つので、
     * ここでは MyBatis の SQL（UNIQUE(書籍ID,区分) を守った差し替え・書籍ID での引き当て）を見る。</p>
     */
    @Test
    void fileRowRoundTripAndTotalPagesUpdate() {
        UserPrincipal user = guardian();
        long bookId = readingService.createBook(user, new ReadingModels.BookSaveRequest(
                "ファイル行テスト", "Test Author", "Starter", "未着手", 30, 30, false, List.of(), null, null, null,
                null, null)).book().bookId();

        ReadingFileEntity entity = new ReadingFileEntity();
        entity.setBookId(bookId);
        entity.setFileKind(ReadingModels.FILE_KIND_PDF);
        entity.setOriginalName("original.pdf");
        entity.setStoredName("ER-test-00000001.pdf");
        entity.setStoredPath("books/ER-test/");
        entity.setMimeType("application/pdf");
        entity.setFileSize(1234L);
        entity.setPageCount(30);
        entity.setCreatedBy(user.accountId());
        assertThat(fileMapper.insert(entity)).isEqualTo(1);
        assertThat(entity.getFileId()).isNotNull();

        // 登録した行が読める（実体は無いので画面は「PDF 未登録」）
        ReadingFileEntity loaded = fileMapper.find(bookId, ReadingModels.FILE_KIND_PDF);
        assertThat(loaded.getStoredName()).isEqualTo("ER-test-00000001.pdf");
        assertThat(loaded.getStoredPath()).isEqualTo("books/ER-test/");
        assertThat(loaded.getFileSize()).isEqualTo(1234L);
        assertThat(readingService.detail(guardian(), bookId, null, null).book().hasPdf()).isTrue();
        assertThat(readingService.detail(guardian(), bookId, null, null).book().pdfAvailable()).isFalse();

        // 差し替え（同じ行の更新。登録元コード・登録日時は残る）
        loaded.setStoredName("ER-test-00000002.pdf");
        loaded.setFileSize(4321L);
        loaded.setUpdatedBy(user.accountId());
        assertThat(fileMapper.update(loaded)).isEqualTo(1);
        assertThat(fileMapper.find(bookId, ReadingModels.FILE_KIND_PDF).getStoredName())
                .isEqualTo("ER-test-00000002.pdf");

        // 表紙は別の行（UNIQUE(書籍ID, 区分)）
        ReadingFileEntity cover = new ReadingFileEntity();
        cover.setBookId(bookId);
        cover.setFileKind(ReadingModels.FILE_KIND_COVER);
        cover.setStoredName("ER-test-00000001.png");
        cover.setStoredPath("books/ER-test/");
        cover.setMimeType("image/png");
        cover.setCreatedBy(user.accountId());
        fileMapper.insert(cover);
        assertThat(fileMapper.findByBook(bookId))
                .extracting(ReadingFileEntity::getFileKind)
                .containsExactlyInAnyOrder(ReadingModels.FILE_KIND_COVER, ReadingModels.FILE_KIND_PDF);

        // PDF を上げたときの総ページ数（現在ページは総ページ数に丸める）
        assertThat(bookMapper.updateTotalPages(bookId, 10, user.accountId())).isEqualTo(1);
        ReadingModels.BookRow afterUpdate = readingService.detail(guardian(), bookId, null, null).book();
        assertThat(afterUpdate.totalPages()).isEqualTo(10);
        assertThat(afterUpdate.currentPage()).isEqualTo(10);

        // 行の削除
        assertThat(fileMapper.delete(loaded.getFileId())).isEqualTo(1);
        assertThat(fileMapper.find(bookId, ReadingModels.FILE_KIND_PDF)).isNull();
    }

    // ------------------------------------------------------------ 読書履歴の絞り込み

    /**
     * 読書履歴の絞り込み（`dateFrom` / `dateTo` / `pageNo`）。
     *
     * <p>移行データの 書籍ID=3（Harry Potter 1）は 265 件・2026-04-08〜2026-08-05。
     * 絞り込みは `totalElements` / `totalPages` にも効く。</p>
     */
    @Test
    void recordHistoryFiltersByDateAndPage() {
        long bookId = 3L;
        ReadingModels.RecordListResult all = readingService.listRecords(guardian(), bookId, null, null, null, 1, 100);

        assertThat(all.totalElements()).as("移行した 265 件").isEqualTo(265);
        assertThat(all.totalPages()).isEqualTo(3);
        assertThat(all.items()).hasSize(100);

        // 範囲を狭めると件数が減り、返る記録は範囲内だけ
        ReadingModels.RecordListResult august =
                readingService.listRecords(guardian(), bookId, "2026-08-01", "2026-08-31", null, 1, 100);
        assertThat(august.totalElements()).isGreaterThan(0);
        assertThat(august.totalElements()).isLessThan(all.totalElements());
        assertThat(august.items()).allSatisfy(record ->
                assertThat(record.readAt()).startsWith("2026-08").isBetween("2026-08-01", "2026-08-31T23:59:59"));
        assertThat(august.totalPages()).isEqualTo((int) Math.ceil(august.totalElements() / 100.0));

        // dateTo はその日いっぱいを含む（最初の日 2026-04-08 の記録が取れる）
        ReadingModels.RecordListResult firstDay =
                readingService.listRecords(guardian(), bookId, "2026-04-08", "2026-04-08", null, 1, 100);
        assertThat(firstDay.totalElements()).isGreaterThan(0);
        assertThat(firstDay.items()).allSatisfy(record -> assertThat(record.readAt()).startsWith("2026-04-08"));
        // その前日までなら 0 件（＝ dateTo の境界が効いている）
        assertThat(readingService.listRecords(guardian(), bookId, null, "2026-04-07", null, 1, 100).totalElements()).isZero();

        // 範囲外は 0 件（totalElements も 0）
        ReadingModels.RecordListResult nothing =
                readingService.listRecords(guardian(), bookId, "2026-01-01", "2026-01-31", null, 1, 100);
        assertThat(nothing.totalElements()).isZero();
        assertThat(nothing.items()).isEmpty();
        assertThat(nothing.totalPages()).isZero();

        // dateFrom だけ / dateTo だけでも絞れる
        assertThat(readingService.listRecords(guardian(), bookId, "2026-08-01", null, null, 1, 100).totalElements())
                .isEqualTo(august.totalElements());
        assertThat(readingService.listRecords(guardian(), bookId, null, "2026-04-07", null, 1, 100).totalElements()).isZero();

        // ページ番号: 「そのページを読んだ記録」だけ（開始 <= pageNo <= 終了/終了なし）
        ReadingModels.RecordRow sample = all.items().get(0);
        assertThat(sample.pageStart()).isNotNull();
        assertThat(sample.pageEnd()).isNotNull();
        int pageNo = sample.pageStart();
        ReadingModels.RecordListResult onPage = readingService.listRecords(guardian(), bookId, null, null, pageNo, 1, 100);
        assertThat(onPage.totalElements()).isGreaterThan(0);
        assertThat(onPage.items()).allSatisfy(record -> {
            assertThat(record.pageStart()).isLessThanOrEqualTo(pageNo);
            if (record.pageEnd() != null) {
                assertThat(record.pageEnd()).isGreaterThanOrEqualTo(pageNo);
            }
        });
        assertThat(onPage.items()).extracting(ReadingModels.RecordRow::recordId).contains(sample.recordId());

        // どの記録の範囲にも入らないページは 0 件
        assertThat(readingService.listRecords(guardian(), bookId, null, null, 1, 1, 100).totalElements())
                .as("最初の記録でも 7 ページからなので 1 ページは 0 件").isZero();
        assertThat(readingService.listRecords(guardian(), bookId, null, null, 99999, 1, 100).totalElements()).isZero();

        // 日付とページ番号は同時に効く
        int startOfSample = sample.pageStart();
        assertThat(readingService.listRecords(guardian(), bookId, sample.readAt().substring(0, 10),
                        sample.readAt().substring(0, 10), startOfSample, 1, 100).items())
                .extracting(ReadingModels.RecordRow::recordId).contains(sample.recordId());
    }

    // ------------------------------------------------------ 語彙辞書キャッシュ

    /**
     * `RED_語彙辞書情報` の upsert と取得（`UNIQUE (言語, 見出し語)` で 1 行のまま更新）。
     *
     * <p>実 DB で確かめる。外部へは行かない（Mapper を直接使う）ので、ネットワークが
     * 無い環境でも通る。</p>
     */
    @Test
    void dictionaryCacheUpsertsOneRowPerWord() {
        dictionaryMapper.delete("英語", "e2e-cache-word");

        ReadingDictionaryEntity first = new ReadingDictionaryEntity();
        first.setLanguage("英語");
        first.setHeadword("e2e-cache-word");
        first.setJapanese("最初の日本語訳");
        first.setChinese("最初的中文");
        first.setSource("EXCELAPI+YOUDAO");
        assertThat(dictionaryMapper.upsert(first)).isEqualTo(1);

        ReadingDictionaryEntity stored = dictionaryMapper.find("英語", "e2e-cache-word");
        assertThat(stored.getJapanese()).isEqualTo("最初の日本語訳");
        assertThat(stored.getChinese()).isEqualTo("最初的中文");
        assertThat(stored.getSource()).isEqualTo("EXCELAPI+YOUDAO");
        assertThat(stored.getFetchedAt()).isNotNull();
        Long dictionaryId = stored.getDictionaryId();

        // 同じ語をもう一度引けた（ExcelAPI は落ちていて中国語だけ取れた）→ 1 行のまま更新
        ReadingDictionaryEntity second = new ReadingDictionaryEntity();
        second.setLanguage("英語");
        second.setHeadword("e2e-cache-word");
        second.setChinese("新しい中文");
        second.setSource("YOUDAO");
        assertThat(dictionaryMapper.upsert(second)).isEqualTo(1);
        dictionaryMapper.upsert(second);

        ReadingDictionaryEntity updated = dictionaryMapper.find("英語", "e2e-cache-word");
        assertThat(updated.getDictionaryId()).as("行は増えない（UNIQUE(言語,見出し語)）").isEqualTo(dictionaryId);
        assertThat(updated.getChinese()).isEqualTo("新しい中文");
        assertThat(updated.getJapanese()).as("今回取れなかった項目は消さない").isEqualTo("最初の日本語訳");
        assertThat(updated.getSource()).isEqualTo("YOUDAO");

        // 日本語の語は別の行（UNIQUE は 言語 + 見出し語）
        ReadingDictionaryEntity japanese = new ReadingDictionaryEntity();
        japanese.setLanguage("日本語");
        japanese.setHeadword("e2e-cache-word");
        japanese.setExplanation("解説");
        japanese.setSource("MANUAL");
        dictionaryMapper.upsert(japanese);
        assertThat(dictionaryMapper.find("日本語", "e2e-cache-word").getExplanation()).isEqualTo("解説");
        assertThat(dictionaryMapper.find("英語", "e2e-cache-word").getDictionaryId()).isEqualTo(dictionaryId);

        // 後始末（このテストのトランザクションはロールバックするが、明示的にも消す）
        dictionaryMapper.delete("英語", "e2e-cache-word");
        dictionaryMapper.delete("日本語", "e2e-cache-word");
        assertThat(dictionaryMapper.find("英語", "e2e-cache-word")).isNull();
    }

    // -------------------------------------------- 公開範囲と【自分の本棚】（2026-09-14）

    /**
     * **機能の核**: 家庭の本（FAMILY）は、その家庭の保護者・生徒にだけ見える。
     * 他家庭の生徒にも管理者にも見えない（決定 Q1・Q2）。
     */
    @Test
    void familyBookIsVisibleOnlyInsideItsFamily() {
        long bookId = readingService.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "家庭だけの本", "Test Author", "Starter", "未着手", 20, null, false,
                List.of(), null, null, null, null, null)).book().bookId();

        ReadingModels.BookRow created = readingService.detail(guardian(), bookId, null, null).book();
        assertThat(created.scope()).as("保護者が登録した本は家庭の本").isEqualTo("FAMILY");
        assertThat(created.ownerFamilyId()).as("持ち主は自分の家庭（生徒のアカウントID 2）").isEqualTo(2L);
        assertThat(created.ownerFamilyLabel()).isNotBlank();
        assertThat(created.inMyShelf()).as("登録した本人の本棚に自動で入る（決定 Q5）").isTrue();

        // 同じ家庭の生徒は【図書館】で見える（本棚には入っていない）
        assertThat(visibleBookIds(student())).contains(bookId);
        assertThat(readingService.detail(student(), bookId, null, null).book().inMyShelf()).isFalse();

        // 別の家庭の生徒には見えない（404。存在も漏らさない）
        assertThat(visibleBookIds(otherFamilyStudent())).doesNotContain(bookId);
        assertThatThrownBy(() -> readingService.detail(otherFamilyStudent(), bookId, null, null))
                .isInstanceOf(NotFoundException.class);

        // 管理者は家庭の本を見ない（全体書籍だけ。決定 Q1）
        assertThat(visibleBookIds(admin())).doesNotContain(bookId);
        assertThatThrownBy(() -> readingService.detail(admin(), bookId, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * 公開範囲の見え方（2026-09-14 の決定）。
     *
     * <p>**冊数はデータによって変わる**（管理者が全体書籍を足す／家庭が自分の本を足す）ので、
     * 「6 冊」のような固定値では見ない。ここでは関係だけを確かめる:</p>
     * <ul>
     *   <li>全体書籍（GLOBAL）は**だれから見ても同じ**</li>
     *   <li>家庭の本（FAMILY）は**その家庭だけ**に見える（他の家庭・管理者には見えない）</li>
     * </ul>
     */
    @Test
    void globalBooksAreVisibleToEveryoneAndFamilyBooksOnlyToTheirFamily() {
        // 管理者に見えるのは全体書籍だけ（＝全体書籍の集合）
        java.util.Set<Long> global = visibleBookIds(admin());
        assertThat(global).as("全体書籍が 1 冊以上ある").isNotEmpty();
        assertThat(visibleBookIdsOfScope(admin(), "GLOBAL")).containsExactlyInAnyOrderElementsOf(global);
        assertThat(visibleBookIdsOfScope(admin(), "FAMILY")).as("管理者に家庭の本は見えない").isEmpty();

        // だれから見ても全体書籍は見える
        for (UserPrincipal user : List.of(guardian(), student(), otherFamilyStudent())) {
            assertThat(visibleBookIds(user)).as("全体書籍はみんなに見える").containsAll(global);
        }

        // 保護者（家庭 2）だけに見える本＝家庭 2 の家庭の本。あれば、他の家庭と管理者には見えない
        java.util.Set<Long> familyOnly = new java.util.HashSet<>(visibleBookIds(guardian()));
        familyOnly.removeAll(global);
        if (!familyOnly.isEmpty()) {
            assertThat(visibleBookIdsOfScope(guardian(), "FAMILY"))
                    .as("自分の家庭の本は見える").containsAll(familyOnly);
            assertThat(visibleBookIds(otherFamilyStudent()))
                    .as("他の家庭の本は見えない").doesNotContainAnyElementsOf(familyOnly);
            assertThat(visibleBookIds(admin()))
                    .as("管理者に家庭の本は見えない").doesNotContainAnyElementsOf(familyOnly);
        }
    }

    /** 管理者は全体書籍だけを作れる（家庭の本は作れない）。 */
    @Test
    void adminCreatesGlobalBooksOnly() {
        ReadingModels.BookRow created = readingService.createBook(admin(), new ReadingModels.BookSaveRequest(
                "管理者の全体書籍", "Test Author", "Starter", "未着手", 10, null, false,
                List.of(), null, null, null, null, null, "GLOBAL")).book();

        assertThat(created.scope()).isEqualTo("GLOBAL");
        assertThat(created.ownerFamilyId()).isNull();
        assertThat(created.inMyShelf()).as("全体書籍は誰の本棚にも入らない").isFalse();

        assertThatThrownBy(() -> readingService.createBook(admin(), new ReadingModels.BookSaveRequest(
                "家庭の本は作れない", "Test Author", "Starter", "未着手", 10, null, false,
                List.of(), null, null, null, null, null, "FAMILY")))
                .isInstanceOf(ReadingAccessException.class);
    }

    /** 生徒は登録・修正・削除ができない（読むことと自分の本棚だけ。決定 Q9）。 */
    @Test
    void studentCannotManageBooks() {
        assertThatThrownBy(() -> readingService.createBook(student(), new ReadingModels.BookSaveRequest(
                "生徒の本", "Test Author", "Starter", "未着手", 10, null, false,
                List.of(), null, null, null, null, null)))
                .isInstanceOf(ReadingAccessException.class);

        // 全体書籍は読めるが直せない（403）
        long globalBookId = visibleBookIds(student()).iterator().next();
        assertThatThrownBy(() -> readingService.deleteBook(student(), globalBookId))
                .isInstanceOf(ReadingAccessException.class);
        assertThatThrownBy(() -> readingService.setPinned(student(), globalBookId, true, null))
                .isInstanceOf(ReadingAccessException.class);
    }

    /** 保護者は全体書籍を直せない（読めるだけ）。家庭の本だけが直せる。 */
    @Test
    void guardianCannotEditGlobalBooks() {
        // 一覧の先頭は家庭の本（自分の家庭のもの）のこともあるので、全体書籍を明示的に選ぶ
        long globalBookId = readingService.searchBooks(guardian(), null, null, null, null, null, null, null,
                "GLOBAL", null, 1, 1).items().get(0).bookId();

        assertThatThrownBy(() -> readingService.deleteBook(guardian(), globalBookId))
                .isInstanceOf(ReadingAccessException.class);
        assertThatThrownBy(() -> readingService.setPinned(guardian(), globalBookId, true, null))
                .isInstanceOf(ReadingAccessException.class);
    }

    /**
     * `totals` と「PDF 登録済み」と分類の冊数が、**見える本だけ**を数えている
     * （当て忘れると画面の「全 N 冊」だけが全体の件数になって食い違う）。
     */
    @Test
    void totalsAndCategoryCountsMatchVisibleBooks() {
        ReadingModels.BookListResult shelf = readingService.searchBooks(otherFamilyStudent(), null, null, null,
                null, null, null, null, null, null, 1, 100);

        assertThat(shelf.totals().bookCount()).as("サマリの冊数＝一覧の件数").isEqualTo(shelf.totalElements());
        assertThat(shelf.totals().uncategorizedCount())
                .isEqualTo(shelf.items().stream().filter(item -> item.categoryId() == null).count());
        assertThat(shelf.totals().pdfCount())
                .as("実体がある PDF だけを数え、見える本に限る")
                .isLessThanOrEqualTo(shelf.totals().bookCount());

        // 分類の冊数も見える本だけ（合計は一覧の冊数を超えない）
        long categorized = readingService.listCategories(otherFamilyStudent()).items().stream()
                .mapToLong(ReadingModels.CategoryRow::bookCount).sum();
        assertThat(categorized).isLessThanOrEqualTo(shelf.totals().bookCount());
    }

    /** 【自分の本棚】は `shelf=MINE` で自分の行だけを返す（他人の本棚は混ざらない）。 */
    @Test
    void myShelfReturnsOnlyMyOwnShelf() {
        // すでに入っている行（画面や他の検証で入れた分）を基準にして、増減だけを見る
        // （移行では初期投入しないが、DB の状態に依存しない書き方にする。決定 D3）
        java.util.Set<Long> before = myShelfIds(student());
        long first = visibleBookIds(student()).stream().sorted()
                .filter(id -> !before.contains(id)).findFirst().orElseThrow();

        readingService.addToShelf(student(), first);
        ReadingModels.BookListResult mine = readingService.searchBooks(student(), null, null, null, null, null,
                null, null, null, "MINE", 1, 100);
        assertThat(mine.items()).extracting(ReadingModels.BookRow::bookId).contains(first);
        assertThat(mine.items()).allSatisfy(item -> assertThat(item.inMyShelf()).isTrue());
        assertThat(mine.totals().myShelfCount()).isEqualTo(before.size() + 1);

        // 冪等（もう一度入れても増えない）
        readingService.addToShelf(student(), first);
        assertThat(myShelfIds(student())).hasSize(before.size() + 1);

        // 同じ本を家族の別の人が入れると、その人の行になる（本棚はアカウントごと。決定 Q2）
        java.util.Set<Long> guardianBefore = myShelfIds(guardian());
        boolean guardianHadIt = guardianBefore.contains(first);
        readingService.addToShelf(guardian(), first);
        assertThat(myShelfIds(guardian())).contains(first);
        assertThat(myShelfIds(guardian())).hasSize(guardianBefore.size() + (guardianHadIt ? 0 : 1));
        assertThat(myShelfIds(student())).as("家族の別の人の本棚は変わらない").hasSize(before.size() + 1);

        // 外す（こちらも冪等）→ 元の状態に戻る
        readingService.removeFromShelf(student(), first);
        assertThat(myShelfIds(student())).containsExactlyInAnyOrderElementsOf(before);
        readingService.removeFromShelf(student(), first);
        assertThat(myShelfIds(student())).containsExactlyInAnyOrderElementsOf(before);
        assertThat(visibleBookIds(student())).as("外しても図書館には残る").contains(first);
    }

    /** 見えない本は【本棚に入れる】こともできない（404）。 */
    @Test
    void cannotAddInvisibleBookToShelf() {
        long familyBookId = readingService.createBook(guardian(), new ReadingModels.BookSaveRequest(
                "他家庭には入れられない本", "Test Author", "Starter", "未着手", 10, null, false,
                List.of(), null, null, null, null, null)).book().bookId();

        assertThatThrownBy(() -> readingService.addToShelf(otherFamilyStudent(), familyBookId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> readingService.addToShelf(admin(), familyBookId))
                .isInstanceOf(NotFoundException.class);
    }

    /** 分類も家庭ごと（決定 Q6）。他家庭の分類は見えず、自分の分類だけ作れる。 */
    @Test
    void categoriesAreScopedPerFamily() {
        ReadingModels.CategoryRow created = readingService.createCategory(guardian(),
                new ReadingModels.CategorySaveRequest("家庭の分類", null, null)).category();
        assertThat(created.ownerFamilyId()).as("保護者が作った分類は自分の家庭のもの").isEqualTo(2L);

        // 同じ名前でも、全体の分類（管理者が作る）とは別物として作れる
        ReadingModels.CategoryRow global = readingService.createCategory(admin(),
                new ReadingModels.CategorySaveRequest("家庭の分類", null, null)).category();
        assertThat(global.ownerFamilyId()).as("管理者の分類は全体の分類").isNull();
        assertThat(global.name()).isEqualTo(created.name());

        assertThat(readingService.listCategories(otherFamilyStudent()).items())
                .as("他家庭の分類は見えない")
                .noneMatch(item -> created.categoryId() == item.categoryId());
        assertThatThrownBy(() -> readingService.updateCategory(otherGuardian(), created.categoryId(),
                new ReadingModels.CategorySaveRequest("改名", 0, null)))
                .as("他家庭の保護者からは見えないので 404")
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> readingService.updateCategory(otherFamilyStudent(), created.categoryId(),
                new ReadingModels.CategorySaveRequest("改名", 0, null)))
                .as("生徒は分類を触れない（403）")
                .isInstanceOf(ReadingAccessException.class);
    }

    /** 不正な日付・1 未満のページ番号は 400 相当（実 DB でも Mapper まで行かない）。 */
    @Test
    void recordHistoryRejectsInvalidFilterValues() {
        assertThatThrownBy(() -> readingService.listRecords(guardian(), 3L, "2026-13-40", null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> readingService.listRecords(guardian(), 3L, "abc", null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> readingService.listRecords(guardian(), 3L, null, null, 0, 1, 20))
                .isInstanceOf(ValidationException.class);
    }
}
