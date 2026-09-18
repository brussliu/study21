package com.study21.user.reading;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 読書管理の実装（2.0 の英語読書を 2.1 の設計で作り直したもの）。
 *
 * <p>2.0 の規則をそのまま引き継いでいる点:</p>
 * <ul>
 *   <li>並びは「置頂 → 最後に読んだ順」</li>
 *   <li>読書記録を付けると 現在ページ・読書ステータス（総ページに達したら 読了）・
 *       最近の読書時間・累計読書時間・最終読書日時 をまとめて更新する</li>
 *   <li>累計標記件数 は標記の追加・削除のたびに数え直す</li>
 *   <li>書籍を消すと 読書記録・標記 も消える（DB の ON DELETE CASCADE）</li>
 * </ul>
 *
 * <p>本文 PDF と表紙画像は `RED_書籍ファイル情報` が管理し、実体は
 * {@link ReadingFileStorage}（`books/&lt;書籍番号&gt;/`）に置く。2.0 から引き継いだ行は
 * 実体が無いことがあり、その場合は `pdfAvailable=false` で画面が「PDF 未登録」を出す。</p>
 */
@Service
public class ReadingServiceImpl implements ReadingService {

    private static final DateTimeFormatter BOOK_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int TAG_MAX = 60;
    private static final int TAG_COUNT_MAX = 10;
    private static final int TEXT_MAX = 2000;
    /** 手書き（pen）の点列 JSON の上限。DB は TEXT なので長めに許す */
    private static final int DRAWING_MAX = 100_000;

    private final ReadingBookMapper bookMapper;
    private final ReadingRecordMapper recordMapper;
    private final ReadingMarkMapper markMapper;
    private final ReadingCategoryMapper categoryMapper;
    private final ReadingFileMapper fileMapper;
    private final ReadingFileStorage storage;
    /** 家庭（＝保護者—生徒の紐付け）の解決に使う（新しい家庭ID は作らない） */
    private final AccountMapper accountMapper;

    public ReadingServiceImpl(ReadingBookMapper bookMapper,
                              ReadingRecordMapper recordMapper,
                              ReadingMarkMapper markMapper,
                              ReadingCategoryMapper categoryMapper,
                              ReadingFileMapper fileMapper,
                              ReadingFileStorage storage,
                              AccountMapper accountMapper) {
        this.bookMapper = bookMapper;
        this.recordMapper = recordMapper;
        this.markMapper = markMapper;
        this.categoryMapper = categoryMapper;
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.accountMapper = accountMapper;
    }

    // ------------------------------------------------------------------ 一覧

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.BookListResult searchBooks(UserPrincipal user, String keyword, String difficulty,
                                                    String status, String tag, Boolean pinned, Long categoryId,
                                                    String language, String scope, String shelf,
                                                    int page, int size) {
        ReadingScope actor = scope(user);
        int safeSize = size <= 0 ? ReadingModels.DEFAULT_SIZE : Math.min(size, ReadingModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String difficultyFilter = normalizeChoice(difficulty, ReadingModels.DIFFICULTIES, "難易度");
        String statusFilter = normalizeChoice(status, ReadingModels.STATUSES, "読書ステータス");
        String languageFilter = normalizeChoice(language, ReadingModels.LANGUAGES, "言語");
        String keywordFilter = blankToNull(keyword);
        String tagFilter = blankToNull(tag);
        // 0＝未分類のみ / 正の値＝その分類 / null＝すべて
        Long categoryFilter = normalizeCategoryFilter(categoryId);
        // 可視スコープ（ALL / GLOBAL / FAMILY）と【自分の本棚】だけの絞り込み
        String scopeFilter = normalizeScopeFilter(scope);
        boolean myShelfOnly = isMyShelf(shelf);

        long total = bookMapper.count(keywordFilter, difficultyFilter, statusFilter, tagFilter, pinned,
                categoryFilter, languageFilter, actor.accountId(), actor.familyStudentId(),
                scopeFilter, myShelfOnly);
        List<ReadingModels.BookRow> items = bookMapper.search(keywordFilter, difficultyFilter, statusFilter,
                        tagFilter, pinned, categoryFilter, languageFilter, actor.accountId(),
                        actor.familyStudentId(), scopeFilter, myShelfOnly, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(this::toRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        ReadingShelfTotals totals = bookMapper.totals(actor.accountId(), actor.familyStudentId(),
                scopeFilter, myShelfOnly);

        return new ReadingModels.BookListResult(items, total, safePage, safeSize, totalPages,
                new ReadingModels.ShelfTotals(totals.books(), totals.reading(), totals.finished(),
                        totals.minutes(), totals.marks(),
                        countAvailablePdfs(actor, scopeFilter, myShelfOnly), totals.uncategorized(),
                        totals.myShelf()));
    }

    /**
     * 本文 PDF の実体がある冊数（書籍管理画面の「PDF 登録済み N 冊」）。
     *
     * <p>実体の有無はストレージ（ディスク）でしか分からないので、PDF 行を引いて
     * 1 冊ずつ確認する。1 冊 1 行（`UNIQUE (書籍ID, ファイル区分)`）なので行数＝冊数。</p>
     */
    private long countAvailablePdfs(ReadingScope actor, String scopeFilter, boolean myShelfOnly) {
        return fileMapper.findByKind(ReadingModels.FILE_KIND_PDF, actor.accountId(), actor.familyStudentId(),
                        scopeFilter, myShelfOnly)
                .stream()
                .filter(file -> storage.exists(file.getStoredPath(), file.getStoredName()))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.BookDetail detail(UserPrincipal user, long bookId, Integer markPage, String markType) {
        ReadingScope actor = scope(user);
        ReadingBookEntity book = requireBook(actor, bookId);
        List<ReadingModels.RecordRow> records = recordMapper
                .list(bookId, null, null, null, actor.familyStudentId(), 50, 0).stream()
                .map(ReadingServiceImpl::toRecordRow)
                .toList();
        String type = normalizeChoice(markType, ReadingModels.MARK_TYPES, "標記の種別");
        List<ReadingModels.MarkRow> marks = markMapper.list(bookId, markPage, type, 500, 0).stream()
                .map(ReadingServiceImpl::toMarkRow)
                .toList();
        List<Integer> markedPages = markMapper.markedPages(bookId);
        // 閲覧画面も「自分の本棚に入っているか」を知りたい（画面の出し分け用）
        book.setInMyShelf(bookMapper.existsShelf(bookId, actor.accountId()));
        return new ReadingModels.BookDetail(toRow(book), records, marks, markedPages);
    }

    // ------------------------------------------------------------ 登録・更新

    @Override
    @Transactional
    public ReadingModels.BookMutationResult createBook(UserPrincipal user, ReadingModels.BookSaveRequest request) {
        ReadingScope actor = scope(user);
        requireManageRole(actor, "書籍の登録");
        // 公開範囲はクライアントに決めさせない（管理者＝GLOBAL / 保護者＝FAMILY＋自家庭）
        String bookScope = actor.createScope();
        rejectScopeOverride(actor, request.scope());
        int totalPages = requireTotalPages(request.totalPages());
        ReadingBookEntity entity = new ReadingBookEntity();
        entity.setBookNo(nextBookNo());
        entity.setSubject("英語");
        // 言語を省略した新規登録は '英語'（2.0 からの移行データと同じ）
        entity.setLanguage(choiceOrDefault(request.language(), ReadingModels.LANGUAGES, "言語",
                ReadingModels.DEFAULT_LANGUAGE));
        entity.setTitle(request.title().trim());
        entity.setAuthor(request.author().trim());
        entity.setDifficulty(choiceOrDefault(request.difficulty(), ReadingModels.DIFFICULTIES, "難易度", "Elementary"));
        entity.setStatus(choiceOrDefault(request.status(), ReadingModels.STATUSES, "読書ステータス", "未着手"));
        entity.setTotalPages(totalPages);
        entity.setCurrentPage(clampPage(request.currentPage(), totalPages, 1));
        entity.setPinned(Boolean.TRUE.equals(request.pinned()));
        entity.setTags(formatTags(request.tags()));
        entity.setSummary(trimToNull(request.summary(), TEXT_MAX));
        entity.setNote(trimToNull(request.note(), TEXT_MAX));
        entity.setCategoryId(requireCategory(actor, request.categoryId()));
        entity.setCreatedBy(user.accountId());
        entity.setScope(bookScope);
        entity.setOwnerFamilyId(ReadingScope.SCOPE_FAMILY.equals(bookScope) ? actor.familyStudentId() : null);
        bookMapper.insert(entity);

        // 保護者が登録した本は**登録した本人の本棚にだけ**自動で入れる（家庭の他の人は
        // 【図書館】から入れる。決定 Q5）。全体書籍は誰の本棚にも入らない。
        if (ReadingScope.SCOPE_FAMILY.equals(bookScope)) {
            bookMapper.insertShelf(entity.getBookId(), actor.accountId(), actor.accountId());
        }

        ReadingBookEntity saved = requireBook(actor, entity.getBookId());
        return new ReadingModels.BookMutationResult(toRow(saved), "書籍を登録しました。（" + saved.getBookNo() + "）");
    }

    @Override
    @Transactional
    public ReadingModels.BookMutationResult updateBook(UserPrincipal user, long bookId,
                                                       ReadingModels.BookSaveRequest request) {
        ReadingScope actor = scope(user);
        ReadingBookEntity current = requireBook(actor, bookId);
        requireManage(actor, current, "修正");
        int totalPages = requireTotalPages(request.totalPages());
        ReadingBookEntity entity = new ReadingBookEntity();
        entity.setBookId(bookId);
        // 言語を送ってこなければ今の値のまま（未設定の行は '英語' に寄せる）
        entity.setLanguage(choiceOrDefault(request.language(), ReadingModels.LANGUAGES, "言語",
                current.getLanguage() == null ? ReadingModels.DEFAULT_LANGUAGE : current.getLanguage()));
        entity.setTitle(request.title().trim());
        entity.setAuthor(request.author().trim());
        entity.setDifficulty(choiceOrDefault(request.difficulty(), ReadingModels.DIFFICULTIES, "難易度",
                current.getDifficulty()));
        entity.setStatus(choiceOrDefault(request.status(), ReadingModels.STATUSES, "読書ステータス",
                current.getStatus()));
        entity.setTotalPages(totalPages);
        entity.setCurrentPage(clampPage(request.currentPage(), totalPages, current.getCurrentPage()));
        entity.setPinned(request.pinned() == null ? Boolean.TRUE.equals(current.getPinned())
                : Boolean.TRUE.equals(request.pinned()));
        entity.setTags(formatTags(request.tags()));
        entity.setSummary(trimToNull(request.summary(), TEXT_MAX));
        entity.setNote(trimToNull(request.note(), TEXT_MAX));
        entity.setCategoryId(requireCategory(actor, request.categoryId()));
        entity.setUpdatedBy(user.accountId());
        entity.setVersion(request.version() == null ? current.getVersion() : request.version());

        if (bookMapper.update(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)), "書籍を更新しました。");
    }

    @Override
    @Transactional
    public ReadingModels.BookMutationResult setPinned(UserPrincipal user, long bookId, boolean pinned,
                                                      Integer version) {
        ReadingScope actor = scope(user);
        ReadingBookEntity current = requireBook(actor, bookId);
        // 置頂は本の属性なので、直せる人だけが変えられる
        // （管理者以外は全体書籍を置頂できない。決定 Q4）
        requireManage(actor, current, "置頂");
        int expected = version == null ? current.getVersion() : version;
        if (bookMapper.updatePinned(bookId, pinned, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)),
                pinned ? "書籍を置頂にしました。" : "書籍の置頂を解除しました。");
    }

    @Override
    @Transactional
    public ReadingModels.BookMutationResult setStatus(UserPrincipal user, long bookId, String status,
                                                      Integer version) {
        ReadingScope actor = scope(user);
        ReadingBookEntity current = requireBook(actor, bookId);
        String value = normalizeChoice(status, ReadingModels.STATUSES, "読書ステータス");
        if (value == null) {
            throw new ValidationException("読書ステータスを指定してください。");
        }
        int expected = version == null ? current.getVersion() : version;
        if (bookMapper.updateStatus(bookId, value, user.accountId(), expected) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)),
                "読書ステータスを「" + value + "」にしました。");
    }

    @Override
    @Transactional
    public ReadingModels.SimpleResult deleteBook(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        ReadingBookEntity current = requireBook(actor, bookId);
        requireManage(actor, current, "削除");
        // 行（RED_書籍ファイル情報）は CASCADE で消えるので、先に実体の場所を控えておく。
        // 2.0 から引き継いだ実体（legacy ルート）は読取専用なので消えない。
        List<ReadingFileEntity> files = fileMapper.findByBook(bookId);
        bookMapper.delete(bookId);
        for (ReadingFileEntity file : files) {
            storage.deleteStored(file.getStoredPath(), file.getStoredName());
        }
        return new ReadingModels.SimpleResult(1, "書籍を削除しました。");
    }

    // ------------------------------------------------------------ 読書記録

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.RecordListResult listRecords(UserPrincipal user, Long bookId, String dateFrom,
                                                      String dateTo, Integer pageNo, int page, int size) {
        ReadingScope actor = scope(user);
        // 本を指定したときは、その本が見えるかを先に確かめる（他家庭の本は 404）
        if (bookId != null) {
            requireBook(actor, bookId);
        }
        int safeSize = size <= 0 ? ReadingModels.DEFAULT_SIZE : Math.min(size, ReadingModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        LocalDate from = parseDate(dateFrom, "開始日");
        LocalDate to = parseDate(dateTo, "終了日");
        // dateTo は「その日いっぱい」を含める（翌日の 0 時より前）
        LocalDate toExclusive = to == null ? null : to.plusDays(1);
        Integer pageFilter = requirePageNo(pageNo);

        long total = recordMapper.count(bookId, from, toExclusive, pageFilter, actor.familyStudentId());
        List<ReadingModels.RecordRow> items = recordMapper.list(bookId, from, toExclusive, pageFilter,
                        actor.familyStudentId(), safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(ReadingServiceImpl::toRecordRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new ReadingModels.RecordListResult(items, total, safePage, safeSize, totalPages);
    }

    @Override
    @Transactional
    public ReadingModels.RecordMutationResult createRecord(UserPrincipal user, long bookId,
                                                           ReadingModels.RecordSaveRequest request) {
        ReadingScope actor = scope(user);
        ReadingBookEntity book = requireBook(actor, bookId);
        int totalPages = book.getTotalPages() == null ? 1 : book.getTotalPages();
        Timestamp readAt = request.readAt() == null || request.readAt().isBlank()
                ? Timestamp.valueOf(LocalDateTime.now())
                : parseTimestamp(request.readAt());
        if (readAt == null) {
            readAt = Timestamp.valueOf(LocalDateTime.now());
        }

        int pageEnd = clampPage(request.pageEnd(), totalPages, currentPageOf(book));
        int pageStart = clampPage(request.pageStart(), totalPages, pageEnd);
        int minutes = Math.max(0, request.minutes() == null ? 0 : request.minutes());
        int markCount = Math.max(0, request.markCount() == null ? 0 : request.markCount());

        ReadingRecordEntity entity = new ReadingRecordEntity();
        entity.setBookId(bookId);
        entity.setReadAt(readAt);
        entity.setPageStart(pageStart);
        entity.setPageEnd(pageEnd);
        entity.setMinutes(minutes);
        entity.setMarkCount(markCount);
        entity.setMemo(trimToNull(request.memo(), TEXT_MAX));
        entity.setCreatedBy(user.accountId());
        recordMapper.insert(entity);

        // 2.0 と同じ: 総ページに達したら 読了、それ以外は 読書中
        String status = pageEnd >= totalPages ? "読了" : "読書中";
        bookMapper.updateAfterRecord(bookId, pageEnd, status, minutes, readAt, user.accountId());

        ReadingRecordEntity saved = recordMapper.findById(entity.getRecordId());
        return new ReadingModels.RecordMutationResult(toRecordRow(saved), toRow(requireBook(actor, bookId)),
                "読書記録を保存しました。（P." + pageStart + " - P." + pageEnd + "）");
    }

    @Override
    @Transactional
    public ReadingModels.SimpleResult deleteRecord(UserPrincipal user, long recordId) {
        ReadingScope actor = scope(user);
        ReadingRecordEntity record = recordMapper.findById(recordId);
        if (record == null) {
            throw new NotFoundException("読書記録が見つかりません。");
        }
        // 見えない本の記録は「無い」ものとして扱う（他家庭の履歴を消させない）
        requireBook(actor, record.getBookId());
        recordMapper.delete(recordId);
        // 消したあとの記録から 最近/累計の読書時間・最終読書日時 を数え直す
        bookMapper.refreshRecordSummary(record.getBookId(), user.accountId());
        return new ReadingModels.SimpleResult(1, "読書履歴を削除しました。");
    }

    // -------------------------------------------------------------- 標記

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.MarkListResult listMarks(UserPrincipal user, long bookId, Integer pageNo, String markType,
                                                  int page, int size) {
        ReadingScope actor = scope(user);
        requireBook(actor, bookId);
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);
        int safePage = Math.max(1, page);
        String type = normalizeChoice(markType, ReadingModels.MARK_TYPES, "標記の種別");
        long total = markMapper.count(bookId, pageNo, type);
        List<ReadingModels.MarkRow> items = markMapper.list(bookId, pageNo, type, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(ReadingServiceImpl::toMarkRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new ReadingModels.MarkListResult(items, total, safePage, safeSize, totalPages,
                markMapper.count(bookId, null, "vocabulary"),
                markMapper.count(bookId, null, "highlight"),
                markMapper.count(bookId, null, "memo"),
                markMapper.count(bookId, null, "underline"));
    }

    @Override
    @Transactional
    public ReadingModels.MarkMutationResult createMark(UserPrincipal user, long bookId,
                                                       ReadingModels.MarkSaveRequest request) {
        ReadingScope actor = scope(user);
        ReadingBookEntity book = requireBook(actor, bookId);
        int totalPages = book.getTotalPages() == null ? 1 : book.getTotalPages();
        String type = normalizeChoice(request.markType(), ReadingModels.MARK_TYPES, "標記の種別");
        if (type == null) {
            throw new ValidationException("標記の種別を指定してください。");
        }
        int pageNo = Math.max(1, request.pageNo());
        if (pageNo > totalPages) {
            throw new ValidationException("ページ番号は総ページ数（" + totalPages + "）以内で指定してください。");
        }
        String target = trimToNull(request.targetText(), 500);
        String content = trimToNull(request.content(), TEXT_MAX);
        String drawing = trimToNull(request.drawingData(), DRAWING_MAX);
        // 手書き（pen）は点列（描画データ）だけでも成立する
        if (target == null && content == null && drawing == null) {
            throw new ValidationException("標記の内容を入力してください。");
        }

        ReadingMarkEntity entity = new ReadingMarkEntity();
        entity.setBookId(bookId);
        entity.setPageNo(pageNo);
        entity.setMarkType(type);
        entity.setTargetText(target);
        entity.setContent(content);
        entity.setColor(trimToNull(request.color(), 20));
        // PDF に重ねる座標（ページ内の正規化値 0〜1）。値は加工せずそのまま保存する。
        entity.setPositionX(requireRatio(request.positionX(), "位置X"));
        entity.setPositionY(requireRatio(request.positionY(), "位置Y"));
        entity.setWidth(requireRatio(request.width(), "幅"));
        entity.setHeight(requireRatio(request.height(), "高さ"));
        entity.setDrawingData(drawing);
        entity.setOrderNo(markMapper.nextOrderNo(bookId, pageNo));
        entity.setCreatedBy(user.accountId());
        markMapper.insert(entity);

        bookMapper.refreshMarkCount(bookId, user.accountId());
        ReadingMarkEntity saved = markMapper.findById(entity.getMarkId());
        return new ReadingModels.MarkMutationResult(toMarkRow(saved), toRow(requireBook(actor, bookId)),
                "標記を追加しました。（P." + pageNo + "）");
    }

    @Override
    @Transactional
    public ReadingModels.BookMutationResult deleteMark(UserPrincipal user, long markId) {
        ReadingScope actor = scope(user);
        ReadingMarkEntity mark = markMapper.findById(markId);
        if (mark == null) {
            throw new NotFoundException("標記が見つかりません。");
        }
        // 見えない本の標記は「無い」ものとして扱う
        requireBook(actor, mark.getBookId());
        markMapper.delete(markId);
        bookMapper.refreshMarkCount(mark.getBookId(), user.accountId());
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, mark.getBookId())),
                "標記を削除しました。");
    }

    /**
     * その書籍の標記と読書記録（履歴）をすべて削除し、あわせて読書の進捗もリセットする
     * （画面の【標記クリア】。2026-09-14 の指示で読書記録も消すことにした）。
     *
     * <p>標記の削除・読書記録の削除・進捗（現在ページ・ステータス・最近/累計の読書時間・最終読書日時）の
     * リセットは 1 つのトランザクションで行い、片方だけ残らないようにする。
     * 記録も消すので、リセット後の累計読書時間・最終読書日時と残った記録が食い違うことはない。</p>
     */
    @Override
    @Transactional
    public ReadingModels.BookMutationResult resetMarksAndProgress(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        requireBook(actor, bookId);
        int removedMarks = markMapper.deleteByBook(bookId);
        int removedRecords = recordMapper.deleteByBook(bookId);
        bookMapper.refreshMarkCount(bookId, user.accountId());
        bookMapper.resetProgress(bookId, user.accountId());
        String message = "標記 " + removedMarks + " 件・読書記録 " + removedRecords
                + " 件と読書の進捗（現在ページ・ステータス）をリセットしました。";
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)), message);
    }

    // -------------------------------------------------------------- 自分の本棚

    /**
     * 【本棚に入れる】（2026-09-14 の決定で新設。本棚は**アカウントごと**）。
     *
     * <p>触るのは**呼び出した本人の行だけ**（ボディで他人のアカウントID は受け取らない）。
     * `UNIQUE (アカウントID, 書籍ID)` に `ON CONFLICT DO NOTHING` なので**冪等**
     * （何度押しても 1 行・件数は増えない）。見えない本は 404。</p>
     */
    @Override
    @Transactional
    public ReadingModels.BookMutationResult addToShelf(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        requireBook(actor, bookId);
        bookMapper.insertShelf(bookId, actor.accountId(), actor.accountId());
        ReadingBookEntity saved = requireBook(actor, bookId);
        saved.setInMyShelf(true);
        return new ReadingModels.BookMutationResult(toRow(saved),
                "本棚に入れました。【自分の本棚】から読めます。");
    }

    /** 【本棚から外す】。入っていなければ何もしない（冪等）。 */
    @Override
    @Transactional
    public ReadingModels.BookMutationResult removeFromShelf(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        requireBook(actor, bookId);
        bookMapper.deleteShelf(bookId, actor.accountId());
        ReadingBookEntity saved = requireBook(actor, bookId);
        saved.setInMyShelf(false);
        return new ReadingModels.BookMutationResult(toRow(saved), "本棚から外しました。");
    }

    // -------------------------------------------------------------------- 内部

    /**
     * 見える本を 1 冊引く。**見えない本（他家庭の FAMILY・管理者から見た FAMILY）は
     * 「無い」ものとして 404** にする（本の存在を漏らさない）。
     */
    private ReadingBookEntity requireBook(ReadingScope actor, long bookId) {
        ReadingBookEntity book = bookMapper.findById(bookId);
        if (book == null || !canView(actor, book)) {
            throw new NotFoundException("書籍が見つかりません。");
        }
        return book;
    }

    /** その本が見えるか（全体書籍か、自分の家庭の書籍か）。 */
    private static boolean canView(ReadingScope actor, ReadingBookEntity book) {
        if (ReadingScope.SCOPE_GLOBAL.equals(book.getScope())) {
            return true;
        }
        return actor.familyStudentId() != null
                && actor.familyStudentId().equals(book.getOwnerFamilyId());
    }

    /**
     * 操作している人の可視範囲（ロールと家庭）を求める。
     *
     * <p>家庭は既存の「保護者—生徒」の紐付けから解決する（新しい家庭ID は作らない）:
     * 生徒は自分のアカウントID、保護者は {@code ACC_アカウント.保護者ID} から引いた
     * 生徒のアカウントID。管理者は家庭を持たない（null＝全体書籍だけが見える）。</p>
     */
    private ReadingScope scope(UserPrincipal user) {
        if (user == null || user.accountType() == null) {
            throw new UnauthenticatedException("ログインが必要です。");
        }
        switch (user.accountType()) {
            case ADMIN:
                return new ReadingScope(user.accountId(), user.accountType(), null);
            case STUDENT:
                return new ReadingScope(user.accountId(), user.accountType(), user.accountId());
            default:
                AccountEntity student = accountMapper.findStudentByGuardianId(user.accountId());
                return new ReadingScope(user.accountId(), user.accountType(),
                        student == null ? null : student.getAccountId());
        }
    }

    /**
     * 書籍・分類を**登録**できる人か（生徒は読むだけ。決定 Q9）。
     * 家庭が特定できない保護者は登録させない（資料管理の
     * 「学生と保護者の紐付けを一意に特定できません。」と同じ扱い）。
     */
    private static void requireManageRole(ReadingScope actor, String action) {
        if (!actor.canManageBooks()) {
            throw ReadingAccessException.roleNotAllowed(action);
        }
        if (!actor.isAdmin() && actor.familyStudentId() == null) {
            throw new ValidationException("学生と保護者の紐付けを一意に特定できません。");
        }
    }

    /** その本を操作してよいか（ロール × 所有）。見えない本はここへ来る前に 404 になっている。 */
    private static void requireManage(ReadingScope actor, ReadingBookEntity book, String action) {
        if (!actor.canManageBooks()) {
            throw ReadingAccessException.roleNotAllowed(action);
        }
        if (!actor.canEdit(book)) {
            throw ReadingAccessException.notOwner(action);
        }
    }

    /**
     * 登録・修正で送られてきた公開範囲の検査。
     * 保護者（＝家庭の書籍だけを作れる人）が `GLOBAL` を送っても無視する
     * （サーバが FAMILY に固定する）。管理者が `FAMILY` を送ったときは 403。
     */
    private static void rejectScopeOverride(ReadingScope actor, String requestedScope) {
        String value = blankToNull(requestedScope);
        if (value == null) {
            return;
        }
        if (!ReadingScope.SCOPE_GLOBAL.equals(value) && !ReadingScope.SCOPE_FAMILY.equals(value)) {
            throw new ValidationException("公開範囲は GLOBAL / FAMILY のいずれかを指定してください。");
        }
        if (actor.isAdmin() && ReadingScope.SCOPE_FAMILY.equals(value)) {
            throw ReadingAccessException.roleNotAllowed("家庭の書籍の登録");
        }
    }

    /**
     * 一覧の `scope`（`ALL`＝全体書籍＋自分の家庭（既定）/ `GLOBAL`＝全体書籍だけ /
     * `FAMILY`＝自分の家庭だけ）。家庭を持たない管理者が `FAMILY` を選ぶと 0 冊になる。
     */
    private static String normalizeScopeFilter(String scope) {
        String value = blankToNull(scope);
        if (value == null || ReadingScope.SCOPE_ALL.equals(value)) {
            return ReadingScope.SCOPE_ALL;
        }
        if (ReadingScope.SCOPE_GLOBAL.equals(value) || ReadingScope.SCOPE_FAMILY.equals(value)) {
            return value;
        }
        throw new ValidationException("公開範囲は ALL / GLOBAL / FAMILY のいずれかを指定してください。");
    }

    /** 一覧の `shelf`（`MINE` だけを受け付ける）。 */
    private static boolean isMyShelf(String shelf) {
        String value = blankToNull(shelf);
        if (value == null) {
            return false;
        }
        if (ReadingScope.SHELF_MINE.equalsIgnoreCase(value)) {
            return true;
        }
        throw new ValidationException("本棚の指定は MINE だけを受け付けます。");
    }

    /** 2.0 と同じ ER-yyyyMMdd-HHmmss。1 秒以内の連続登録は枝番を付ける。 */    private String nextBookNo() {
        String base = "ER-" + LocalDateTime.now().format(BOOK_NO_FORMAT);
        String candidate = base;
        int suffix = 1;
        while (bookMapper.findByNo(candidate) != null) {
            suffix += 1;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private static int requireTotalPages(int totalPages) {
        if (totalPages < 1) {
            throw new ValidationException("総ページ数は1以上で入力してください。");
        }
        return totalPages;
    }

    private static int currentPageOf(ReadingBookEntity book) {
        int total = book.getTotalPages() == null ? 1 : book.getTotalPages();
        return clampPage(book.getCurrentPage(), total, 1);
    }

    private static int clampPage(Integer page, int totalPages, int fallback) {
        if (page == null) {
            return Math.max(1, Math.min(fallback, Math.max(1, totalPages)));
        }
        return Math.max(1, Math.min(page, Math.max(1, totalPages)));
    }

    /** 選択肢（難易度・ステータス・標記種別）の検証。空は null。 */
    private static String normalizeChoice(String value, List<String> allowed, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (!allowed.contains(text)) {
            throw new ValidationException(label + "は " + String.join(" / ", allowed) + " のいずれかを指定してください。");
        }
        return text;
    }

    private static String choiceOrDefault(String value, List<String> allowed, String label, String fallback) {
        String normalized = normalizeChoice(value, allowed, label);
        return normalized == null ? (fallback == null ? allowed.get(0) : fallback) : normalized;
    }

    /** タグは カンマ区切りのテキストで保存する（2.0 と同じ形式）。 */
    private static String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = blankToNull(tag);
            if (value == null) {
                continue;
            }
            if (value.length() > TAG_MAX) {
                throw new ValidationException("タグは" + TAG_MAX + "文字以内で入力してください。");
            }
            unique.add(value);
            if (unique.size() >= TAG_COUNT_MAX) {
                break;
            }
        }
        return unique.isEmpty() ? null : String.join(",", unique);
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : tags.split("[,\u3001]")) {
            String value = blankToNull(part);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static Timestamp parseTimestamp(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(text).toInstant());
        } catch (RuntimeException ignored) {
            // ローカル日時として解釈する
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text));
        } catch (RuntimeException ignored) {
            // 自動生成の値にフォールバックする
        }
        try {
            return Timestamp.from(java.time.Instant.parse(text));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimToNull(String value, int max) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String iso(Timestamp value, ZoneId zone) {
        return value == null ? null : value.toLocalDateTime().toString();
    }

    private ReadingModels.BookRow toRow(ReadingBookEntity entity) {
        int totalPages = entity.getTotalPages() == null ? 1 : entity.getTotalPages();
        int currentPage = entity.getCurrentPage() == null ? 1 : entity.getCurrentPage();
        int percent = totalPages <= 0 ? 0 : (int) Math.min(100, Math.round(currentPage * 100.0 / totalPages));
        ZoneId zone = ZoneId.systemDefault();
        boolean hasPdf = entity.getPdfFileId() != null;
        boolean hasCover = entity.getCoverFileId() != null;
        return new ReadingModels.BookRow(
                entity.getBookId() == null ? 0L : entity.getBookId(),
                entity.getBookNo(),
                entity.getSubject(),
                entity.getTitle(),
                entity.getAuthor(),
                entity.getDifficulty(),
                entity.getStatus(),
                totalPages,
                currentPage,
                Boolean.TRUE.equals(entity.getPinned()),
                parseTags(entity.getTags()),
                entity.getSummary(),
                entity.getNote(),
                entity.getRecentMinutes() == null ? 0 : entity.getRecentMinutes(),
                entity.getTotalMinutes() == null ? 0 : entity.getTotalMinutes(),
                entity.getMarkCount() == null ? 0 : entity.getMarkCount(),
                iso(entity.getLastReadAt(), zone),
                percent,
                entity.getVersion() == null ? 1 : entity.getVersion(),
                iso(entity.getCreatedAt(), zone),
                iso(entity.getUpdatedAt(), zone),
                entity.getCategoryId(),
                entity.getCategoryName(),
                hasPdf,
                entity.getPdfOriginalName(),
                hasPdf && storage.exists(entity.getPdfStoredPath(), entity.getPdfStoredName()),
                hasCover,
                hasCover && storage.exists(entity.getCoverStoredPath(), entity.getCoverStoredName()),
                entity.getLanguage(),
                entity.getScope() == null ? ReadingScope.SCOPE_GLOBAL : entity.getScope(),
                entity.getOwnerFamilyId(),
                entity.getOwnerFamilyLabel(),
                Boolean.TRUE.equals(entity.getInMyShelf()));
    }

    // ------------------------------------------------------------ 分類（マスタ）

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.CategoryListResult listCategories(UserPrincipal user) {
        ReadingScope actor = scope(user);
        List<ReadingModels.CategoryRow> items = categoryMapper.list(actor.familyStudentId()).stream()
                .map(ReadingServiceImpl::toCategoryRow)
                .toList();
        return new ReadingModels.CategoryListResult(items);
    }

    @Override
    @Transactional
    public ReadingModels.CategoryMutationResult createCategory(UserPrincipal user,
                                                               ReadingModels.CategorySaveRequest request) {
        ReadingScope actor = scope(user);
        requireManageRole(actor, "分類の登録");
        // 管理者＝全体の分類（所有家族なし）/ 保護者＝自分の家庭の分類
        Long ownerFamilyId = actor.categoryOwnerFamilyId();
        String name = requireCategoryName(request.name(), null, ownerFamilyId);
        ReadingCategoryEntity entity = new ReadingCategoryEntity();
        entity.setName(name);
        entity.setDisplayOrder(requireDisplayOrder(request.displayOrder(),
                categoryMapper.nextDisplayOrder(ownerFamilyId)));
        entity.setDescription(trimToNull(request.description(), TEXT_MAX));
        entity.setOwnerFamilyId(ownerFamilyId);
        entity.setCreatedBy(user.accountId());
        categoryMapper.insert(entity);
        return new ReadingModels.CategoryMutationResult(
                toCategoryRow(requireCategoryById(actor, entity.getCategoryId())),
                "分類「" + name + "」を登録しました。");
    }

    @Override
    @Transactional
    public ReadingModels.CategoryMutationResult updateCategory(UserPrincipal user, long categoryId,
                                                               ReadingModels.CategorySaveRequest request) {
        ReadingScope actor = scope(user);
        requireManageRole(actor, "分類の修正");
        ReadingCategoryEntity current = requireCategoryById(actor, categoryId);
        requireCategoryOwner(actor, current, "修正");
        String name = requireCategoryName(request.name(), categoryId, current.getOwnerFamilyId());
        ReadingCategoryEntity entity = new ReadingCategoryEntity();
        entity.setCategoryId(categoryId);
        entity.setName(name);
        entity.setDisplayOrder(requireDisplayOrder(request.displayOrder(),
                current.getDisplayOrder() == null ? 0 : current.getDisplayOrder()));
        entity.setDescription(trimToNull(request.description(), TEXT_MAX));
        entity.setUpdatedBy(user.accountId());
        categoryMapper.update(entity);
        return new ReadingModels.CategoryMutationResult(toCategoryRow(requireCategoryById(actor, categoryId)),
                "分類「" + name + "」を更新しました。");
    }

    @Override
    @Transactional
    public ReadingModels.SimpleResult deleteCategory(UserPrincipal user, long categoryId) {
        ReadingScope actor = scope(user);
        requireManageRole(actor, "分類の削除");
        ReadingCategoryEntity current = requireCategoryById(actor, categoryId);
        requireCategoryOwner(actor, current, "削除");
        long bookCount = categoryMapper.countBooks(categoryId, actor.familyStudentId());
        // 本は消さない（RED_書籍情報.分類ID が ON DELETE SET NULL で NULL に戻り未分類になる）
        categoryMapper.delete(categoryId);
        String message = bookCount > 0
                ? "分類「" + current.getName() + "」を削除しました。（" + bookCount + " 冊は未分類になりました）"
                : "分類「" + current.getName() + "」を削除しました。";
        return new ReadingModels.SimpleResult((int) bookCount, message);
    }

    // ------------------------------------------------------ 本文 PDF・表紙画像

    @Override
    @Transactional
    public ReadingModels.BookMutationResult uploadPdf(UserPrincipal user, long bookId, MultipartFile file,
                                                      Integer totalPages) {
        ReadingScope actor = scope(user);
        ReadingBookEntity book = requireBook(actor, bookId);
        requireManage(actor, book, "本文 PDF の登録");
        if (totalPages != null && totalPages < 1) {
            throw new ValidationException("総ページ数は1以上で指定してください。");
        }
        ReadingFileStorage.StoredFile stored = storage.storePdf(book.getBookNo(), file);
        ReadingFileEntity existing = fileMapper.find(bookId, ReadingModels.FILE_KIND_PDF);
        // 行を書き換えると古い場所が分からなくなるので、先に控える
        String oldStoredPath = existing == null ? null : existing.getStoredPath();
        String oldStoredName = existing == null ? null : existing.getStoredName();
        saveFile(bookId, ReadingModels.FILE_KIND_PDF, stored, totalPages, existing, user);
        // 差し替え: 新しい実体を置いてから古い実体を消す（読めない時間を作らない）
        if (existing != null) {
            storage.deleteStored(oldStoredPath, oldStoredName);
        }
        if (totalPages != null) {
            bookMapper.updateTotalPages(bookId, totalPages, user.accountId());
        }
        boolean replaced = existing != null;
        String message = (replaced ? "本文 PDF を差し替えました。" : "本文 PDF を登録しました。")
                + "（" + stored.originalName() + "）";
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)), message);
    }

    @Override
    @Transactional
    public ReadingModels.SimpleResult deletePdf(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        requireManage(actor, requireBook(actor, bookId), "本文 PDF の削除");
        ReadingFileEntity file = fileMapper.find(bookId, ReadingModels.FILE_KIND_PDF);
        if (file == null) {
            return new ReadingModels.SimpleResult(0, "本文 PDF は登録されていません。");
        }
        fileMapper.delete(file.getFileId());
        storage.deleteStored(file.getStoredPath(), file.getStoredName());
        return new ReadingModels.SimpleResult(1, "本文 PDF を削除しました。");
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.FileDownload downloadPdf(UserPrincipal user, long bookId) {
        return download(scope(user), bookId, ReadingModels.FILE_KIND_PDF, "本文 PDF が登録されていません。",
                "PDF が見つかりません。");
    }

    @Override
    @Transactional
    public ReadingModels.BookMutationResult uploadCover(UserPrincipal user, long bookId, MultipartFile file) {
        ReadingScope actor = scope(user);
        ReadingBookEntity book = requireBook(actor, bookId);
        requireManage(actor, book, "表紙の登録");
        ReadingFileStorage.StoredFile stored = storage.storeCover(book.getBookNo(), file);
        ReadingFileEntity existing = fileMapper.find(bookId, ReadingModels.FILE_KIND_COVER);
        String oldStoredPath = existing == null ? null : existing.getStoredPath();
        String oldStoredName = existing == null ? null : existing.getStoredName();
        saveFile(bookId, ReadingModels.FILE_KIND_COVER, stored, null, existing, user);
        if (existing != null) {
            storage.deleteStored(oldStoredPath, oldStoredName);
        }
        String message = existing != null ? "表紙を差し替えました。" : "表紙を登録しました。";
        return new ReadingModels.BookMutationResult(toRow(requireBook(actor, bookId)), message);
    }

    @Override
    @Transactional
    public ReadingModels.SimpleResult deleteCover(UserPrincipal user, long bookId) {
        ReadingScope actor = scope(user);
        requireManage(actor, requireBook(actor, bookId), "表紙の削除");
        ReadingFileEntity file = fileMapper.find(bookId, ReadingModels.FILE_KIND_COVER);
        if (file == null) {
            return new ReadingModels.SimpleResult(0, "表紙は登録されていません。");
        }
        fileMapper.delete(file.getFileId());
        storage.deleteStored(file.getStoredPath(), file.getStoredName());
        return new ReadingModels.SimpleResult(1, "表紙を削除しました。");
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingModels.FileDownload downloadCover(UserPrincipal user, long bookId) {
        return download(scope(user), bookId, ReadingModels.FILE_KIND_COVER, "表紙が登録されていません。",
                "表紙が見つかりません。");
    }

    /**
     * 配信するファイルの場所を返す。行が無い・実体が無い・2.0 のツリー未設定（legacy ルートが空）
     * は 404 にして、画面が「PDF 未登録」を出せるようにする。
     */
    private ReadingModels.FileDownload download(ReadingScope actor, long bookId, String fileKind,
                                                String missingRowMessage, String missingFileMessage) {
        requireBook(actor, bookId);
        ReadingFileEntity file = fileMapper.find(bookId, fileKind);
        if (file == null) {
            throw new NotFoundException(missingRowMessage);
        }
        Path path = storage.resolve(file.getStoredPath(), file.getStoredName());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException(missingFileMessage);
        }
        String fileName = file.getOriginalName() == null || file.getOriginalName().isBlank()
                ? file.getStoredName() : file.getOriginalName();
        return new ReadingModels.FileDownload(path,
                storage.contentType(file.getMimeType(), file.getStoredName()), fileName,
                file.getFileSize() == null ? fileSizeOf(path) : file.getFileSize());
    }

    private void saveFile(long bookId, String fileKind, ReadingFileStorage.StoredFile stored, Integer pageCount,
                          ReadingFileEntity existing, UserPrincipal user) {
        if (existing == null) {
            ReadingFileEntity entity = new ReadingFileEntity();
            entity.setBookId(bookId);
            entity.setFileKind(fileKind);
            entity.setOriginalName(stored.originalName());
            entity.setStoredName(stored.storedName());
            entity.setStoredPath(stored.relativePath());
            entity.setMimeType(stored.contentType());
            entity.setFileSize(stored.fileSize());
            entity.setPageCount(pageCount);
            entity.setCreatedBy(user.accountId());
            fileMapper.insert(entity);
            return;
        }
        existing.setOriginalName(stored.originalName());
        existing.setStoredName(stored.storedName());
        existing.setStoredPath(stored.relativePath());
        existing.setMimeType(stored.contentType());
        existing.setFileSize(stored.fileSize());
        // totalPages を省略した差し替えでは、分かっているページ数を消さない
        existing.setPageCount(pageCount == null ? existing.getPageCount() : pageCount);
        existing.setUpdatedBy(user.accountId());
        fileMapper.update(existing);
    }

    private static long fileSizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException ex) {
            return 0L;
        }
    }

    /** 見える分類を 1 件引く（全体の分類か、自分の家庭の分類）。見えない分類は 404。 */
    private ReadingCategoryEntity requireCategoryById(ReadingScope actor, long categoryId) {
        ReadingCategoryEntity category = categoryMapper.findById(categoryId, actor.familyStudentId());
        if (category == null) {
            throw new NotFoundException("分類が見つかりません。");
        }
        return category;
    }

    /**
     * その分類を直してよいか。管理者は全体の分類だけ、保護者は自分の家庭の分類だけ
     * （見えない分類は 404 なので、ここへ来るのは「見えるが持ち主が違う」ときだけ）。
     */
    private static void requireCategoryOwner(ReadingScope actor, ReadingCategoryEntity category, String action) {
        Long owner = category.getOwnerFamilyId();
        boolean allowed = actor.isAdmin()
                ? owner == null
                : owner != null && owner.equals(actor.familyStudentId());
        if (!allowed) {
            throw ReadingAccessException.notOwner("分類の" + action);
        }
    }

    /** 分類名: 前後空白を落として 1〜50 文字。同じ持ち主の中での重複は 400。 */
    private String requireCategoryName(String name, Long excludeId, Long ownerFamilyId) {
        String value = blankToNull(name);
        if (value == null) {
            throw new ValidationException("分類名を入力してください。");
        }
        if (value.length() > ReadingModels.CATEGORY_NAME_MAX) {
            throw new ValidationException("分類名は" + ReadingModels.CATEGORY_NAME_MAX + "文字以内で入力してください。");
        }
        if (categoryMapper.findByName(value, excludeId, ownerFamilyId) != null) {
            throw new ValidationException("同じ名前の分類「" + value + "」がすでにあります。");
        }
        return value;
    }

    private static int requireDisplayOrder(Integer displayOrder, int fallback) {
        int value = displayOrder == null ? fallback : displayOrder;
        if (value < 0) {
            throw new ValidationException("表示順は0以上で指定してください。");
        }
        return value;
    }

    /**
     * 書籍の分類ID。null は未分類。0 や存在しない ID は 400。
     * **見えない分類（他家庭の分類・管理者から見た家庭の分類）も 400** にする
     * （本に付けられるのは自分に見える分類だけ）。
     */
    private Long requireCategory(ReadingScope actor, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        if (categoryId <= 0) {
            throw new ValidationException("分類の指定が正しくありません。");
        }
        if (categoryMapper.findById(categoryId, actor.familyStudentId()) == null) {
            throw new ValidationException("指定された分類が見つかりません。");
        }
        return categoryId;
    }

    /** 読書履歴の日付（yyyy-MM-dd）。空は null、解釈できない値は 400。 */
    private static LocalDate parseDate(String value, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(label + "は yyyy-MM-dd の形式で入力してください。");
        }
    }

    /** 読書履歴のページ番号（1 以上）。空は null。 */
    private static Integer requirePageNo(Integer pageNo) {
        if (pageNo == null) {
            return null;
        }
        if (pageNo < 1) {
            throw new ValidationException("ページ番号は1以上で指定してください。");
        }
        return pageNo;
    }

    /** 一覧の絞り込み（0＝未分類のみ / 正の値＝その分類 / null＝すべて）。 */
    private static Long normalizeCategoryFilter(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        if (categoryId < 0) {
            throw new ValidationException("分類の指定が正しくありません。");
        }
        return categoryId;
    }

    /** 標記の座標（ページ内の正規化値 0〜1）。DDL の CHECK と同じ範囲を先に検査する。 */
    private static BigDecimal requireRatio(BigDecimal value, String label) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException(label + "は 0〜1 の範囲で指定してください。");
        }
        return value;
    }

    private static ReadingModels.CategoryRow toCategoryRow(ReadingCategoryEntity entity) {
        return new ReadingModels.CategoryRow(
                entity.getCategoryId() == null ? 0L : entity.getCategoryId(),
                entity.getName(),
                entity.getDisplayOrder() == null ? 0 : entity.getDisplayOrder(),
                entity.getDescription(),
                entity.getBookCount() == null ? 0L : entity.getBookCount(),
                entity.getOwnerFamilyId());
    }

    private static ReadingModels.RecordRow toRecordRow(ReadingRecordEntity entity) {
        return new ReadingModels.RecordRow(
                entity.getRecordId() == null ? 0L : entity.getRecordId(),
                entity.getBookId() == null ? 0L : entity.getBookId(),
                entity.getBookTitle(),
                iso(entity.getReadAt(), ZoneId.systemDefault()),
                entity.getPageStart(),
                entity.getPageEnd(),
                entity.getMinutes() == null ? 0 : entity.getMinutes(),
                entity.getMarkCount() == null ? 0 : entity.getMarkCount(),
                entity.getMemo());
    }

    private static ReadingModels.MarkRow toMarkRow(ReadingMarkEntity entity) {
        return new ReadingModels.MarkRow(
                entity.getMarkId() == null ? 0L : entity.getMarkId(),
                entity.getBookId() == null ? 0L : entity.getBookId(),
                entity.getPageNo() == null ? 0 : entity.getPageNo(),
                entity.getMarkType(),
                entity.getTargetText(),
                entity.getContent(),
                entity.getColor(),
                iso(entity.getCreatedAt(), ZoneId.systemDefault()),
                entity.getPositionX(),
                entity.getPositionY(),
                entity.getWidth(),
                entity.getHeight(),
                entity.getDrawingData());
    }
}
