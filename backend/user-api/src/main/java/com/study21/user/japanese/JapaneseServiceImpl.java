package com.study21.user.japanese;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.japanese.JpnWordDetailAssembler;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEditorComposer;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import com.study21.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日本語勉強の実装（2.0 の日本語機能を 2.1 の 3 画面向けに作り直したもの）。
 *
 * <p>2.0 から引き継いだ規則:</p>
 * <ul>
 *   <li>テスト種別は A〜E。C は読み・漢字、D は文脈の意味、E は漢字の使い方。
 *       A・B は 2.0 に問題テーブルが無く、単語（見出し語・読み）から出題する。</li>
 *   <li>単語の習得度は**技能（テスト種別 × 技能区分）ごと**に持ち、
 *       学習状況の 総合習得度 はその平均。平均 80 以上で MASTERED、
 *       直前が誤答なら REVIEW、それ以外は LEARNING（2.0 と同じ）。</li>
 *   <li>テストの進捗（完了数・正解数・不正解数・学習時間）はテストと出題の両方に記録する。</li>
 * </ul>
 *
 * <p>復習間隔は 2.1 の規則（正解なら間隔を 2 倍に延ばし、誤答なら 1 日に戻す）で、
 * 習得度は 1 問 ±20 の増減。2.0 の詳細な間隔表は移行していない（設計書に記載）。</p>
 */
@Service
public class JapaneseServiceImpl implements JapaneseService {

    private static final Logger log = LoggerFactory.getLogger(JapaneseServiceImpl.class);

    private static final DateTimeFormatter TEST_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_REVIEW_INTERVAL_DAYS = 30;
    private static final BigDecimal MASTERY_STEP = BigDecimal.valueOf(20);
    private static final BigDecimal MASTERED_THRESHOLD = BigDecimal.valueOf(80);

    /** テスト種別 → 出題する問題種別（2.0 の問題テーブルに合わせる）。 */
    private static final Map<String, List<String>> QUESTION_TYPES = Map.of(
            "C", List.of("C1_READING", "C2_KANJI"),
            "D", List.of("D_CONTEXT_MEANING"),
            "E", List.of("E_KANJI_USAGE"));

    /** AI 取得の取得区分（画面の A・B／C／D／E に対応）。 */
    private static final List<String> AI_KINDS = List.of("DETAIL", "C", "D", "E");

    /** AI 取得の対象を選ぶときに許す上限（設定ページの「1 回の最大単語数」の最大）。 */
    private static final int AI_TARGET_LIMIT_MAX = 200;

    /**
     * 問題種別 → 技能区分（2.0 の技能習得情報に合わせる）。
     *
     * <p>A（勉強）は入れない。`JPN_技能習得情報` の CHECK が B〜E の技能区分だけを許すため
     * （A の確認は `JPN_学習状況情報.A確認回数` で数える）。</p>
     */
    private static final Map<String, String> SKILL_CODES = Map.of(
            "C1_READING", "C_READING_RECOGNITION",
            "C2_KANJI", "C_KANJI_RECOGNITION",
            "D_CONTEXT_MEANING", "D_CONTEXT_MEANING",
            "E_KANJI_USAGE", "E_KANJI_USAGE",
            "B_ORTHOGRAPHY", "B_ORTHOGRAPHY",
            "B_READING_RECALL", "B_READING_RECALL");

    /** 1 問で見せる選択肢の数（正解 1 ＋ 誤答 3）。プール（5〜7 件）からここだけ選ぶ。 */
    private static final int PROMPTED_CHOICE_COUNT = 4;

    /** 出題選択肢JSON が持つキー（形を 1 本にする。A・B も同じ形で書く）。 */
    private static final String CHOICE_ID_KEY = "choiceId";
    private static final String CHOICE_VALUE_KEY = "value";
    private static final String CHOICE_READING_KEY = "reading";
    private static final String CHOICE_CORRECT_KEY = "correct";

    private final JpnWordMapper wordMapper;
    private final JpnWordDetailMapper detailMapper;
    private final JpnTestMapper testMapper;
    private final JpnStatusMapper statusMapper;
    private final ObjectMapper objectMapper;
    /** 選択肢を選ぶ・並べ替えるための乱数（テストから種を固定できるように外から渡せる）。 */
    private final java.util.Random random;

    /**
     * Spring が使うコンストラクタ（乱数は既定のものを使う）。
     *
     * <p>テストから選択肢の抽選を再現できるように、乱数を渡すコンストラクタも用意してある。
     * 候補が 2 つあると Spring は既定コンストラクタを探してしまうので、こちらに目印を付ける。</p>
     */
    @org.springframework.beans.factory.annotation.Autowired
    public JapaneseServiceImpl(JpnWordMapper wordMapper, JpnWordDetailMapper detailMapper,
                               JpnTestMapper testMapper, JpnStatusMapper statusMapper,
                               ObjectMapper objectMapper) {
        this(wordMapper, detailMapper, testMapper, statusMapper, objectMapper, new java.util.Random());
    }

    public JapaneseServiceImpl(JpnWordMapper wordMapper, JpnWordDetailMapper detailMapper,
                               JpnTestMapper testMapper, JpnStatusMapper statusMapper,
                               ObjectMapper objectMapper, java.util.Random random) {
        this.wordMapper = wordMapper;
        this.detailMapper = detailMapper;
        this.testMapper = testMapper;
        this.statusMapper = statusMapper;
        this.objectMapper = objectMapper;
        this.random = random;
    }

    // ================================================================ 単語

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordListResult searchWords(long accountId, String keyword, String reading, String jlpt,
                                                     String part, String state, String book,
                                                     String categoryFrom, String categoryTo,
                                                     String learnState, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String learnStateFilter = normalizeChoice(learnState, JapaneseModels.LEARN_STATES, "学習状態");
        String stateFilter = normalizeChoice(state, List.of("ACTIVE", "INACTIVE"), "状態");
        // 分類は範囲（From ～ To）。片方だけでもよい（空は「制限なし」）
        String categoryFromFilter = blankToNull(categoryFrom);
        String categoryToFilter = blankToNull(categoryTo);

        long total = wordMapper.count(blankToNull(keyword), blankToNull(reading), blankToNull(jlpt),
                blankToNull(part), stateFilter, blankToNull(book), categoryFromFilter, categoryToFilter,
                learnStateFilter, accountId);
        List<JpnWordEntity> rows = wordMapper.search(blankToNull(keyword), blankToNull(reading),
                blankToNull(jlpt), blankToNull(part), stateFilter, blankToNull(book),
                categoryFromFilter, categoryToFilter,
                learnStateFilter, accountId, safeSize, (safePage - 1) * safeSize);
        // 一覧の「詳細情報件数」列: 出てくる語だけをまとめて数える（1 語ずつ引かない）。
        // 詳細がまだ無い語は行が返らない＝null（画面は「—」）
        Map<Long, JapaneseModels.WordDetailCounts> detailCounts = detailCountsOf(rows);
        List<JapaneseModels.WordRow> items = rows.stream()
                .map(row -> toWordRow(row, detailCounts.get(row.getWordId())))
                .toList();
        JpnTotalsEntity totals = wordMapper.totals(accountId);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.WordListResult(items, total, safePage, safeSize, totalPages,
                new JapaneseModels.WordTotals(totals.words(), totals.learned(), totals.favorites(),
                        totals.mastery(), totals.answered()));
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.AiTargets aiTargets(long accountId, String keyword, String reading, String jlpt,
                                              String part, String state, String book,
                                              String categoryFrom, String categoryTo, String learnState,
                                              String kind, boolean skipAcquired, int limit) {
        String kindFilter = normalizeChoice(kind, AI_KINDS, "取得区分");
        if (kindFilter == null) {
            throw new ValidationException("取得区分は " + String.join(" / ", AI_KINDS)
                    + " のいずれかを指定してください。");
        }
        String stateFilter = normalizeChoice(state, List.of("ACTIVE", "INACTIVE"), "状態");
        String learnStateFilter = normalizeChoice(learnState, JapaneseModels.LEARN_STATES, "学習状態");
        String categoryFromFilter = blankToNull(categoryFrom);
        String categoryToFilter = blankToNull(categoryTo);
        // 画面が設定値から渡す。サーバーでも丸める（受付の API が最終的な上限を検証する）
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? AI_TARGET_LIMIT_MAX : limit, AI_TARGET_LIMIT_MAX));

        JpnAiTargetCountsEntity counts = wordMapper.countAiTargets(
                blankToNull(keyword), blankToNull(reading), blankToNull(jlpt), blankToNull(part),
                stateFilter, blankToNull(book), categoryFromFilter, categoryToFilter,
                learnStateFilter, accountId, kindFilter);
        long total = counts == null ? 0 : counts.getTotal();
        long acquired = counts == null ? 0 : counts.getAcquired();
        // 取得済み・取得中を除いた数（＝「取得済みをスキップ」を選んだときの対象数）
        long skippable = counts == null ? 0 : counts.getCandidates();
        // 「すべて再取得」は一致する語をそのまま対象にする（取得済みも新しい版で取り直す）
        long candidates = skipAcquired ? skippable : total;

        List<Long> wordIds = candidates == 0 ? List.of() : wordMapper.findAiTargets(
                blankToNull(keyword), blankToNull(reading), blankToNull(jlpt), blankToNull(part),
                stateFilter, blankToNull(book), categoryFromFilter, categoryToFilter,
                learnStateFilter, accountId, kindFilter, skipAcquired, safeLimit);
        return new JapaneseModels.AiTargets(total, acquired, candidates, wordIds,
                Math.max(0, candidates - wordIds.size()), safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordDetailResult wordDetail(long accountId, long wordId) {
        JpnWordEntity word = requireWord(accountId, wordId);
        List<JapaneseModels.CollectionRow> collections = wordMapper.listCollections(wordId).stream()
                .map(entity -> new JapaneseModels.CollectionRow(entity.getCollectionId(), entity.getLevel(),
                        entity.getBook(), entity.getCategory(), entity.getWordSeq() == null ? 0 : entity.getWordSeq(),
                        entity.getListedWord(), entity.getListedReading(), entity.getListedPartOfSpeech(),
                        entity.getChineseMeaning()))
                .toList();
        List<JapaneseModels.QuestionRow> questions = wordMapper.listQuestions(wordId).stream()
                .map(entity -> new JapaneseModels.QuestionRow(entity.getQuestionId(), entity.getQuestionType(),
                        entity.getQuestionNo() == null ? 1 : entity.getQuestionNo(), entity.getQuestionTextJa(),
                        entity.getCorrectValue(), entity.getChoiceCount() == null ? 0 : entity.getChoiceCount()))
                .toList();
        JpnWordDetailEntity detail = detailMapper.findActiveDetail(wordId);
        return new JapaneseModels.WordDetailResult(toWordRow(word), collections, questions,
                toDetailView(detail, wordMapper.findAlternateReading(wordId)));
    }

    @Override
    @Transactional
    public JapaneseModels.RegisterResult registerWords(UserPrincipal user,
                                                      JapaneseModels.RegisterRequest request) {
        List<JapaneseModels.RegisterWord> words = request == null || request.words() == null
                ? List.of() : request.words();
        if (words.isEmpty()) {
            throw new ValidationException("登録する単語を入力してください。");
        }

        int created = 0;
        int collections = 0;
        List<String> skipped = new ArrayList<>();
        // 同じ書籍・分類の中での次の SEQ（DB を何度も引かないように覚える）
        Map<String, Integer> nextSeq = new HashMap<>();
        // この登録で扱う書籍（マスタを先に用意し、最後に統計を数え直す）
        Set<String> books = new LinkedHashSet<>();
        for (JapaneseModels.RegisterWord entry : words) {
            String book = blankToNull(entry.book());
            if (book != null && books.add(book)) {
                ensureBook(user, book);
            }
        }

        for (JapaneseModels.RegisterWord entry : words) {
            String word = entry.word() == null ? "" : entry.word().trim();
            if (word.isEmpty()) {
                continue;
            }
            String book = entry.book() == null || entry.book().isBlank() ? null : entry.book().trim();
            String category = entry.category() == null || entry.category().isBlank()
                    ? null : entry.category().trim();
            if (book == null || category == null) {
                // 書籍と分類は一覧の表示に必須。無い行は入れない（黙って収録なしにしない）
                throw new ValidationException("書籍と分類は必須です（" + word + "）。");
            }

            // 語は既にあれば作り直さない（同じ語を二重に作らない）
            long wordId = entry.wordId() == null ? 0L : entry.wordId();
            if (wordId == 0L) {
                String reading = blankToNull(entry.reading());
                JpnWordEntity existing = wordMapper.findByWordAndReading(word, reading);
                if (existing == null) {
                    JpnWordEntity entity = new JpnWordEntity();
                    entity.setWord(word);
                    entity.setReading(reading);
                    entity.setWordKey(word);
                    entity.setReadingKey(reading == null ? "" : reading);
                    entity.setStateCode("ACTIVE");
                    entity.setCreatedBy(user.accountId());
                    wordMapper.insert(entity);
                    wordId = entity.getWordId();
                    created += 1;
                } else {
                    wordId = existing.getWordId();
                }
            }

            // 同じ語が同じ Unit に二重に入らないようにする
            if (wordMapper.countCollection(wordId, book, category) > 0) {
                skipped.add(word);
                continue;
            }
            // 位置（SEQ）を決める。指定が無い・不正・既に埋まっているときは後ろに詰める
            // （画面の割り当ては表示中のページから推すので、重なることがある）
            Integer seq = isSeqFree(book, category, entry.wordSeq())
                    ? entry.wordSeq()
                    : takeNextSeq(nextSeq, book, category);
            String listedWord = entry.listedWord() == null ? word : entry.listedWord();
            try {
                wordMapper.insertCollection(wordId, entry.level(), book, category, seq,
                        listedWord, entry.listedPartOfSpeech(), entry.listedChineseMeaning(), user.accountId());
            } catch (DuplicateKeyException cause) {
                // 同時に走った別の登録が同じ位置を取った（uq_jpn_collect_position）。
                // 次の番号で 1 回だけやり直す（2 回目も駄目なら例外をそのまま上げる）
                Integer retry = takeNextSeq(nextSeq, book, category);
                log.warn("japanese collection position conflict. wordId={} book={} category={} seq={} retry={}",
                        wordId, book, category, seq, retry);
                wordMapper.insertCollection(wordId, entry.level(), book, category, retry,
                        listedWord, entry.listedPartOfSpeech(), entry.listedChineseMeaning(), user.accountId());
            }
            collections += 1;
        }

        // 書籍の統計（分類数・収録語数）を数え直す。新しい書籍は 0 で作ったのでここで入る
        for (String book : books) {
            wordMapper.refreshBookCounts(book);
        }

        // 画面に出す結果。1 件も入らなかったときは、その理由（全部すでにある／対象が無い）を書く
        String message;
        if (skipped.isEmpty()) {
            message = collections == 0
                    ? "登録する単語がありませんでした。"
                    : collections + " 件を登録しました。";
        } else if (collections == 0) {
            message = skipped.size() + " 件は同じ Unit に登録済みのため飛ばしました（登録した語はありません）。";
        } else {
            message = collections + " 件を登録しました（" + skipped.size()
                    + " 件は同じ Unit に登録済みのため飛ばしました）。";
        }
        return new JapaneseModels.RegisterResult(created, collections, skipped.size(), skipped, message);
    }

    /** その位置（SEQ）をそのまま使ってよいか（指定があり、まだ埋まっていない）。 */
    private boolean isSeqFree(String book, String category, Integer seq) {
        return seq != null && seq >= 1 && wordMapper.countCollectionSeq(book, category, seq) == 0;
    }

    /**
     * その Unit の次の番号を取る（同じ実行の中では覚えている値を進める）。
     *
     * <p>DB を何度も引かないように {@code nextSeq} に記憶する。1 つの Unit の最大値は
     * 実行の途中で変わる（この実行が入れている行）ので、記憶した値のほうが正しい。</p>
     */
    private int takeNextSeq(Map<String, Integer> nextSeq, String book, String category) {
        String key = book + "\u0000" + category;
        Integer remembered = nextSeq.get(key);
        if (remembered == null) {
            Integer max = wordMapper.maxCollectionSeq(book, category);
            remembered = max == null ? 1 : max + 1;
        }
        nextSeq.put(key, remembered + 1);
        return remembered;
    }

    /**
     * 書籍マスタに行が無ければ作る。
     *
     * <p>収録の {@code 書籍ID} は {@code 書籍名} から引くので、名前だけの行があると
     * 収録がマスタから浮く（一覧は書籍名の文字列で出るが、書籍で絞り込めない）。
     * 書籍コードは数字だけの既存コードの次の番号（{@code '01'} の次は {@code '02'}）。</p>
     */
    private void ensureBook(UserPrincipal user, String book) {
        if (wordMapper.findBookIdByName(book) != null) {
            return;
        }
        Integer max = wordMapper.maxBookCode();
        String code = String.format("%02d", (max == null ? 0 : max) + 1);
        wordMapper.insertBook(code, book, user.accountId());
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult createWord(UserPrincipal user,
                                                        JapaneseModels.WordSaveRequest request) {
        String word = request.word().trim();
        // 読みは**任意**。新規登録では入れず、AI 詳細取得で後から埋める（設計 §5）
        String reading = blankToNull(request.reading());
        JpnWordEntity existing = wordMapper.findByWordAndReading(word, reading);
        if (existing != null) {
            throw new ConflictException("同じ見出し語と読みの単語がすでに登録されています。");
        }
        JpnWordEntity entity = new JpnWordEntity();
        entity.setWord(word);
        entity.setReading(reading);
        entity.setWordKey(word);
        // 読みキーは NOT NULL なので、読みが無いときは空文字（＝まだ読みが無い）
        entity.setReadingKey(reading == null ? "" : reading);
        entity.setJlptLevel(blankToNull(request.jlptLevel()));
        entity.setPartOfSpeech(blankToNull(request.partOfSpeech()));
        entity.setStateCode(choiceOrDefault(request.stateCode(), List.of("ACTIVE", "INACTIVE"), "状態", "ACTIVE"));
        entity.setNote(blankToNull(request.note()));
        entity.setCreatedBy(user.accountId());
        wordMapper.insert(entity);
        return new JapaneseModels.WordMutationResult(
                toWordRow(requireWord(user.accountId(), entity.getWordId())), "単語を登録しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult updateWord(UserPrincipal user, long wordId,
                                                        JapaneseModels.WordSaveRequest request) {
        JpnWordEntity current = requireWord(user.accountId(), wordId);
        String word = request.word().trim();
        // 修正でも読みは任意（空にすれば「まだ読みが無い」に戻せる）
        String reading = blankToNull(request.reading());
        JpnWordEntity duplicate = wordMapper.findByWordAndReading(word, reading);
        if (duplicate != null && !Objects.equals(duplicate.getWordId(), wordId)) {
            throw new ConflictException("同じ見出し語と読みの単語がすでに登録されています。");
        }
        JpnWordEntity entity = new JpnWordEntity();
        entity.setWordId(wordId);
        entity.setWord(word);
        entity.setReading(reading);
        entity.setWordKey(word);
        entity.setReadingKey(reading == null ? "" : reading);
        entity.setJlptLevel(blankToNull(request.jlptLevel()));
        entity.setPartOfSpeech(blankToNull(request.partOfSpeech()));
        entity.setStateCode(choiceOrDefault(request.stateCode(), List.of("ACTIVE", "INACTIVE"), "状態",
                current.getStateCode()));
        entity.setNote(blankToNull(request.note()));
        entity.setUpdatedBy(user.accountId());
        entity.setVersion(request.version() == null ? current.getVersion() : request.version());
        if (wordMapper.update(entity) == 0) {
            throw new ConflictException("他の操作で先に更新されました。再読み込みしてください。");
        }
        return new JapaneseModels.WordMutationResult(
                toWordRow(requireWord(user.accountId(), wordId)), "単語を更新しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.SimpleResult deleteWord(UserPrincipal user, long wordId) {
        requireWord(user.accountId(), wordId);
        wordMapper.delete(wordId);
        return new JapaneseModels.SimpleResult(1, "単語を削除しました。");
    }

    /**
     * 画面の編集を <b>詳細の新しい版</b>として保存する（切片4）。
     *
     * <p><strong>順番が意味を持つ</strong>（部分 UNIQUE 索引 uq_jpn_detail_active が
     * 「1 語につき ACTIVE は 1 行」を守っている）:</p>
     * <ol>
     *   <li>今の有効版（ヘッダ ＋ 11 の子テーブル）を読む</li>
     *   <li>要求の内容で新しい版のヘッダと段落の行を作る
     *       （{@link JpnWordDetailEditorComposer}。行ごとの出所はここで決まる）</li>
     *   <li><b>古い有効版を ARCHIVED にする</b>（消さない＝履歴）</li>
     *   <li><b>新しい版を ACTIVE で入れる</b>（内容版数は最大 + 1、元詳細ID は 3 の版、
     *       生成ID は NULL、手修正フラグは true）</li>
     *   <li>段落の行を新しい 詳細ID へ入れる</li>
     *   <li>母表（JPN_単語情報）を更新する（楽観的ロック。失敗したら全部ロールバック）</li>
     * </ol>
     *
     * <p>3 を 4 より先にしないと部分 UNIQUE 索引に当たる（ACTIVE が 2 行になるため）。</p>
     *
     * <p><strong>行の「出所」の判定</strong>（{@link JpnWordDetailEditorComposer}）:
     * 要求の段落の行を元の版の行と<b>内容で</b>突き合わせ、まったく同じ行は元の出所
     * （{@code 登録元コード} と {@code 手修正フラグ}）を引き継ぐ。変わった行・新しく足した行は
     * {@code 登録元コード='APP'}・{@code 手修正フラグ}=true。元の版にあって要求に無い行は消す。
     * こうすると「画面で触った行は以後 AI に上書きされない」「触っていない AI の行は次の取得で
     * 普段どおり置き換わる」が両立する。</p>
     */
    @Override
    @Transactional
    public JapaneseModels.WordDetailResult saveWordEditor(UserPrincipal user, long wordId,
                                                         JapaneseModels.WordEditorRequest request) {
        if (request.word().version() == null || request.contentVersion() == null || request.contentVersion() < 0) {
            throw new ValidationException("編集開始時の版数を指定してください。");
        }
        requireWord(user.accountId(), wordId);
        JpnWordDetailEntity current = detailMapper.findActiveDetail(wordId);
        int version = JpnWordDetailAssembler.contentVersionOf(current);
        if (version != request.contentVersion()) {
            throw new ConflictException("詳細情報が更新されています。入力を控えてから読み直してください。");
        }
        JpnWordDetailChildren.Rows currentRows = current == null
                ? JpnWordDetailChildren.Rows.empty() : childrenOf(current.getDetailId());

        // 要求の詳細（画面が送ってきた内容をそのまま使う。送られなかった語レベルのキーは
        // 元の版の値で埋める＝JpnWordDetailEditorComposer）
        Map<String, Object> requested = new LinkedHashMap<>();
        if (request.detail() != null) {
            requested.putAll(request.detail());
        }
        requested.put("manuallyCorrected", true);
        requested.put("jlptLevel", blankToNull(request.word().jlptLevel()));
        requested.put("partOfSpeech", blankToNull(request.word().partOfSpeech()));

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                requested, current, currentRows, wordId, user.accountId());

        // 先に古い有効版を ARCHIVED にしてから、新しい版を ACTIVE で入れる
        // （部分 UNIQUE 索引 uq_jpn_detail_active の順番）
        //
        // 同時に 2 つの保存が走ると、後から来た方は「ARCHIVED にする相手がもう居ない」ので
        // 0 行更新のまま INSERT へ進み、先に出来た ACTIVE と一意索引でぶつかる。これは
        // 利用者から見れば「他の人の更新が先だった」だけなので 409 に写し替える
        // （DataIntegrityViolationException のままだと 500 になり、入力が消える）。ほかの異常は隠さない。
        try {
            if (current != null) {
                detailMapper.archiveActiveDetail(wordId, user.accountId());
            }
            detailMapper.insertDetailVersion(created.header());
            Long detailId = created.header().getDetailId();
            if (detailId == null) {
                throw new ConflictException("詳細情報が更新されています。再読み込みしてください。");
            }
            insertRows(detailId, created.children());
        } catch (DataIntegrityViolationException cause) {
            // 版の書き込みで一意索引に当たった＝ほかの保存が先だった。母表の更新はここでは
            // 走らせていないので、写し替えるのは版の書き込みの衝突だけ
            throw new ConflictException("詳細情報が更新されています。入力を控えてから読み直してください。");
        }

        // 母表の版数も検査し、どちらかの保存に失敗したら両方をロールバックする
        updateWord(user, wordId, request.word());
        return wordDetail(user.accountId(), wordId);
    }

    /**
     * 11 の段落の行を新しい版へ入れる（空の段落は呼ばない）。
     *
     * <p>行の出所（{@code 登録元コード}・{@code 手修正フラグ}）は行ごとに決まっている
     * （{@link JpnWordDetailEditorComposer} が元の版と突き合わせて入れた値）。
     * SQL はその値をそのまま書く。</p>
     */
    private void insertRows(long detailId, JpnWordDetailChildren.Rows rows) {
        if (rows.senses() != null && !rows.senses().isEmpty()) {
            detailMapper.insertSenses(detailId, rows.senses());
        }
        if (rows.examples() != null && !rows.examples().isEmpty()) {
            detailMapper.insertExamples(detailId, rows.examples());
        }
        if (rows.patterns() != null && !rows.patterns().isEmpty()) {
            detailMapper.insertPatterns(detailId, rows.patterns());
        }
        if (rows.dialogs() != null && !rows.dialogs().isEmpty()) {
            detailMapper.insertDialogs(detailId, rows.dialogs());
            // 発言は 会話ID が要るので、入れた会話を 表示順 で引き直してから入れる
            // （会話の 表示順 は版の中で一意。組み立てたときの並びと 1 対 1 で対応する）
            List<JpnWordDetailChildren.DialogLine> lines = new ArrayList<>();
            List<JpnWordDetailChildren.Dialog> saved = detailMapper.listDialogs(detailId);
            for (int index = 0; index < rows.dialogs().size() && index < saved.size(); index += 1) {
                JpnWordDetailChildren.Dialog source = rows.dialogs().get(index);
                if (source.getLines() == null) {
                    continue;
                }
                for (JpnWordDetailChildren.DialogLine line : source.getLines()) {
                    line.setDialogId(saved.get(index).getDialogId());
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                detailMapper.insertDialogLines(detailId, lines);
            }
        }
        if (rows.synonyms() != null && !rows.synonyms().isEmpty()) {
            detailMapper.insertSynonyms(detailId, rows.synonyms());
        }
        if (rows.cautions() != null && !rows.cautions().isEmpty()) {
            detailMapper.insertCautions(detailId, rows.cautions());
        }
        if (rows.collocations() != null && !rows.collocations().isEmpty()) {
            detailMapper.insertCollocations(detailId, rows.collocations());
        }
        if (rows.relatedWords() != null && !rows.relatedWords().isEmpty()) {
            detailMapper.insertRelatedWords(detailId, rows.relatedWords());
        }
        if (rows.usageNotes() != null && !rows.usageNotes().isEmpty()) {
            detailMapper.insertUsageNotes(detailId, rows.usageNotes());
        }
        if (rows.practices() != null && !rows.practices().isEmpty()) {
            detailMapper.insertPractices(detailId, rows.practices());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordDetailVersions detailVersions(long accountId, long wordId) {
        requireWord(accountId, wordId);
        List<JapaneseModels.DetailVersionRow> items = detailMapper.listDetailVersions(wordId).stream()
                .map(JapaneseServiceImpl::toVersionRow)
                .toList();
        return new JapaneseModels.WordDetailVersions(items);
    }

    /**
     * 指定した版を有効にする（楽観的ロック）。
     *
     * <p>「未指定＝最新版が有効」は<b>コードを必要としない</b>。新しい版は必ず {@code ACTIVE} で
     * 生まれる（画面の編集も AI の取得も）ので、何も指定しなければ最後に作った版が有効なまま。</p>
     *
     * <p>切り替えは新しい版を作らない（{@code ACTIVE} を移すだけ）。同じトランザクションで
     * 元の有効版を {@code ARCHIVED} にしてから対象を {@code ACTIVE} にする（部分 UNIQUE 索引の順番）。
     * 対象の {@code バージョン} が消えていたら 409（ほかの操作が先に更新した）。</p>
     */
    @Override
    @Transactional
    public JapaneseModels.WordDetailResult activateDetailVersion(UserPrincipal user, long wordId, long detailId,
                                                                 JapaneseModels.ActivateVersionRequest request) {
        requireWord(user.accountId(), wordId);
        if (request == null || request.version() == null) {
            throw new ValidationException("版番号を指定してください。");
        }
        JpnWordDetailVersionEntity target = detailMapper.findDetailVersion(wordId, detailId);
        if (target == null) {
            throw new NotFoundException("指定した版が見つかりません。");
        }
        // 楽観的ロックは先に見る（ARCHIVED にしてから気づくと、元の版が履歴になってしまう）
        // Integer は参照で比べない（127 を超えると別のインスタンスになり、必ず食い違う）
        if (target.getVersion() == null || !Objects.equals(target.getVersion(), request.version())) {
            throw new ConflictException("版が更新されています。再読み込みしてください。");
        }
        // 既に有効なら何もしない（ARCHIVED にする相手が居ない）
        if (!"ACTIVE".equals(target.getStateCode())) {
            try {
                detailMapper.archiveActiveDetail(wordId, user.accountId());
                if (detailMapper.activateDetailVersion(wordId, detailId, request.version(), user.accountId()) == 0) {
                    throw new ConflictException("版が更新されています。再読み込みしてください。");
                }
            } catch (DataIntegrityViolationException cause) {
                // 同時に別の版を有効にしようとした（部分 UNIQUE 索引 uq_jpn_detail_active）。
                // 利用者から見れば「他の操作が先だった」だけなので 409 にする
                throw new ConflictException("版が更新されています。再読み込みしてください。");
            }
        }
        return wordDetail(user.accountId(), wordId);
    }

    /**
     * 問題（C/D/E）の版の一覧（一覧の「取得状態」のタグから開く履歴）。
     *
     * <p>1 行 = AI の取得 1 回（{@code JPN_AI生成履歴情報}）。2.0 の「詳細情報取得履歴」と同じ形で、
     * プロバイダ・モデル・状態・取得日時・生成件数を出し、その版の問題が今使われているかも返す。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.WordQuestionVersionList questionVersions(long accountId, long wordId) {
        requireWord(accountId, wordId);
        return questionVersionsOf(wordId);
    }

    /**
     * 問題の版を切り替える。
     *
     * <p>同じ種別の中で、指定した版だけを {@code ACTIVE}、ほかを {@code ARCHIVED} にする
     * （問題は消さない＝テストの出題が参照している）。指定した版に問題が無ければ 400
     * （失敗した取得の版は選べない）。</p>
     */
    @Override
    @Transactional
    public JapaneseModels.WordQuestionVersionList activateQuestionVersion(UserPrincipal user, long wordId,
                                                                          String questionType, int contentVersion) {
        requireWord(user.accountId(), wordId);
        String type = blankToNull(questionType);
        if (type == null) {
            throw new ValidationException("問題の種別を指定してください。");
        }
        if (contentVersion <= 0) {
            throw new ValidationException("版番号を指定してください。");
        }
        // その版に問題が無い（失敗した取得の版）は切り替えられない。**先に見る**のが大事:
        // 更新してから気づくと、その種別の問題が全部 ARCHIVED になって「使える版が無い」状態になる
        JapaneseModels.WordQuestionVersion target = questionVersionsOf(wordId).items().stream()
                .filter(item -> type.equals(item.questionType()) && item.contentVersion() == contentVersion)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("指定した版が見つかりません。"));
        if (target.questionCount() <= 0) {
            throw new ValidationException("この版には問題がありません（取得に失敗した版は選べません）。");
        }
        if (wordMapper.activateQuestionVersion(wordId, type, contentVersion, user.accountId()) == 0) {
            throw new ConflictException("使用する版を切り替えられませんでした。再読み込みしてください。");
        }
        return questionVersionsOf(wordId);
    }

    /** 問題の版の一覧（1 つの入口から引く。切り替えのあとも同じ形で返す）。 */
    private JapaneseModels.WordQuestionVersionList questionVersionsOf(long wordId) {
        List<JapaneseModels.WordQuestionVersion> items = wordMapper.listQuestionVersions(wordId).stream()
                .map(row -> new JapaneseModels.WordQuestionVersion(
                        row.getQuestionType(), intValue(row.getContentVersion()), intValue(row.getQuestionCount()),
                        Boolean.TRUE.equals(row.getActive()), row.getGenerationState(),
                        row.getAiProvider(), row.getAiModel(),
                        intValue(row.getGeneratedCount()), intValue(row.getFailedCount()),
                        row.getErrorMessage(), iso(row.getStartedAt()), iso(row.getFinishedAt())))
                .toList();
        return new JapaneseModels.WordQuestionVersionList(items, items.size());
    }

    /** 版の一覧の 1 行（段落の行数は数えた値を渡す）。 */
    private static JapaneseModels.DetailVersionRow toVersionRow(JpnWordDetailVersionEntity entity) {
        return new JapaneseModels.DetailVersionRow(
                entity.getDetailId() == null ? 0L : entity.getDetailId(),
                intValue(entity.getContentVersion()),
                entity.getVersion() == null ? 1 : entity.getVersion(),
                entity.getStateCode(),
                "ACTIVE".equals(entity.getStateCode()),
                Boolean.TRUE.equals(entity.getManualCorrected()) || Boolean.TRUE.equals(entity.getManualRows()),
                entity.getAiProvider(), entity.getAiModel(), entity.getGenerationId(),
                iso(entity.getFetchedAt()), entity.getNote(),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()),
                new JapaneseModels.DetailVersionCounts(
                        intValue(entity.getSenseCount()), intValue(entity.getExampleCount()),
                        intValue(entity.getPatternCount()), intValue(entity.getDialogCount()),
                        intValue(entity.getSynonymCount()), intValue(entity.getCautionCount()),
                        intValue(entity.getCollocationCount()), intValue(entity.getRelatedWordCount()),
                        intValue(entity.getUsageNoteCount()), intValue(entity.getPracticeCount())));
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult setFavorite(UserPrincipal user, long wordId, boolean favorite) {
        requireWord(user.accountId(), wordId);
        statusMapper.insertStatusIfAbsent(user.accountId(), wordId);
        statusMapper.updateFavorite(user.accountId(), wordId, favorite, Timestamp.valueOf(LocalDateTime.now()));
        return new JapaneseModels.WordMutationResult(toWordRow(requireWord(user.accountId(), wordId)),
                favorite ? "お気に入りに追加しました。" : "お気に入りから外しました。");
    }

    @Override
    @Transactional
    public JapaneseModels.WordMutationResult setLearned(UserPrincipal user, long wordId, boolean learned) {
        requireWord(user.accountId(), wordId);
        statusMapper.insertStatusIfAbsent(user.accountId(), wordId);
        statusMapper.updateLearned(user.accountId(), wordId, learned, Timestamp.valueOf(LocalDateTime.now()));
        return new JapaneseModels.WordMutationResult(toWordRow(requireWord(user.accountId(), wordId)),
                learned ? "習得済にしました。" : "習得済を解除しました。");
    }

    // ============================================================== テスト

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.TestListResult searchTests(long accountId, String state, String testType,
                                                     int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String stateFilter = normalizeChoice(state, JapaneseModels.TEST_STATES, "テストの状態");
        String typeFilter = normalizeChoice(testType, JapaneseModels.TEST_TYPES, "テスト種別");

        long total = testMapper.count(accountId, stateFilter, typeFilter);
        List<JapaneseModels.TestRow> items = testMapper.search(accountId, stateFilter, typeFilter,
                        safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toTestRow)
                .toList();
        JpnTotalsEntity totals = testMapper.totals(accountId);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.TestListResult(items, total, safePage, safeSize, totalPages,
                new JapaneseModels.TestTotals(totals.tests(), totals.completed(), totals.running(),
                        totals.averageScoreValue(), totals.totalActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.TestDetailResult testDetail(long accountId, long testId) {
        JpnTestEntity test = requireTest(accountId, testId);
        return new JapaneseModels.TestDetailResult(toTestRow(test), buildQuestionViews(testId));
    }

    @Override
    @Transactional
    public JapaneseModels.TestDetailResult startTest(long accountId, long testId) {
        testMapper.lockTest(testId);
        JpnTestEntity test = requireTest(accountId, testId);
        if ("CREATED".equals(test.getStateCode()))
            testMapper.updateState(testId, "RUNNING", Timestamp.valueOf(LocalDateTime.now()), null);
        return testDetail(accountId, testId);
    }

    @Override
    @Transactional
    public JapaneseModels.TestDetailResult createTest(UserPrincipal user, JapaneseModels.TestCreateRequest request) {
        String testType = normalizeChoice(request.testType(), JapaneseModels.TEST_TYPES, "テスト種別");
        if (testType == null) {
            throw new ValidationException("テスト種別を指定してください。");
        }
        int requested = request.questionCount() == null ? 0 : request.questionCount();
        if (requested < 0 || requested > JapaneseModels.MAX_QUESTIONS) throw new ValidationException("数量は0（全部）〜100で指定してください。");
        int count = requested == 0 ? Integer.MAX_VALUE : requested;
        if (request.categoryFrom() != null && request.categoryTo() != null && !request.categoryFrom().isBlank()
                && !request.categoryTo().isBlank() && request.categoryFrom().compareTo(request.categoryTo()) > 0)
            throw new ValidationException("分類 From は分類 To 以前を指定してください。");
        String mode = choiceOrDefault(request.mode(), JapaneseModels.TEST_MODES, "出題方式", "ALL");
        String difficulty = choiceOrDefault(request.difficulty(),
                List.of("EASY", "NORMAL", "HARD"), "難易度", "NORMAL");
        boolean random = "RANDOM".equals(mode);

        JpnTestEntity test = new JpnTestEntity();
        test.setTestNo(nextTestNo());
        test.setAccountId(user.accountId());
        test.setTestType(testType);
        test.setLevel(blankToNull(request.level()));
        test.setBook(blankToNull(request.book()));
        test.setCategoryFrom(blankToNull(request.categoryFrom()));
        test.setCategoryTo(blankToNull(request.categoryTo()));
        test.setDifficulty(difficulty);
        test.setMode(mode);
        test.setQuestionCount(count);
        test.setStartedAt(null);
        testMapper.insert(test);

        List<JpnTestQuestionEntity> entries = "A".equals(testType) || "B".equals(testType)
                ? buildWordEntries(test, testType)
                : buildQuestionEntries(test, testType, difficulty);
        if (entries.isEmpty()) {
            throw new ValidationException("条件に合う問題がありません。レベルや分類の指定を変えてください。");
        }
        for (JpnTestQuestionEntity entry : entries) {
            testMapper.insertEntry(entry);
        }
        // 実際に出題できた数で上書きする（条件に合う問題が少ないとき）
        testMapper.updateQuestionCount(test.getTestId(), entries.size());
        return new JapaneseModels.TestDetailResult(toTestRow(testMapper.findById(test.getTestId())),
                buildQuestionViews(test.getTestId()));
    }

    /**
     * C・D・E: 問題テーブルから条件に合う問題を選ぶ。
     *
     * <p><strong>選択肢はプールから選んで固定する</strong>（切片6・設計「2. 选项池」）:
     * その問題の選択肢（{@code JPN_単語問題選択肢情報}。正解 1 ＋ 誤答 4〜6）を読み、
     * <b>正解 1 ＋ 誤答からランダム 3</b> を選んで並べ替え、今回提示する 4 択として
     * {@code 出題選択肢JSON} に書く。こうすると:</p>
     * <ul>
     *   <li>同じテストを開き直しても同じ選択肢（毎回変わらない）</li>
     *   <li>プールを AI が取り直しても、受験済みの記録が変わらない</li>
     *   <li>画面の表示と回答の判定が必ず同じ選択肢を見る</li>
     * </ul>
     *
     * <p>プールが足りない問題（正解が 1 件でない・誤答が 3 件未満）は 1 問も出さずに失敗させる
     * （4 択を作れないまま出題すると、選択肢が 3 件以下の問題ができる）。</p>
     */
    private List<JpnTestQuestionEntity> buildQuestionEntries(JpnTestEntity test, String testType, String difficulty) {
        List<JpnQuestionEntity> questions = testMapper.pickQuestions(testType, test.getLevel(), test.getBook(),
                test.getCategoryFrom(), test.getCategoryTo(), difficulty, "RANDOM".equals(test.getMode()),
                test.getAccountId(), test.getQuestionCount());
        List<JpnTestQuestionEntity> entries = new ArrayList<>();
        int order = 1;
        for (JpnQuestionEntity question : questions) {
            List<JpnChoiceEntity> pool = testMapper.listChoices(question.getQuestionId());
            List<PromptedChoice> prompted = promptedChoices(pool);
            if (prompted == null) {
                throw new ValidationException("選択肢が足りない問題があります（正解 1 件と誤答 3 件以上が必要です）。");
            }
            JpnTestQuestionEntity entry = new JpnTestQuestionEntity();
            entry.setTestId(test.getTestId());
            entry.setWordId(question.getWordId());
            entry.setQuestionId(question.getQuestionId());
            entry.setCollectionId(question.getCollectionId());
            entry.setOrderNo(order);
            entry.setAccountId(test.getAccountId());
            entry.setChoicesJson(jsonOfChoices(prompted));
            JpnWordEntity word = wordMapper.findById(question.getWordId(), test.getAccountId());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("questionType", question.getQuestionType());
            snapshot.put("questionText", question.getQuestionTextJa());
            snapshot.put("correctValue", question.getCorrectValue());
            snapshot.put("explanation", question.getExplanationJa());
            snapshot.put("meaning", question.getQuestionTextZh());
            snapshot.put("example", question.getExampleJa());
            snapshot.put("book", question.getBook());
            snapshot.put("category", question.getCategory());
            snapshot.put("word", word == null ? question.getTargetWord() : word.getWord());
            snapshot.put("reading", word == null ? question.getTargetReading() : word.getReading());
            entry.setSnapshotJson(json(snapshot));
            entries.add(entry);
            order += 1;
        }
        return entries;
    }

    /**
     * 出題に固定する「今回提示した選択肢」1 件（{@code 出題選択肢JSON} の要素）。
     *
     * <p>{@code choiceId} はプールの選択肢ID（C/D/E）。A・B はプールを持たないので null にし、
     * 回答は表示順（1 から）で受ける。</p>
     */
    private record PromptedChoice(Long choiceId, String value, String reading, boolean correct) {
    }

    /**
     * テスト作成時に、見せる選択肢を決める（設計「2. 选项池」）。
     *
     * <p>{@code プール}（正解 1 ＋ 誤答 4〜6）から<b>正解 1 ＋ 誤答からランダム 3</b> を選び、
     * <b>並び順も混ぜる</b>（毎回同じ順で出すと、位置で覚えられてしまう）。</p>
     *
     * <p>戻り値は提示順の 4 件。プールが足りない（正解がちょうど 1 件でない・誤答が 3 件未満）
     * ときは null（4 択を作れない）。乱数は {@link #random} 1 つだけを使うので、テストから
     * 種を固定すれば「選んだ誤答」も「並び順」も再現できる。</p>
     */
    private List<PromptedChoice> promptedChoices(List<JpnChoiceEntity> pool) {
        int wrongNeeded = PROMPTED_CHOICE_COUNT - 1;
        if (pool == null || pool.size() < PROMPTED_CHOICE_COUNT) {
            return null;
        }
        JpnChoiceEntity correct = null;
        List<JpnChoiceEntity> wrong = new ArrayList<>();
        for (JpnChoiceEntity choice : pool) {
            if (Boolean.TRUE.equals(choice.getCorrect())) {
                // 正解が 2 件以上あるプールは使わない（表示と判定が食い違う）
                if (correct != null) {
                    return null;
                }
                correct = choice;
            } else {
                wrong.add(choice);
            }
        }
        if (correct == null || wrong.size() < wrongNeeded) {
            return null;
        }
        // 誤答はランダムに 3 件（毎回同じ誤答を出すと、答えを覚えられてしまう）
        Collections.shuffle(wrong, random);
        List<PromptedChoice> picked = new ArrayList<>();
        for (JpnChoiceEntity choice : wrong.subList(0, wrongNeeded)) {
            picked.add(new PromptedChoice(choice.getChoiceId(), choice.getValue(), choice.getReading(), false));
        }
        picked.add(new PromptedChoice(correct.getChoiceId(), correct.getValue(), correct.getReading(), true));
        // 提示順も混ぜる（正解がいつも同じ位置に来ないように）
        return shufflePrompted(picked);
    }

    /**
     * A・B（プールを持たない種別）の選択肢を、C/D/E と同じ形の 4 択にする。
     *
     * <p>A・B の正解は<b>入力の語から決まる値</b>（{@code correctValue}）で、選択肢は別の単語から
     * 作った文字列。プールの選択肢ID は無いので {@code choiceId} は null にし、回答は表示順で受ける。</p>
     */
    private List<PromptedChoice> promptedChoices(List<String> values, String correctValue) {
        int wrongNeeded = PROMPTED_CHOICE_COUNT - 1;
        if (values == null || values.size() < PROMPTED_CHOICE_COUNT) {
            return null;
        }
        String correct = null;
        List<String> wrong = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.equals(correctValue)) {
                if (correct != null) {
                    return null;
                }
                correct = value;
            } else {
                wrong.add(value);
            }
        }
        if (correct == null || wrong.size() < wrongNeeded) {
            return null;
        }
        Collections.shuffle(wrong, random);
        List<PromptedChoice> picked = new ArrayList<>();
        for (String value : wrong.subList(0, wrongNeeded)) {
            picked.add(new PromptedChoice(null, value, null, false));
        }
        picked.add(new PromptedChoice(null, correct, null, true));
        return shufflePrompted(picked);
    }

    /** 提示順を混ぜる（正解がいつも同じ位置に来ないように）。 */
    private List<PromptedChoice> shufflePrompted(List<PromptedChoice> picked) {
        Collections.shuffle(picked, random);
        return picked;
    }

    /** 今回提示した選択肢を {@code 出題選択肢JSON}（jsonb に入れる配列）にする。 */
    private String jsonOfChoices(List<PromptedChoice> prompted) {
        List<Map<String, Object>> json = new ArrayList<>();
        for (PromptedChoice choice : prompted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(CHOICE_ID_KEY, choice.choiceId());
            item.put(CHOICE_VALUE_KEY, choice.value());
            item.put(CHOICE_READING_KEY, choice.reading());
            item.put(CHOICE_CORRECT_KEY, choice.correct());
            json.add(item);
        }
        return json(json);
    }

    /**
     * A・B: 2.0 に問題テーブルが無いので、単語（見出し語・読み）から出題を作る。
     * A は「見出し語 → 読み」、B は「読み → 見出し語」。誤答の選択肢は同じ条件の別の単語から取る。
     */
    private List<JpnTestQuestionEntity> buildWordEntries(JpnTestEntity test, String testType) {
        List<JpnWordEntity> pool = new ArrayList<>(testMapper.pickWords(testType, test.getLevel(), test.getBook(),
                test.getCategoryFrom(), test.getCategoryTo(), "RANDOM".equals(test.getMode()), test.getQuestionCount()));
        List<JpnTestQuestionEntity> entries = new ArrayList<>();
        for (JpnWordEntity word : pool) {
            if (entries.size() >= test.getQuestionCount()) break;
            if ("B".equals(testType) && (blankToNull(word.getReading()) == null || blankToNull(word.getChineseMeaning()) == null)) continue;
            JpnTestQuestionEntity entry = new JpnTestQuestionEntity();
            entry.setTestId(test.getTestId());
            entry.setWordId(word.getWordId());
            entry.setCollectionId(word.getCollectionId());
            entry.setOrderNo(entries.size() + 1);
            entry.setAccountId(test.getAccountId());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("questionType", "A".equals(testType) ? "A_STUDY" : "B_INPUT");
            snapshot.put("questionText", "A".equals(testType) ? "単語の意味と使い方を確認しましょう。" : word.getChineseMeaning());
            snapshot.put("word", word.getWord());
            snapshot.put("reading", word.getReading());
            snapshot.put("meaning", word.getChineseMeaning());
            snapshot.put("book", word.getBook());
            snapshot.put("category", word.getCategory());
            snapshot.put("correctValue", word.getWord());
            snapshot.put("wordDetail", wordDetail(test.getAccountId(), word.getWordId()));
            entry.setSnapshotJson(json(snapshot));
            entry.setChoicesJson("[]");
            entries.add(entry);
        }
        return entries;
    }

    /** A・B の出題内容（問題文・正解・選択肢）を 1 つの JSON にして出題行に持たせる。 */
    private String wordSnapshot(String questionType, JpnWordEntity word, String correctValue, List<String> choices) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionType", questionType);
        snapshot.put("questionText", "A_READING".equals(questionType)
                ? "「" + word.getWord() + "」の読みを選んでください。"
                : "「" + word.getReading() + "」の漢字表記を選んでください。");
        snapshot.put("correctValue", correctValue);
        snapshot.put("word", word.getWord());
        snapshot.put("reading", word.getReading());
        snapshot.put("choices", choices);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception cause) {
            throw new IllegalStateException("出題内容を作れませんでした。", cause);
        }
    }

    @Override
    @Transactional
    public JapaneseModels.AnswerResult answer(UserPrincipal user, long testId,
                                              JapaneseModels.AnswerRequest request) {
        testMapper.lockTest(testId);
        JpnTestEntity test = requireTest(user.accountId(), testId);
        JpnTestQuestionEntity entry = testMapper.findEntry(testId, request.orderNo());
        if (entry == null) {
            throw new NotFoundException("出題が見つかりません。");
        }
        if ("ANSWERED".equals(entry.getEntryState())) {
            throw new ConflictException("この問題はすでに回答済みです。");
        }
        if ("COMPLETED".equals(test.getStateCode())) {
            throw new ConflictException("このテストは完了しています。");
        }

        // 問題が未確定（2.0 から移行した出題、または A・B の出題）はここで内容を決める
        Map<String, Object> snapshot = parseSnapshot(entry.getSnapshotJson());
        JpnQuestionEntity question = entry.getQuestionId() == null && snapshot.isEmpty()
                ? resolveQuestion(test, entry)
                : null;
        Long questionId = entry.getQuestionId() != null
                ? entry.getQuestionId()
                : (question == null ? null : question.getQuestionId());

        AnswerJudgement judgement = judge(test, entry, question, snapshot, request);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        long elapsed = request.elapsedMs() == null ? 0L : Math.min(1800000L, Math.max(0L, request.elapsedMs()));

        int answerCount = (entry.getAnswerCount() == null ? 0 : entry.getAnswerCount()) + 1;
        int wrongCount = (entry.getWrongCount() == null ? 0 : entry.getWrongCount()) + (judgement.correct() ? 0 : 1);
        boolean retry = "B".equals(test.getTestType()) && request.readingText() != null && !judgement.correct() && answerCount < 3;
        testMapper.updateEntryAnswer(entry.getEntryId(), questionId, retry ? "PENDING" : "ANSWERED", retry ? null : judgement.judgment(),
                answerCount, wrongCount, elapsed, retry ? null : now);

        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("attempt", answerCount);
        attempt.put("answer", request.answerText());
        attempt.put("reading", request.readingText());
        attempt.put("choiceId", request.choiceId());
        attempt.put("correct", judgement.correct());
        attempt.put("judgment", judgement.judgment());
        attempt.put("elapsedMs", elapsed);
        attempt.put("at", now.toString());
        testMapper.appendHistory(entry.getEntryId(), json(List.of(attempt)));
        if (test.getStartedAt() == null) testMapper.updateState(testId, "RUNNING", now, null);

        // テストの進捗
        List<JpnTestQuestionEntity> entries = testMapper.listEntries(testId);
        int done = (int) entries.stream().filter(item -> "ANSWERED".equals(item.getEntryState())).count();
        int correct = (int) entries.stream()
                .filter(item -> "CORRECT".equals(item.getJudgment()) || "CONFIRMED".equals(item.getJudgment())).count();
        int wrong = done - correct;
        String state = done >= entries.size() ? "COMPLETED" : "RUNNING";
        long activeMs = (test.getActiveMs() == null ? 0L : test.getActiveMs()) + elapsed;
        testMapper.updateProgress(testId, done, correct, wrong, state, now, activeMs);
        if ("COMPLETED".equals(state)) {
            testMapper.updateState(testId, "COMPLETED", test.getStartedAt(), now);
        }

        // 学習状況・技能習得・日次（技能区分はテスト種別 × 問題種別から決める）
        String skillCode = SKILL_CODES.getOrDefault(judgement.questionType(), test.getTestType() + "_SKILL");
        if (!retry) {
            long learningMs = elapsed + ("B".equals(test.getTestType()) ? value(entry.getActiveMs()) : 0L);
            if ("B".equals(test.getTestType()) && request.readingText() != null) {
                String expectedReading = String.valueOf(snapshot.getOrDefault("reading", entry.getReading()));
                boolean readingCorrect = normalizeInput(expectedReading, true).equals(normalizeInput(request.readingText(), true));
                boolean writingCorrect = normalizeInput(String.valueOf(snapshot.getOrDefault("word", entry.getWord())), false)
                        .equals(normalizeInput(request.answerText(), false));
                updateInputSkill(user.accountId(), entry.getWordId(), "B_READING_RECALL", readingCorrect, learningMs / 2, now);
                updateInputSkill(user.accountId(), entry.getWordId(), "B_ORTHOGRAPHY", writingCorrect, learningMs - learningMs / 2, now);
                skillCode = null;
            }
            updateLearning(user.accountId(), entry.getWordId(), test.getTestType(), skillCode,
                    judgement.judgment(), judgement.correct(), learningMs, now);
        }

        JpnTestEntity updated = testMapper.findById(testId);
        return new JapaneseModels.AnswerResult(judgement.correct(), judgement.judgment(),
                retry ? null : judgement.correctValue(), retry ? null : judgement.explanation(), toTestRow(updated),
                judgement.correct() ? "正解です。" : "不正解です。", !retry);
    }

    /**
     * 回答の判定。
     *
     * <p><strong>判定は「今回提示した選択肢」だけで行う</strong>（切片6・設計「2. 选项池」）:
     * テスト作成時に固定した {@code 出題選択肢JSON} を読み、その中の正解と選んだ選択肢を突き合わせる。
     * プール（{@code JPN_単語問題選択肢情報}）は読み直さないので、<b>画面に出た選択肢と判定が必ず一致</b>し、
     * プールを AI が取り直しても受験済みの記録は変わらない。</p>
     *
     * <p>選択肢ID の意味は出題の作り方で変わる:</p>
     * <ul>
     *   <li>C/D/E … プールの {@code 選択肢ID}</li>
     *   <li>A・B … プールが無いので <b>1 からの並び順</b>（今の画面と同じ送り方）</li>
     * </ul>
     *
     * <p>古い出題（{@code 出題選択肢JSON} が空の移行データ）は今までどおり
     * プール（{@code 問題ID}）で判定する。A・B で値も無いときは
     * {@code 単語スナップショットJSON.choices} の並び順で受ける。</p>
     */
    private AnswerJudgement judge(JpnTestEntity test, JpnTestQuestionEntity entry, JpnQuestionEntity resolved,
                                  Map<String, Object> snapshot, JapaneseModels.AnswerRequest request) {
        String correctValue = entry.getCorrectValue() != null
                ? entry.getCorrectValue()
                : (resolved != null ? resolved.getCorrectValue()
                        : (snapshot.get("correctValue") == null ? null : String.valueOf(snapshot.get("correctValue"))));
        String explanation = entry.getExplanationJa() != null
                ? entry.getExplanationJa()
                : (resolved == null ? null : resolved.getExplanationJa());
        String questionType = entry.getQuestionType() != null
                ? entry.getQuestionType()
                : (resolved != null ? resolved.getQuestionType()
                        : String.valueOf(snapshot.getOrDefault("questionType", test.getTestType())));

        if (snapshot.get("correctValue") != null) correctValue = String.valueOf(snapshot.get("correctValue"));
        if (snapshot.get("explanation") != null) explanation = String.valueOf(snapshot.get("explanation"));
        if ("A".equals(test.getTestType()) && "学習完了".equals(request.answerText()))
            return new AnswerJudgement(true, "CONFIRMED", correctValue, explanation, "A_STUDY");
        if ("B".equals(test.getTestType()) && request.readingText() != null) {
            String heading = String.valueOf(snapshot.getOrDefault("word", entry.getWord()));
            String reading = String.valueOf(snapshot.getOrDefault("reading", entry.getReading()));
            boolean orthography = normalizeInput(heading, false).equals(normalizeInput(request.answerText(), false));
            boolean kana = normalizeInput(reading, true).equals(normalizeInput(request.readingText(), true));
            boolean both = orthography && kana;
            return new AnswerJudgement(both, both ? "CORRECT" : orthography || kana ? "MIXED" : "INCORRECT",
                    heading + "（" + reading + "）", "表記：" + (orthography ? "正解" : heading) + " / 読み：" + (kana ? "正解" : reading), "B_ORTHOGRAPHY");
        }
        boolean correct = judgeWithPromptedChoices(entry, correctValue, request);
        return new AnswerJudgement(correct, correct ? "CORRECT" : "INCORRECT", correctValue, explanation,
                questionType);
    }

    /**
     * テスト作成時に固定した 4 択（{@code 出題選択肢JSON}）で判定する。
     *
     * <p>選んだ選択肢が見つからない（＝今回提示していない）ときは 400 にする。表示していない
     * 選択肢で正解にできてしまうと、画面と判定が食い違う。</p>
     */
    private boolean judgeWithPromptedChoices(JpnTestQuestionEntity entry, String correctValue,
                                             JapaneseModels.AnswerRequest request) {
        List<Map<String, Object>> prompted = parsePromptedChoices(entry.getChoicesJson());
        if (prompted.isEmpty()) {
            // 出題選択肢JSON を持たない古い出題（移行データ）。今までどおりプール／スナップショットで判定する
            return judgeLegacy(entry, correctValue, request);
        }
        Map<String, Object> selected = selectedPromptedChoice(prompted, hasPoolChoiceIds(prompted), request);
        if (selected == null) {
            throw new ValidationException("今回提示した選択肢を指定してください。");
        }
        // 「提示した正解の印」と「問題の正解値」を二重に確かめる（片方だけ壊れても誤判定しない）
        boolean markedCorrect = Boolean.TRUE.equals(selected.get(CHOICE_CORRECT_KEY));
        boolean valueCorrect = correctValue == null || Objects.equals(selected.get(CHOICE_VALUE_KEY), correctValue);
        return markedCorrect && valueCorrect;
    }

    /** 提示した選択肢がプールの選択肢ID を持つか（C/D/E は持ち、A・B は持たない）。 */
    private static boolean hasPoolChoiceIds(List<Map<String, Object>> prompted) {
        return prompted.stream().anyMatch(choice -> choice.get(CHOICE_ID_KEY) instanceof Number);
    }

    /**
     * 今回提示した選択肢の中から、回答が指したものを選ぶ。
     *
     * <p>プールの選択肢ID を持つ出題（C/D/E）は<b>ID の完全一致だけ</b>で探す。持たない出題（A・B）は
     * 1 からの並び順で受ける（今の画面と同じ送り方）。どちらも無ければ {@code answerText} を値で探す。
     * 見つからなければ null（＝今回提示していない選択肢。呼び側が 400 にする）。</p>
     *
     * @param hasChoiceIds 提示した選択肢がプールの選択肢ID を持つか
     */
    private Map<String, Object> selectedPromptedChoice(List<Map<String, Object>> prompted, boolean hasChoiceIds,
                                                       JapaneseModels.AnswerRequest request) {
        if (request.choiceId() != null) {
            if (hasChoiceIds) {
                Map<String, Object> byId = prompted.stream()
                        .filter(choice -> numericEquals(choice.get(CHOICE_ID_KEY), request.choiceId()))
                        .findFirst()
                        .orElse(null);
                if (byId != null) {
                    return byId;
                }
            } else {
                // A・B はプールの選択肢ID を持たないので、1 からの並び順で受ける
                int order = request.choiceId().intValue();
                if (order >= 1 && order <= prompted.size()) {
                    return prompted.get(order - 1);
                }
            }
        }
        if (request.answerText() != null && !request.answerText().isBlank()) {
            String answer = request.answerText().trim();
            return prompted.stream()
                    .filter(choice -> answer.equals(choice.get(CHOICE_VALUE_KEY)))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 出題選択肢JSON が無い古い出題の判定。
     *
     * <p>問題が確定している出題は今までどおりプール（{@code 問題ID}）で判定する。プールを持たない
     * A・B だけが {@code 単語スナップショットJSON.choices} の並び順で受ける（互換）。</p>
     */
    private boolean judgeLegacy(JpnTestQuestionEntity entry, String correctValue,
                                JapaneseModels.AnswerRequest request) {
        List<JpnChoiceEntity> pool = entry.getQuestionId() == null
                ? null : testMapper.listChoices(entry.getQuestionId());
        if (pool != null && !pool.isEmpty()) {
            JpnChoiceEntity selected = pool.stream()
                    .filter(choice -> Objects.equals(choice.getChoiceId(), request.choiceId()))
                    .findFirst()
                    .orElse(null);
            if (selected == null && request.answerText() != null && !request.answerText().isBlank()) {
                selected = pool.stream()
                        .filter(choice -> choice.getValue() != null
                                && choice.getValue().equals(request.answerText().trim()))
                        .findFirst()
                        .orElse(null);
            }
            if (selected == null) {
                throw new ValidationException("選択肢を指定してください。");
            }
            return Boolean.TRUE.equals(selected.getCorrect())
                    || Objects.equals(selected.getValue(), correctValue);
        }
        if (correctValue == null) {
            throw new ValidationException("この出題には正解が登録されていません。");
        }
        String answer = request.answerText() == null ? null : request.answerText().trim();
        if ((answer == null || answer.isEmpty()) && request.choiceId() != null) {
            answer = choiceValueFromSnapshot(entry.getSnapshotJson(), request.choiceId());
        }
        if (answer == null || answer.isEmpty()) {
            throw new ValidationException("回答を指定してください。");
        }
        return answer.equals(correctValue);
    }

    /** JSON の数値と Long を型をまたいで比べる（Jackson は整数を Integer で返す）。 */
    private static boolean numericEquals(Object value, Long expected) {
        return value instanceof Number number && expected != null
                && number.longValue() == expected;
    }

    /** 出題行の {@code 出題選択肢JSON} を読む（読めなければ空。古い出題として扱う）。 */
    private List<Map<String, Object>> parsePromptedChoices(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (Exception cause) {
            log.warn("出題選択肢JSON を読めませんでした（先頭 60 文字）: {}",
                    json.substring(0, Math.min(60, json.length())));
            return List.of();
        }
    }

    /** A・B の古い出題は選択肢 ID を持たないので、スナップショットの並び順（1 から）で受け取る。 */
    private String choiceValueFromSnapshot(String snapshotJson, Long choiceId) {
        Object choices = parseSnapshot(snapshotJson).get("choices");
        if (!(choices instanceof List<?> list)) {
            return null;
        }
        int index = choiceId.intValue() - 1;
        if (index < 0 || index >= list.size()) {
            return null;
        }
        Object value = list.get(index);
        return value == null ? null : String.valueOf(value);
    }

    /** 2.0 から移行した出題で問題が未確定のとき、その語のテスト種別に合う問題を 1 つ選ぶ。 */
    private JpnQuestionEntity resolveQuestion(JpnTestEntity test, JpnTestQuestionEntity entry) {
        List<String> types = QUESTION_TYPES.get(test.getTestType());
        if (types == null) {
            return null;
        }
        return testMapper.findQuestionByWordAndTypes(entry.getWordId(), types);
    }

    private void updateInputSkill(long accountId, long wordId, String skill, boolean correct, long elapsed, Timestamp now) {
        JpnSkillEntity previous = statusMapper.findSkill(accountId, wordId, "B", skill);
        statusMapper.insertSkillIfAbsent(accountId, wordId, "B", skill);
        statusMapper.updateSkillAfterAnswer(accountId, wordId, "B", skill, correct ? "LEARNING" : "REVIEW",
                nextMastery(previous == null ? null : previous.getMastery(), correct), correct,
                correct ? "CORRECT" : "INCORRECT", Timestamp.valueOf(now.toLocalDateTime().plusDays(correct ? 3 : 1)), elapsed, now);
    }

    /** 回答 1 件ぶんの学習状況・技能習得・日次の更新。 */
    private void updateLearning(long accountId, long wordId, String testType, String skillCode,
                                String judgment, boolean correct, long elapsedMs, Timestamp now) {
        int reviewInterval = nextReviewInterval(accountId, wordId, testType, skillCode, correct);
        Timestamp nextReviewAt = Timestamp.valueOf(now.toLocalDateTime().plusDays(reviewInterval));

        // A（勉強）は技能別の習得度を持たない（`JPN_技能習得情報` の CHECK が B〜E だけを許す。
        // 設計: A の確認は `JPN_学習状況情報.A確認回数` で数える）。技能の行を書かないだけで、
        // 学習状況と日次は A でも更新する。
        BigDecimal skillMastery = skillCode == null ? statusMapper.averageSkillMastery(accountId, wordId) : BigDecimal.ZERO;
        if (tracksSkill(testType) && skillCode != null) {
            statusMapper.insertSkillIfAbsent(accountId, wordId, testType, skillCode);
            skillMastery = nextMastery(statusMapper.averageSkillMastery(accountId, wordId), correct);
            statusMapper.updateSkillAfterAnswer(accountId, wordId, testType, skillCode,
                    correct ? "LEARNING" : "REVIEW", skillMastery, correct, judgment, nextReviewAt, elapsedMs, now);
        }

        statusMapper.insertStatusIfAbsent(accountId, wordId);
        statusMapper.updateStatusAfterAnswer(accountId, wordId, correct ? "LEARNING" : "REVIEW",
                skillMastery, correct, false, nextReviewAt, reviewInterval, testType, judgment, elapsedMs, now);
        // 総合習得度と学習状態は技能の平均から作り直す（2.0 と同じ規則）
        statusMapper.refreshStatusFromSkills(accountId, wordId, judgment, "A".equals(testType) ? 1 : 0);
        statusMapper.upsertDaily(accountId, LocalDate.now(), testType, elapsedMs, correct);
    }

    /** その種別が技能別の習得度（`JPN_技能習得情報`）を持つか。A だけ持たない。 */
    private static boolean tracksSkill(String testType) {
        return !"A".equals(testType);
    }

    /** 習得度の増減（正解 +20 / 誤答 -20。0〜100 に収める）。 */
    private static BigDecimal nextMastery(BigDecimal current, boolean correct) {
        BigDecimal base = current == null ? BigDecimal.ZERO : current;
        BigDecimal next = correct ? base.add(MASTERY_STEP) : base.subtract(MASTERY_STEP);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return next.compareTo(BigDecimal.valueOf(100)) > 0 ? BigDecimal.valueOf(100) : next;
    }

    /** 復習間隔（正解なら倍に延ばす。誤答なら 1 日に戻す）。 */
    private int nextReviewInterval(long accountId, long wordId, String testType, String skillCode, boolean correct) {
        if (!correct) {
            return 1;
        }
        JpnSkillEntity current = statusMapper.findSkill(accountId, wordId, testType, skillCode);
        if (current == null || current.getNextReviewAt() == null) {
            return 3;
        }
        // 前回の復習予定が先なら間隔を延ばす（最大 30 日）
        long days = java.time.Duration.between(
                current.getNextReviewAt().toLocalDateTime(), LocalDateTime.now()).toDays();
        return (int) Math.max(1, Math.min(MAX_REVIEW_INTERVAL_DAYS, Math.max(3, days + 3)));
    }

    @Override
    @Transactional
    public JapaneseModels.TestMutationResult completeTest(UserPrincipal user, long testId) {
        JpnTestEntity test = requireTest(user.accountId(), testId);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        testMapper.updateState(testId, "COMPLETED", test.getStartedAt(), now);
        return new JapaneseModels.TestMutationResult(toTestRow(testMapper.findById(testId)), "テストを完了にしました。");
    }

    @Override
    @Transactional
    public JapaneseModels.SimpleResult deleteTest(UserPrincipal user, long testId) {
        requireTest(user.accountId(), testId);
        testMapper.delete(testId);
        return new JapaneseModels.SimpleResult(1, "テストを削除しました。");
    }

    // ========================================================== 勉強状況

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.StatusResult status(long accountId, String learnState, String jlpt, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String learnStateFilter = normalizeChoice(learnState, JapaneseModels.LEARN_STATES, "学習状態");

        JpnTotalsEntity totals = statusMapper.summary(accountId, LocalDate.now());
        List<JapaneseModels.DailyRow> daily = statusMapper.listDaily(accountId, 31).stream()
                .map(entity -> new JapaneseModels.DailyRow(
                        entity.getStudyDate() == null ? null : entity.getStudyDate().toString(),
                        value(entity.getActiveMs()), value(entity.getTypeAMs()), value(entity.getTypeBMs()),
                        value(entity.getTypeCMs()), value(entity.getTypeDMs()), value(entity.getTypeEMs()),
                        intValue(entity.getWordCount()), intValue(entity.getTestCount()),
                        intValue(entity.getDoneCount()), intValue(entity.getCorrectCount()),
                        intValue(entity.getWrongCount())))
                .toList();
        long total = statusMapper.countStatuses(accountId, learnStateFilter, blankToNull(jlpt));
        List<JapaneseModels.StatusRow> items = statusMapper.listStatuses(accountId, learnStateFilter,
                        blankToNull(jlpt), safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toStatusRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        long answered = totals.answered();
        int accuracy = answered == 0 ? 0 : (int) Math.round(totals.correct() * 100.0 / answered);
        JapaneseModels.StatusSummary summary = new JapaneseModels.StatusSummary(totals.words(), totals.learned(),
                totals.favorites(), totals.mastery(), answered, totals.correct(), accuracy, totals.active(),
                totals.todayActive(),
                totals.getLastStudiedAt() == null ? null : totals.getLastStudiedAt().toLocalDateTime().toString());
        return new JapaneseModels.StatusResult(summary, daily, items, total, safePage, safeSize, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public JapaneseModels.SkillListResult skills(long accountId, String testType, String skill, int page, int size) {
        int safeSize = size <= 0 ? JapaneseModels.DEFAULT_SIZE : Math.min(size, JapaneseModels.MAX_SIZE);
        int safePage = Math.max(1, page);
        String typeFilter = normalizeChoice(testType, JapaneseModels.TEST_TYPES, "テスト種別");
        long total = statusMapper.countSkills(accountId, typeFilter, blankToNull(skill));
        List<JapaneseModels.SkillRow> items = statusMapper.listSkills(accountId, typeFilter, blankToNull(skill),
                        safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(JapaneseServiceImpl::toSkillRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new JapaneseModels.SkillListResult(items, total, safePage, safeSize, totalPages);
    }

    // ------------------------------------------------------------ 出題の組み立て

    /**
     * 出題一覧（問題と選択肢つき）。
     *
     * <p><strong>返すのは「このテストで実際に見せた選択肢」</strong>（切片6・設計「2. 选项池」）:
     * テスト作成時に固定した {@code 出題選択肢JSON} の 4 件だけを返す。プール（
     * {@code JPN_単語問題選択肢情報}。正解 1 ＋ 誤答 4〜6）は<b>返さない</b>ので、同じテストを
     * 開き直しても同じ 4 択になり、プールを AI が取り直しても受験済みの記録は変わらない。</p>
     *
     * <p>{@code 出題選択肢JSON} を持たない古い出題（移行データ。今は 0 行）だけは、
     * 今までどおりプール（{@code 問題ID}）またはスナップショットの選択肢に回退する。</p>
     */
    private List<JapaneseModels.TestQuestionView> buildQuestionViews(long testId) {
        List<JpnTestQuestionEntity> entries = testMapper.listEntries(testId);
        // プールを引くのは「出題選択肢JSON を持たない古い出題」だけ（無駄にプールを読まない）
        List<Long> questionIds = entries.stream()
                .filter(entry -> parsePromptedChoices(entry.getChoicesJson()).isEmpty())
                .map(JpnTestQuestionEntity::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<JpnChoiceEntity>> choicesByQuestion = questionIds.isEmpty()
                ? Map.of()
                : testMapper.listChoicesByQuestionIds(questionIds).stream()
                        .collect(Collectors.groupingBy(JpnChoiceEntity::getQuestionId,
                                LinkedHashMap::new, Collectors.toList()));

        List<JapaneseModels.TestQuestionView> views = new ArrayList<>();
        for (JpnTestQuestionEntity entry : entries) {
            Map<String, Object> snapshot = parseSnapshot(entry.getSnapshotJson());
            if (snapshot.get("word") != null) entry.setWord(String.valueOf(snapshot.get("word")));
            if (snapshot.get("reading") != null) entry.setReading(String.valueOf(snapshot.get("reading")));
            if (snapshot.get("questionType") != null) entry.setQuestionType(String.valueOf(snapshot.get("questionType")));
            if (snapshot.get("questionText") != null) entry.setQuestionTextJa(String.valueOf(snapshot.get("questionText")));
            if (snapshot.get("correctValue") != null) entry.setCorrectValue(String.valueOf(snapshot.get("correctValue")));
            if (snapshot.get("explanation") != null) entry.setExplanationJa(String.valueOf(snapshot.get("explanation")));
            List<JapaneseModels.ChoiceRow> choices = promptedChoiceRows(entry, choicesByQuestion, snapshot);
            JapaneseModels.TestQuestionRow question = new JapaneseModels.TestQuestionRow(
                    entry.getEntryId(), entry.getOrderNo() == null ? 0 : entry.getOrderNo(),
                    entry.getEntryState(), entry.getJudgment(), intValue(entry.getAnswerCount()),
                    intValue(entry.getWrongCount()), value(entry.getActiveMs()),
                    iso(entry.getAnsweredAt()), entry.getQuestionId(), entry.getWordId(), entry.getWord(),
                    entry.getReading(),
                    entry.getQuestionType() == null
                            ? (snapshot.get("questionType") == null ? null : String.valueOf(snapshot.get("questionType")))
                            : entry.getQuestionType(),
                    entry.getQuestionTextJa() == null
                            ? (snapshot.get("questionText") == null ? null : String.valueOf(snapshot.get("questionText")))
                            : entry.getQuestionTextJa(),
                    entry.getCorrectValue() == null
                            ? (snapshot.get("correctValue") == null ? null : String.valueOf(snapshot.get("correctValue")))
                            : entry.getCorrectValue(),
                    entry.getExplanationJa(), entry.getBook(), entry.getCategory());
            views.add(new JapaneseModels.TestQuestionView(question, choices, snapshot, parsePromptedChoices(entry.getHistoryJson())));
        }
        return views;
    }

    /**
     * その出題で画面に出す選択肢。
     *
     * <p>まず {@code 出題選択肢JSON}（テスト作成時に固定した今回提示の 4 択）。無ければ古い出題
     * として、プール（{@code 問題ID}）→ スナップショットの {@code choices} の順に回退する。</p>
     *
     * <p>画面が回答に使う値は {@code choiceKey}: プールの選択肢ID があればそれ、無ければ表示順。
     * A・B はプールを持たないので表示順（1 から）で答える（今の画面と同じ送り方）。</p>
     */
    private List<JapaneseModels.ChoiceRow> promptedChoiceRows(JpnTestQuestionEntity entry,
                                                              Map<Long, List<JpnChoiceEntity>> choicesByQuestion,
                                                              Map<String, Object> snapshot) {
        List<Map<String, Object>> prompted = parsePromptedChoices(entry.getChoicesJson());
        List<JapaneseModels.ChoiceRow> choices = new ArrayList<>();
        if (!prompted.isEmpty()) {
            for (Map<String, Object> item : prompted) {
                choices.add(promptedChoiceRow(choices.size() + 1, item.get(CHOICE_ID_KEY),
                        item.get(CHOICE_VALUE_KEY), item.get(CHOICE_READING_KEY),
                        item.get(CHOICE_CORRECT_KEY), null));
            }
            return choices;
        }
        if (entry.getQuestionId() != null) {
            List<JpnChoiceEntity> rows = choicesByQuestion.getOrDefault(entry.getQuestionId(), List.of());
            for (JpnChoiceEntity choice : rows) {
                choices.add(new JapaneseModels.ChoiceRow(choice.getChoiceId(),
                        choice.getOrderNo() == null ? 0 : choice.getOrderNo(), choice.getValue(),
                        choice.getReading(), Boolean.TRUE.equals(choice.getCorrect()),
                        choice.getDescriptionJa(), choice.getChoiceId()));
            }
        } else if (snapshot.get("choices") instanceof List<?> values) {
            int order = 1;
            for (Object value : values) {
                String text = value == null ? "" : String.valueOf(value);
                boolean correct = text.equals(String.valueOf(snapshot.get("correctValue")));
                // A・B はプールの選択肢ID を持たないので、並び順（1 から）で答える
                choices.add(new JapaneseModels.ChoiceRow(null, order, text, null, correct, null, (long) order));
                order += 1;
            }
        }
        return choices;
    }

    /** {@code 出題選択肢JSON} の 1 件を画面の選択肢へ写す。 */
    private static JapaneseModels.ChoiceRow promptedChoiceRow(int orderNo, Object choiceId, Object value,
                                                              Object reading, Object correct, String description) {
        Long id = choiceId instanceof Number number ? number.longValue() : null;
        return new JapaneseModels.ChoiceRow(id, orderNo, value == null ? null : String.valueOf(value),
                reading == null ? null : String.valueOf(reading), Boolean.TRUE.equals(correct), description,
                // プールの選択肢ID があればそれ、無ければ表示順（A・B の送り方に合わせる）
                id == null ? (long) orderNo : id);
    }

    /** 値を JSON にする（出題選択肢JSON は jsonb なので、必ず配列の文字列で渡す）。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception cause) {
            throw new IllegalStateException("出題の選択肢を作れませんでした。", cause);
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception cause) {
            log.warn("出題のスナップショットを読めませんでした（先頭 60 文字）: {}",
                    json.substring(0, Math.min(60, json.length())));
            return Map.of();
        }
    }

    // -------------------------------------------------------------------- 内部

    private JpnWordEntity requireWord(long accountId, long wordId) {
        JpnWordEntity word = wordMapper.findById(wordId, accountId);
        if (word == null) {
            throw new NotFoundException("単語が見つかりません。");
        }
        return word;
    }

    private JpnTestEntity requireTest(long accountId, long testId) {
        JpnTestEntity test = testMapper.findById(testId);
        if (test == null || test.getAccountId() == null || test.getAccountId() != accountId) {
            throw new NotFoundException("テストが見つかりません。");
        }
        return test;
    }

    private static String normalizeInput(String text, boolean reading) {
        String value = java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFKC).strip();
        if (!reading) return value;
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) result.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - 96) : c);
        return result.toString();
    }

    private String nextTestNo() {
        String base = "JT-" + LocalDateTime.now().format(TEST_NO_FORMAT);
        String candidate = base;
        int suffix = 1;
        while (testMapper.findByNo(candidate) != null) {
            suffix += 1;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

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
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long value(Long number) {
        return number == null ? 0L : number;
    }

    private static int intValue(Integer number) {
        return number == null ? 0 : number;
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }

    private static String iso(LocalDate value) {
        return value == null ? null : value.toString();
    }

    /**
     * 有効版のヘッダと 11 の子テーブルを、旧 {@code 詳細JSON} と同じ形の detail へ組み立てる。
     *
     * <p>組み立ては {@link JpnWordDetailAssembler}（common-core の純関数）。admin-api の
     * AI 取得も同じ組み立てを使うので、画面が読む形と AI に渡す形が食い違わない。</p>
     */
    private JapaneseModels.WordDetailView toDetailView(JpnWordDetailEntity entity) {
        return toDetailView(entity, null);
    }

    /**
     * 版を画面の形（詳細 JSON）に組み立てる。
     *
     * @param alternateReading 同じ見出し語で別の読みを持つ語の読み（無ければ null）。
     *                         画面が「同じ表記に「〜」の読みもあります」と出すために入れる。
     *                         2.0 は 詳細JSON に持っていたが、2.1 は語の表記から引く（{@link JpnWordMapper}）
     */
    private JapaneseModels.WordDetailView toDetailView(JpnWordDetailEntity entity, String alternateReading) {
        if (entity == null) {
            return null;
        }
        long detailId = entity.getDetailId() == null ? 0L : entity.getDetailId();
        Map<String, Object> detail = JpnWordDetailAssembler.assemble(entity, childrenOf(detailId));
        if (detail != null && alternateReading != null) {
            detail.put("alternateReading", alternateReading);
        }
        return new JapaneseModels.WordDetailView(detailId,
                entity.getContentVersion() == null ? 1 : entity.getContentVersion(),
                entity.getVersion() == null ? 1 : entity.getVersion(),
                entity.getAiProvider(),
                entity.getAiModel(), iso(entity.getFetchedAt()),
                detail == null ? Map.of() : detail);
    }

    /** 版に属する 11 の子テーブルの行（並びは SQL が決める）。 */
    private JpnWordDetailChildren.Rows childrenOf(long detailId) {
        return new JpnWordDetailChildren.Rows(
                detailMapper.listSenses(detailId),
                detailMapper.listExamples(detailId),
                detailMapper.listPatterns(detailId),
                dialogsOf(detailId),
                detailMapper.listSynonyms(detailId),
                detailMapper.listCautions(detailId),
                detailMapper.listCollocations(detailId),
                detailMapper.listRelatedWords(detailId),
                detailMapper.listUsageNotes(detailId),
                detailMapper.listPractices(detailId));
    }

    /**
     * 会話は 2 層（{@code dialogs[].lines[]}）。子テーブルが 2 つに分かれているので、
     * 発言を {@code 会話ID} で親へぶら下げ直す（発言は詳細ID を持たない）。
     */
    private List<JpnWordDetailChildren.Dialog> dialogsOf(long detailId) {
        List<JpnWordDetailChildren.Dialog> dialogs = detailMapper.listDialogs(detailId);
        if (dialogs == null || dialogs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<JpnWordDetailChildren.DialogLine>> linesByDialog = new java.util.LinkedHashMap<>();
        for (JpnWordDetailChildren.DialogLine line : detailMapper.listDialogLines(detailId)) {
            linesByDialog.computeIfAbsent(line.getDialogId(), key -> new ArrayList<>()).add(line);
        }
        for (JpnWordDetailChildren.Dialog dialog : dialogs) {
            dialog.setLines(linesByDialog.getOrDefault(dialog.getDialogId(), List.of()));
        }
        return dialogs;
    }

    /**
     * 一覧に出てくる語の「詳細情報件数」（有効版の段落の行数）。
     *
     * <p>1 ページぶんの語 ID でまとめて引く（1 語ずつ引くと N+1 になる）。
     * 有効版が無い語は結果に現れない＝{@code null}（画面は「—」）。</p>
     */
    private Map<Long, JapaneseModels.WordDetailCounts> detailCountsOf(List<JpnWordEntity> rows) {
        List<Long> wordIds = rows.stream()
                .map(JpnWordEntity::getWordId)
                .filter(Objects::nonNull)
                .toList();
        if (wordIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, JapaneseModels.WordDetailCounts> counts = new HashMap<>();
        for (JpnDetailCountEntity row : detailMapper.listActiveSectionCounts(wordIds)) {
            counts.put(row.getWordId(), new JapaneseModels.WordDetailCounts(
                    countOf(row.getSenseCount()), countOf(row.getExampleCount()),
                    countOf(row.getPatternCount()), countOf(row.getDialogCount()),
                    countOf(row.getSynonymCount()), countOf(row.getCautionCount()),
                    countOf(row.getCollocationCount()), countOf(row.getRelatedWordCount()),
                    countOf(row.getUsageNoteCount()), countOf(row.getPracticeCount())));
        }
        return counts;
    }

    private static int countOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static JapaneseModels.WordRow toWordRow(JpnWordEntity entity) {
        return toWordRow(entity, null);
    }

    private static JapaneseModels.WordRow toWordRow(JpnWordEntity entity,
                                                    JapaneseModels.WordDetailCounts detailCounts) {
        return new JapaneseModels.WordRow(
                entity.getWordId() == null ? 0L : entity.getWordId(),
                entity.getWord(), entity.getReading(), entity.getJlptLevel(), entity.getPartOfSpeech(),
                entity.getChineseMeaning(),
                entity.getStateCode(), entity.getNote(), entity.getVersion() == null ? 1 : entity.getVersion(),
                entity.getBook(), entity.getCategory(), entity.getLevel(), entity.getWordSeq(),
                entity.getCollectionCount() == null ? 0L : entity.getCollectionCount(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                Boolean.TRUE.equals(entity.getFavorite()), Boolean.TRUE.equals(entity.getLearned()),
                iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()),
                // 取得状態は 5 つの内容種別を一覧の 4 区画（A・B／C／D／E）にまとめる
                new JapaneseModels.WordAiState(entity.getDetailAiState(),
                        orElseState(entity.getReadingProblemAiState(), entity.getKanjiProblemReadingState()),
                        entity.getContextProblemAiState(), entity.getKanjiProblemAiState()),
                // 詳細情報件数は一覧の検索のときだけ入る（1 件取得・登録の戻りでは null）
                detailCounts);
    }

    /**
     * C の区画は C1（表記→読み）と C2（読み→表記）の 2 つをまとめて見せる。
     * どちらかが成功していれば「取得済」に見せたいので、成功を優先する。
     */
    private static String orElseState(String first, String second) {
        if ("SUCCEEDED".equals(first) || "SUCCEEDED".equals(second)) {
            return "SUCCEEDED";
        }
        if (first != null) {
            return first;
        }
        return second;
    }

    static JapaneseModels.TestRow toTestRow(JpnTestEntity entity) {
        int done = intValue(entity.getDoneCount());
        int correct = intValue(entity.getCorrectCount());
        int judged = correct + intValue(entity.getWrongCount());
        int score = judged == 0 ? 0 : (int) Math.round(correct * 100.0 / judged);
        return new JapaneseModels.TestRow(
                entity.getTestId() == null ? 0L : entity.getTestId(), entity.getTestNo(), entity.getTestType(),
                entity.getLevel(), entity.getBook(), entity.getCategoryFrom(), entity.getCategoryTo(),
                entity.getDifficulty(), entity.getMode(), intValue(entity.getQuestionCount()), done, correct,
                intValue(entity.getWrongCount()), entity.getStateCode(), iso(entity.getStartedAt()),
                iso(entity.getFinishedAt()), iso(entity.getLastStudiedAt()), value(entity.getActiveMs()),
                score, entity.getVersion() == null ? 1 : entity.getVersion());
    }

    private static JapaneseModels.StatusRow toStatusRow(JpnStatusEntity entity) {
        return new JapaneseModels.StatusRow(
                entity.getWordId() == null ? 0L : entity.getWordId(), entity.getWord(), entity.getReading(),
                entity.getJlptLevel(), entity.getPartOfSpeech(), entity.getBook(), entity.getCategory(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                Boolean.TRUE.equals(entity.getLearned()), Boolean.TRUE.equals(entity.getFavorite()),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                intValue(entity.getWrongCount()), intValue(entity.getStreak()), intValue(entity.getBestStreak()),
                value(entity.getActiveMs()), entity.getLastTestType(), entity.getLastJudgment(),
                iso(entity.getFirstStudiedAt()), iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()),
                intValue(entity.getReviewIntervalDays()));
    }

    private static JapaneseModels.SkillRow toSkillRow(JpnSkillEntity entity) {
        return new JapaneseModels.SkillRow(
                entity.getWordId() == null ? 0L : entity.getWordId(), entity.getWord(), entity.getReading(),
                entity.getTestType(), entity.getSkillCode(),
                entity.getLearnState() == null ? "NOT_STARTED" : entity.getLearnState(),
                entity.getMastery() == null ? BigDecimal.ZERO : entity.getMastery(),
                intValue(entity.getAnsweredCount()), intValue(entity.getCorrectCount()),
                intValue(entity.getWrongCount()), intValue(entity.getStreak()), intValue(entity.getBestStreak()),
                entity.getLastJudgment(), iso(entity.getLastStudiedAt()), iso(entity.getNextReviewAt()));
    }

    /** 出題の判定（内部用）。 */
    private record AnswerJudgement(boolean correct, String judgment, String correctValue, String explanation,
                                   String questionType) {
    }
}

