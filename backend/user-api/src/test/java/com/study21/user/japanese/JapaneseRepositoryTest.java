package com.study21.user.japanese;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import com.study21.user.account.AccountService;
import com.study21.user.account.AccountType;
import com.study21.user.account.RegisterRequest;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する日本語勉強の検証。
 *
 * <p><strong>データに依存しない</strong>: かつては「2.0 から移行した単語 9,847 語・テスト 15 回・
 * 学習状況 213 語」を前提に件数を断言していたが、その移行データは削除された。いまは
 * <b>このテストが自分でデータを作り</b>（{@link JapaneseService#registerWords} で語と収録、
 * 必要なら詳細の版も）、「読める・行動が正しい」ことだけを確かめる。したがって歴史的な件数や
 * 旧書籍名（{@code 01.N1~N5日本語単語}）は断言しない。</p>
 *
 * <p>テストはロールバックするので DB は汚れない（作った語・収録・テスト・学習状況は残らない）。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class JapaneseRepositoryTest {

    /** このテストが作る語（見出し語・読み・収録先）。読みは必須ではないが、出題に要るので入れる。 */
    private static final List<String[]> TEST_WORDS = List.of(
            new String[]{"検証用語1", "けんしょうようごいち"},
            new String[]{"検証用語2", "けんしょうようごに"},
            new String[]{"検証用語3", "けんしょうようごさん"},
            new String[]{"検証用語4", "けんしょうようごよん"});

    @Autowired
    private JapaneseService japaneseService;

    @Autowired
    private AccountService accountService;

    /** AI が書いた版と同じ形の行を作るために使う（画面の編集は 品詞 を単語情報の値で上書きする）。 */
    @Autowired
    private com.study21.user.japanese.JpnWordDetailMapper detailMapper;

    /** テスト専用の SQL（生成履歴・問題の行を作る。MyBatis 経由なので SQL ログに残る）。 */
    @Autowired
    private com.study21.user.testing.TestSqlMapper sql;

    /** 検証用のアカウントをこのテストの中で作る（実在のアカウントの学習状況を汚さない）。 */
    private long createStudentAccountId() {
        String email = "e2e-jp-test-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setParentEmail(email);
        request.setParentPassword("Parent1234");
        request.setSei("検証");
        request.setMei("保護者");
        request.setSeiKana("けんしょう");
        request.setMeiKana("ほごしゃ");
        request.setGrade("中学1年生");
        request.setStudentEmail("s-" + email);
        request.setStudentPassword("Student1234");
        request.setAgreed(true);
        return accountService.register(request).getStudentAccountId();
    }

    private UserPrincipal student(long accountId) {
        return new UserPrincipal(accountId, "e2e@example.com", "試験 生徒", AccountType.STUDENT);
    }

    /**
     * 検証用の語と収録を作る（このテストの中だけで使う書籍名にする）。
     *
     * <p>書籍名は実行ごとに変える。書籍マスタは名前で引くので、他のテストと同じ名前だと
     * 「同じ Unit に登録済み」で飛ばされることがある（テストは並行にも走る）。</p>
     */
    private String registerTestWords(UserPrincipal user) {
        String book = "検証用書籍-" + System.nanoTime();
        List<JapaneseModels.RegisterWord> words = new java.util.ArrayList<>();
        int seq = 1;
        for (String[] pair : TEST_WORDS) {
            words.add(new JapaneseModels.RegisterWord(null, pair[0], pair[1], "N1-N5",
                    book, "Unit001", seq, null, null, null));
            seq += 1;
        }
        JapaneseModels.RegisterResult result =
                japaneseService.registerWords(user, new JapaneseModels.RegisterRequest(words));

        assertThat(result.wordCount()).isEqualTo(TEST_WORDS.size());
        assertThat(result.collectionCount()).isEqualTo(TEST_WORDS.size());
        assertThat(result.skippedCount()).isZero();
        return book;
    }

    /** その書籍の語を一覧から取る（登録した語が消えていないことを確かめるため）。 */
    private JapaneseModels.WordListResult searchTestWords(long accountId, String book) {
        return japaneseService.searchWords(accountId, null, null, null, null, null, book, null, null, null, 1, 20);
    }

    /** 一覧を分類の範囲で絞る（画面の「分類：From ～ To」）。 */
    private JapaneseModels.WordListResult searchByCategory(long accountId, String book,
                                                           String categoryFrom, String categoryTo) {
        return japaneseService.searchWords(accountId, null, null, null, null, null, book,
                categoryFrom, categoryTo, null, 1, 20);
    }

    @Test
    @DisplayName("AI 取得の対象: 検索条件に一致する語を、ページを問わずに選ぶ（実 DB の SQL が動く）")
    void selectsAiTargetsForTheWholeFilteredSet() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);

        // ページは関係しない（1 ページ目でも、条件に一致する語が対象になる）
        JapaneseModels.AiTargets targets =
                japaneseService.aiTargets(accountId, null, null, null, null, null, book, null, null, null,
                        "DETAIL", true, 200);

        assertThat(targets.total()).isEqualTo(TEST_WORDS.size());
        assertThat(targets.acquired()).isZero();          // まだ 1 語も取得していない
        assertThat(targets.candidates()).isEqualTo(TEST_WORDS.size());
        assertThat(targets.wordIds()).hasSize(TEST_WORDS.size());
        assertThat(targets.overLimit()).isZero();
        // 表示順（書籍 → 分類 → 単語SEQ → 単語ID）で選ぶ
        assertThat(targets.wordIds()).isSorted();

        // 上限で切る（次回に回す数を返す）
        JapaneseModels.AiTargets limited =
                japaneseService.aiTargets(accountId, null, null, null, null, null, book, null, null, null,
                        "DETAIL", true, 2);
        assertThat(limited.wordIds()).hasSize(2);
        assertThat(limited.overLimit()).isEqualTo(TEST_WORDS.size() - 2L);

        // 「すべて再取得」は一致する語をそのまま対象にする（candidates が一致総数）
        JapaneseModels.AiTargets all =
                japaneseService.aiTargets(accountId, null, null, null, null, null, book, null, null, null,
                        "DETAIL", false, 200);
        assertThat(all.candidates()).isEqualTo(TEST_WORDS.size());
    }

    @Test
    @DisplayName("AI 取得の対象: C（C1・C2 をまとめる）でも実 DB の SQL が動く")
    void selectsAiTargetsForKindC() {
        long accountId = createStudentAccountId();
        String book = registerTestWords(student(accountId));

        // C は CASE で C1・C2 をまとめる（NULL の扱いが SQL 側で難しいところ）
        JapaneseModels.AiTargets targets =
                japaneseService.aiTargets(accountId, null, null, null, null, null, book, null, null, null,
                        "C", true, 200);

        assertThat(targets.total()).isEqualTo(TEST_WORDS.size());
        assertThat(targets.wordIds()).hasSize(TEST_WORDS.size());
    }

    @Test
    @DisplayName("AI 取得の対象: 絞り込みが空でも「取得済みをスキップ」が効く（実 DB）")
    void skipsAcquiredEvenWithoutFilters() {
        long accountId = createStudentAccountId();

        // 絞り込みを 1 つも渡さない（画面で条件を入れずに【AI 取得】を押した場合。
        // 条件を <where> の外へ素の AND で書くと、ここで「効かない条件」になる）
        JapaneseModels.AiTargets skip = japaneseService.aiTargets(accountId, null, null, null, null, null,
                null, null, null, null, "DETAIL", true, 200);
        JapaneseModels.AiTargets all = japaneseService.aiTargets(accountId, null, null, null, null, null,
                null, null, null, null, "DETAIL", false, 200);

        // 「すべて再取得」の候補は一致する語の全部
        assertThat(all.candidates()).isEqualTo(all.total());
        // 「スキップ」の候補は取得済み（と取得中）を除いた数
        assertThat(skip.candidates()).isLessThanOrEqualTo(skip.total() - skip.acquired());
        // 窓の数字と実際に選ばれる語が食い違わない（条件が効いていなければ取得済みも返ってしまう）
        assertThat(skip.wordIds()).hasSize((int) Math.min(skip.candidates(), 200));
        // 返る語に「取得済み」が混ざらない（混ざると成功済みの語にもう一版積む＝無駄な課金）
        if (!skip.wordIds().isEmpty()) {
            String ids = skip.wordIds().stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            java.util.List<java.util.Map<String, Object>> rows = sql.query(
                    "SELECT count(*) AS \"n\" FROM public.\"v_jpn_word_ai_state\""
                            + " WHERE \"単語ID\" IN (" + ids + ")"
                            + " AND \"内容種別コード\" = 'A_DETAIL' AND \"成功あり\"");
            assertThat(((Number) rows.get(0).get("n")).longValue()).isZero();
        }
    }

    /**
     * 分類の範囲の検証用に、Unit を跨いで語を 1 冊ぶん登録する。
     *
     * @return 書籍名
     */
    private String registerUnitRangeWords(UserPrincipal user) {
        String book = "分類範囲検証用-" + System.nanoTime();
        List<JapaneseModels.RegisterWord> words = new java.util.ArrayList<>();
        String[] units = {"Unit001", "Unit002", "Unit003"};
        int seq = 1;
        for (String unit : units) {
            words.add(new JapaneseModels.RegisterWord(null, "範囲検証" + unit, "はんいけんしょう" + seq,
                    "N1-N5", book, unit, 1, null, null, null));
            seq += 1;
        }
        JapaneseModels.RegisterResult result =
                japaneseService.registerWords(user, new JapaneseModels.RegisterRequest(words));
        assertThat(result.wordCount()).isEqualTo(units.length);
        return book;
    }

    @Test
    @DisplayName("分類は From と To の範囲で絞り込める（片方だけでも効く）")
    void filtersWordsByCategoryRange() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerUnitRangeWords(user);

        // 範囲なし: 3 Unit ぶん全部
        assertThat(searchByCategory(accountId, book, null, null).items())
                .extracting(JapaneseModels.WordRow::category)
                .containsExactlyInAnyOrder("Unit001", "Unit002", "Unit003");

        // From だけ: その Unit 以降
        assertThat(searchByCategory(accountId, book, "Unit002", null).items())
                .extracting(JapaneseModels.WordRow::category)
                .containsExactlyInAnyOrder("Unit002", "Unit003");

        // To だけ: その Unit まで（今回できるようにした）
        assertThat(searchByCategory(accountId, book, null, "Unit002").items())
                .extracting(JapaneseModels.WordRow::category)
                .containsExactlyInAnyOrder("Unit001", "Unit002");

        // 両方: その範囲の中だけ（同じ収録の行で見る）
        assertThat(searchByCategory(accountId, book, "Unit002", "Unit002").items())
                .extracting(JapaneseModels.WordRow::category)
                .containsExactly("Unit002");
    }

    /** 一覧の行を 1 件（単語ID で引く）。 */
    private static JapaneseModels.WordRow rowOf(JapaneseModels.WordListResult result, long wordId) {
        return result.items().stream()
                .filter(item -> item.wordId() == wordId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("一覧に行がありません: " + wordId));
    }

    /**
     * AI 取得（batC41）と同じ形で、有効版の詳細を 1 つ入れる（品詞と語義の中国語つき）。
     *
     * <p>画面の編集（{@code saveWordEditor}）は 品詞・JLPT を**単語情報の値で上書きする**ので、
     * 「詳細にだけ品詞がある」状態はこちらで作る（AI が書いた版と同じ）。</p>
     */
    private void insertAiDetail(long wordId, String partOfSpeech, String chinese) {
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        header.setWordId(wordId);
        header.setStateCode("ACTIVE");
        header.setAiProvider("deepseek");
        header.setAiModel("deepseek-v4-flash");
        header.setFetchedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        header.setManualCorrected(false);
        header.setCoreMeaning("検証の核心。");
        header.setDescriptionZh("验证用的说明。");
        header.setPartOfSpeech(partOfSpeech);
        header.setCreatedBy(2L);
        detailMapper.insertDetailVersion(header);

        JpnWordDetailChildren.Sense sense = new JpnWordDetailChildren.Sense();
        sense.setOrderNo(1);
        sense.setSenseNumber(1);
        sense.setJapanese("検証の語義。");
        sense.setChinese(chinese);
        sense.setSourceCode("BATCH");
        sense.setManualCorrected(false);
        detailMapper.insertSenses(header.getDetailId(), List.of(sense));
    }

    @Test
    @DisplayName("一覧の「詳細情報件数」は、有効版の段落の行数を数えて返す（詳細が無い語は null）")
    void listShowsDetailCountsPerSection() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().get(0).wordId();
        long otherWordId = searchTestWords(accountId, book).items().get(1).wordId();

        // まだ詳細が無い語は数える対象が無い（画面は「—」）
        assertThat(rowOf(searchTestWords(accountId, book), wordId).detailCounts()).isNull();

        // AI 取得の代わりに詳細を 1 版入れる（語義 1・例文 2・文型 1・会話 1 の版）
        insertAiDetail(wordId, "副詞", "验证用的释义（中文）。");
        long detailId = detailMapper.findActiveDetail(wordId).getDetailId();

        JpnWordDetailChildren.Example example = new JpnWordDetailChildren.Example();
        example.setOrderNo(1);
        example.setJapanese("検証の例文1。");
        example.setChinese("验证例句1。");
        example.setSourceCode("BATCH");
        example.setManualCorrected(false);
        JpnWordDetailChildren.Example example2 = new JpnWordDetailChildren.Example();
        example2.setOrderNo(2);
        example2.setJapanese("検証の例文2。");
        example2.setChinese("验证例句2。");
        example2.setSourceCode("BATCH");
        example2.setManualCorrected(false);
        detailMapper.insertExamples(detailId, List.of(example, example2));

        JpnWordDetailChildren.Pattern pattern = new JpnWordDetailChildren.Pattern();
        pattern.setOrderNo(1);
        pattern.setPattern("検証**を**する");
        pattern.setChinese("做验证");
        pattern.setSourceCode("BATCH");
        pattern.setManualCorrected(false);
        detailMapper.insertPatterns(detailId, List.of(pattern));

        JpnWordDetailChildren.Dialog dialog = new JpnWordDetailChildren.Dialog();
        dialog.setOrderNo(1);
        dialog.setScene("検証の場面");
        dialog.setSourceCode("BATCH");
        dialog.setManualCorrected(false);
        detailMapper.insertDialogs(detailId, List.of(dialog));
        // 会話の発言は数えない（会話 1 件のまま）
        JpnWordDetailChildren.DialogLine line = new JpnWordDetailChildren.DialogLine();
        line.setDialogId(detailMapper.listDialogs(detailId).get(0).getDialogId());
        line.setOrderNo(1);
        line.setSpeaker("A");
        line.setJapanese("検証ですか。");
        line.setChinese("是验证吗。");
        line.setSourceCode("BATCH");
        line.setManualCorrected(false);
        detailMapper.insertDialogLines(detailId, List.of(line));

        JapaneseModels.WordDetailCounts counts =
                rowOf(searchTestWords(accountId, book), wordId).detailCounts();
        assertThat(counts).isNotNull();
        assertThat(counts.senses()).isEqualTo(1);
        assertThat(counts.examples()).isEqualTo(2);
        assertThat(counts.patterns()).isEqualTo(1);
        assertThat(counts.dialogs()).isEqualTo(1);
        // まだ入れていない段落は 0（画面は 0 も出す＝2.0 と同じ「何が足りないか」が見える）
        assertThat(counts.synonyms()).isZero();
        assertThat(counts.cautions()).isZero();
        assertThat(counts.collocations()).isZero();
        assertThat(counts.relatedWords()).isZero();
        assertThat(counts.usageNotes()).isZero();
        assertThat(counts.practices()).isZero();

        // 詳細を持たない語は null のまま（一覧に混ざっていても間違って他語の数を出さない）
        assertThat(rowOf(searchTestWords(accountId, book), otherWordId).detailCounts()).isNull();
    }

    @Test
    @DisplayName("問題（C/D/E）の履歴を出し、使う版を切り替えられる（2.0 の詳細情報取得履歴と同じ）")
    void switchesTheQuestionVersionToUse() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().get(0).wordId();

        // 取得 2 回ぶん（版 1 = 古い・版 2 = 今使っている）を、AI が書いた形で作る。
        // 作り直すと古い版は ARCHIVED になる（消さない）
        insertGeneration(wordId, "C1_READING", 1, "SUCCEEDED", "qwen", "qwen3.7-plus");
        insertQuestion(wordId, "C1_READING", 1, "版1の問題", "あい", "ARCHIVED");
        insertGeneration(wordId, "C1_READING", 2, "SUCCEEDED", "deepseek", "deepseek-v4-flash");
        insertQuestion(wordId, "C1_READING", 2, "版2の問題", "あい", "ACTIVE");

        JapaneseModels.WordQuestionVersionList versions =
                japaneseService.questionVersions(accountId, wordId);

        assertThat(versions.items()).hasSize(2);
        // 新しい版が先（版 2 が使用中）
        assertThat(versions.items().get(0).contentVersion()).isEqualTo(2);
        assertThat(versions.items().get(0).active()).isTrue();
        assertThat(versions.items().get(0).questionCount()).isEqualTo(1);
        assertThat(versions.items().get(0).aiModel()).isEqualTo("deepseek-v4-flash");
        assertThat(versions.items().get(1).contentVersion()).isEqualTo(1);
        assertThat(versions.items().get(1).active()).isFalse();

        // 古い版を使うように切り替える（問題は消さない）
        JapaneseModels.WordQuestionVersionList switched =
                japaneseService.activateQuestionVersion(user, wordId, "C1_READING", 1);

        assertThat(switched.items()).filteredOn(item -> item.contentVersion() == 1)
                .singleElement()
                .satisfies(item -> assertThat(item.active()).isTrue());
        assertThat(switched.items()).filteredOn(item -> item.contentVersion() == 2)
                .singleElement()
                .satisfies(item -> assertThat(item.active()).isFalse());

        // 失敗した版（問題が 0 件）は選べない
        insertGeneration(wordId, "C1_READING", 3, "FAILED", "qwen", "qwen3.7-plus");
        assertThatThrownBy(() -> japaneseService.activateQuestionVersion(user, wordId, "C1_READING", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("問題がありません");
        // 知らない版は 404
        assertThatThrownBy(() -> japaneseService.activateQuestionVersion(user, wordId, "C1_READING", 99))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");
    }

    /** 生成履歴（AI の取得 1 回 = 1 版）を 1 行作る。 */
    private void insertGeneration(long wordId, String contentType, int contentVersion, String state,
                                  String provider, String model) {
        sql.execute("INSERT INTO public.\"JPN_AI生成履歴情報\" (\"単語ID\", \"内容種別コード\", \"状態コード\","
                + " \"AI区分\", \"モデル名\", \"内容版数\", \"生成件数\", \"失敗件数\", \"開始日時\", \"登録元コード\")"
                + " VALUES (" + wordId + ", '" + contentType + "', '" + state + "', '" + provider + "', '" + model
                + "', " + contentVersion + ", 1, 0, CURRENT_TIMESTAMP, 'BATCH')");
    }

    /** その版の問題を 1 件作る（AI が書いた形。選択肢は作らない＝件数だけ見る）。 */
    private void insertQuestion(long wordId, String questionType, int contentVersion, String questionJa,
                                String correctValue, String state) {
        sql.execute("INSERT INTO public.\"JPN_単語問題情報\" (\"単語ID\", \"問題種別\", \"問題番号\","
                + " \"問題文_日本語\", \"正解値\", \"難易度\", \"状態コード\", \"内容版数\", \"登録元コード\")"
                + " VALUES (" + wordId + ", '" + questionType + "', 1, '" + questionJa + "', '" + correctValue
                + "', 'NORMAL', '" + state + "', " + contentVersion + ", 'BATCH')");
    }

    @Test
    @DisplayName("一覧の「品詞」「中国語訳」は有効版の詳細から出す（人が入れた品詞が優先）")
    void wordListShowsPartOfSpeechAndChineseFromTheActiveDetail() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().get(0).wordId();

        // 登録しただけの語は、品詞も中国語訳も空（AI 未取得。画面は「—」）
        JapaneseModels.WordRow before = rowOf(searchTestWords(accountId, book), wordId);
        assertThat(before.partOfSpeech()).isNull();
        assertThat(before.chineseMeaning()).isNull();

        // AI 取得の代わりに、有効版の詳細を 1 つ入れる（詳細にだけ品詞がある状態）
        insertAiDetail(wordId, "副詞", "验证用的释义（中文）。");

        JapaneseModels.WordRow after = rowOf(searchTestWords(accountId, book), wordId);
        assertThat(after.partOfSpeech()).isEqualTo("副詞");
        assertThat(after.chineseMeaning()).isEqualTo("验证用的释义（中文）。");

        // 絞り込みも表示と同じ値を見る（詳細の品詞で絞り込める）
        assertThat(japaneseService.searchWords(accountId, null, null, null, "副詞", null, book, null, null, null, 1, 20)
                .items()).extracting(JapaneseModels.WordRow::wordId).contains(wordId);
        assertThat(japaneseService.searchWords(accountId, null, null, null, "名詞", null, book, null, null, null, 1, 20)
                .items()).isEmpty();

        // 人が単語情報の品詞を直したら、そちらが優先（詳細の版の値で上書きしない）
        japaneseService.updateWord(user, wordId, new JapaneseModels.WordSaveRequest(
                "検証用語1", "けんしょうようごいち", "N3", "名詞", "ACTIVE", "検証", after.version()));

        JapaneseModels.WordRow manual = rowOf(searchTestWords(accountId, book), wordId);
        assertThat(manual.partOfSpeech()).isEqualTo("名詞");
        // 中国語訳は詳細のまま（単語情報には列が無い）
        assertThat(manual.chineseMeaning()).isEqualTo("验证用的释义（中文）。");
    }

    @Test
    void registeredWordsAndCollectionsAreReadable() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);

        // 自分で入れた語が読める（件数は「入れた数」と一致する。歴史的な件数は見ない）
        JapaneseModels.WordListResult words = searchTestWords(accountId, book);

        assertThat(words.totalElements()).isEqualTo(TEST_WORDS.size());
        assertThat(words.items()).hasSize(TEST_WORDS.size());
        assertThat(words.items()).allSatisfy(item -> {
            assertThat(item.word()).isNotBlank();
            assertThat(item.reading()).isNotBlank();
            assertThat(item.learnState()).isEqualTo("NOT_STARTED");
            assertThat(item.collectionCount()).isGreaterThanOrEqualTo(1);
            assertThat(item.book()).isEqualTo(book);
        });

        // 見出し語と読みで絞り込める（部分一致）
        JapaneseModels.WordListResult searched = japaneseService.searchWords(accountId, "検証用語1",
                "けんしょうようごいち", null, null, null, book, null, null, null, 1, 20);
        assertThat(searched.items()).extracting(JapaneseModels.WordRow::word).contains("検証用語1");
    }

    @Test
    void wordDetailReturnsCollectionsAndEditorVersions() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().get(0).wordId();

        JapaneseModels.WordDetailResult detail = japaneseService.wordDetail(accountId, wordId);

        assertThat(detail.word().word()).isEqualTo("検証用語1");
        assertThat(detail.collections()).isNotEmpty();
        assertThat(detail.collections().get(0).book()).isEqualTo(book);
        // まだ問題も詳細も無い（このテストが作った語なので、他人のデータに依存しない）
        assertThat(detail.questions()).isEmpty();
        assertThat(detail.detail()).isNull();

        // 画面の編集で最初の版を作り、読み直せる（版管理は切片2〜5 の既存の振る舞い）
        JapaneseModels.WordDetailResult saved = japaneseService.saveWordEditor(user, wordId,
                new JapaneseModels.WordEditorRequest(
                        new JapaneseModels.WordSaveRequest("検証用語1", "けんしょうようごいち", "N3", "名詞",
                                "ACTIVE", "検証", detail.word().version()),
                        0,
                        java.util.Map.of("descriptionJa", "検証用の説明。")));

        assertThat(saved.detail()).isNotNull();
        assertThat(saved.detail().contentVersion()).isEqualTo(1);
        assertThat(saved.detail().detail()).containsEntry("descriptionJa", "検証用の説明。");

        JapaneseModels.WordDetailVersions versions = japaneseService.detailVersions(accountId, wordId);
        assertThat(versions.items()).hasSize(1);
        assertThat(versions.items().get(0).active()).isTrue();
        assertThat(versions.items().get(0).contentVersion()).isEqualTo(1);
    }

    @Test
    void takesTestAnswersAndDeletesIt() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);

        for (var row : searchTestWords(accountId, book).items()) insertAiDetail(row.wordId(), "名詞", "検証の中国語意味");
        // 作成（B は単語から出題するので、問題テーブルが無くても作れる）
        JapaneseModels.TestDetailResult created = japaneseService.createTest(user,
                new JapaneseModels.TestCreateRequest("B", null, book, null, null, null, "RANDOM", 2));

        assertThat(created.test().testNo()).startsWith("JT-");
        assertThat(created.test().state()).isEqualTo("CREATED");
        assertThat(created.test().questionCount()).isEqualTo(2);
        assertThat(created.questions()).hasSize(2);

        // 1 問につき「今回提示した 4 択」が固定されている（正解はちょうど 1 件。切片6）
        assertThat(created.questions()).allSatisfy(view -> {
            assertThat(view.question().questionText()).isNotBlank();
            assertThat(view.question().correctValue()).isNotBlank();
            assertThat(view.choices()).isEmpty();
            assertThat(view.snapshot()).containsKey("wordDetail");
        });

        // 開き直しても同じ選択肢（出題選択肢JSON から返す）
        JapaneseModels.TestDetailResult reopened =
                japaneseService.testDetail(accountId, created.test().testId());
        assertThat(reopened.questions()).extracting(view -> view.choices().stream()
                        .map(JapaneseModels.ChoiceRow::value).toList())
                .isEqualTo(created.questions().stream().map(view -> view.choices().stream()
                        .map(JapaneseModels.ChoiceRow::value).toList()).toList());

        // 提示された中から選んで回答する（choiceKey で答える）
        for (JapaneseModels.TestQuestionView view : created.questions()) {
            JapaneseModels.AnswerResult result = japaneseService.answer(user, created.test().testId(),
                    new JapaneseModels.AnswerRequest(view.question().orderNo(), null, view.question().word(), 5_000L, view.question().reading()));
            assertThat(result.correct()).isTrue();
            assertThat(result.judgment()).isEqualTo("CORRECT");
        }

        JapaneseModels.TestRow finished = japaneseService.testDetail(accountId, created.test().testId()).test();
        assertThat(finished.doneCount()).isEqualTo(2);
        assertThat(finished.correctCount()).isEqualTo(2);
        assertThat(finished.scorePercent()).isEqualTo(100);
        assertThat(finished.state()).isEqualTo("COMPLETED");

        // 学習状況・技能習得・日次が更新されている（件数は「今答えた数」で見る）
        JapaneseModels.StatusResult status = japaneseService.status(accountId, null, null, 1, 20);
        assertThat(status.summary().studiedWordCount()).isEqualTo(2);
        assertThat(status.summary().answeredCount()).isEqualTo(2);
        assertThat(status.summary().correctCount()).isEqualTo(2);
        assertThat(status.summary().todayActiveMs()).isEqualTo(10_000L);
        assertThat(status.items()).allSatisfy(row -> {
            assertThat(row.mastery()).isGreaterThan(java.math.BigDecimal.ZERO);
            assertThat(row.nextReviewAt()).isNotNull();
        });
        JapaneseModels.SkillListResult skills = japaneseService.skills(accountId, "B", null, 1, 20);
        assertThat(skills.items()).hasSize(4);
        assertThat(skills.items()).allSatisfy(skill ->
                assertThat(skill.skillCode()).isIn("B_ORTHOGRAPHY", "B_READING_RECALL"));

        assertThat(japaneseService.testDetail(accountId, created.test().testId()).questions())
                .allSatisfy(v -> assertThat(v.history()).hasSize(1));
        // 完了 → 削除
        assertThat(japaneseService.completeTest(user, created.test().testId()).message()).contains("完了");
        japaneseService.deleteTest(user, created.test().testId());
        assertThatThrownBy(() -> japaneseService.testDetail(accountId, created.test().testId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void answersTypeATestWithoutSkillRows() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);

        // A（勉強）も単語から出題する（問題テーブルを使わない）
        JapaneseModels.TestDetailResult created = japaneseService.createTest(user,
                new JapaneseModels.TestCreateRequest("A", null, book, null, null, null, "RANDOM", 1));
        JapaneseModels.TestQuestionView view = created.questions().get(0);
        assertThat(view.choices()).isEmpty();

        // 実 DB では、A で技能習得の行を書こうとすると CHECK（B〜E だけ）に当たって回答が失敗していた
        JapaneseModels.AnswerResult result = japaneseService.answer(user, created.test().testId(),
                new JapaneseModels.AnswerRequest(view.question().orderNo(), null, "学習完了", 3_000L));

        assertThat(result.correct()).isTrue();
        assertThat(result.judgment()).isEqualTo("CONFIRMED");

        // 学習状況は A でも更新される（回答回数・学習時間・最終種別）
        JapaneseModels.StatusResult status = japaneseService.status(accountId, null, null, 1, 20);
        assertThat(status.summary().studiedWordCount()).isEqualTo(1);
        assertThat(status.summary().answeredCount()).isEqualTo(1);
        assertThat(status.summary().todayActiveMs()).isEqualTo(3_000L);
        assertThat(status.items()).hasSize(1);
        assertThat(status.items().get(0).answeredCount()).isEqualTo(1);
        assertThat(status.items().get(0).lastTestType()).isEqualTo("A");
        assertThat(status.items().get(0).lastJudgment()).isEqualTo("CONFIRMED");

        // 技能習得の行は A では作られない（作ると CHECK 違反になる）
        assertThat(japaneseService.skills(accountId, "A", null, 1, 20).items()).isEmpty();

        japaneseService.completeTest(user, created.test().testId());
        japaneseService.deleteTest(user, created.test().testId());
    }

    @Test
    void favoriteAndLearnedAreStoredPerAccount() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().get(0).wordId();

        JapaneseModels.WordMutationResult favorite = japaneseService.setFavorite(user, wordId, true);
        assertThat(favorite.word().favorite()).isTrue();

        JapaneseModels.WordMutationResult learned = japaneseService.setLearned(user, wordId, true);
        assertThat(learned.word().learned()).isTrue();
        assertThat(learned.word().learnState()).isEqualTo("MASTERED");

        JapaneseModels.StatusResult status = japaneseService.status(accountId, null, null, 1, 20);
        assertThat(status.summary().favoriteCount()).isEqualTo(1);
        assertThat(status.summary().learnedCount()).isEqualTo(1);

        // 解除もできる
        assertThat(japaneseService.setLearned(user, wordId, false).word().learned()).isFalse();
        assertThat(japaneseService.setFavorite(user, wordId, false).word().favorite()).isFalse();
    }

    @Test
    void createsUpdatesAndDeletesWord() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);

        // 新規登録（書籍と分類は既にあるので、収録だけが増える）
        JapaneseModels.WordMutationResult created = japaneseService.createWord(user,
                new JapaneseModels.WordSaveRequest("検証用追加語", "けんしょうようついかご", "N1", "[名]", "ACTIVE",
                        "テストで作成", null));
        long wordId = created.word().wordId();
        assertThat(created.word().word()).isEqualTo("検証用追加語");
        assertThat(created.word().learnState()).isEqualTo("NOT_STARTED");

        JapaneseModels.WordMutationResult updated = japaneseService.updateWord(user, wordId,
                new JapaneseModels.WordSaveRequest("検証用追加語2", "けんしょうようついかご2", "N2", "[名]", "ACTIVE",
                        null, created.word().version()));
        assertThat(updated.word().word()).isEqualTo("検証用追加語2");
        assertThat(updated.word().jlptLevel()).isEqualTo("N2");

        // 検索で見つかる（読みの部分一致）
        JapaneseModels.WordListResult found = japaneseService.searchWords(accountId, null,
                "けんしょうようついか", null, null, null, null, null, null, null, 1, 20);
        assertThat(found.items()).extracting(JapaneseModels.WordRow::wordId).contains(wordId);

        japaneseService.deleteWord(user, wordId);
        assertThatThrownBy(() -> japaneseService.wordDetail(accountId, wordId))
                .isInstanceOf(NotFoundException.class);

        // 元の検証用の語は残っている（削除は 1 語だけ）
        assertThat(searchTestWords(accountId, book).items()).hasSize(TEST_WORDS.size());
    }

    @Autowired private JpnTestMapper testMapper;
    @Autowired private JapaneseTestManagementService management;

    @Test
    void candidateScopeAndFrozenChoiceHistoryUseCurrentSchema() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        long wordId = searchTestWords(accountId, book).items().getFirst().wordId();
        insertQuestion(wordId, "C1_READING", 1, "保存された問題", "正解", "ACTIVE");
        insertQuestion(wordId, "C2_KANJI", 1, "音声から選ぶ", "正解", "ACTIVE");
        sql.execute("INSERT INTO public.\"JPN_単語問題選択肢情報\" (\"問題ID\", \"表示順\", \"選択肢値\", \"正解フラグ\") "
                + "SELECT \"問題ID\", n, CASE WHEN n = 1 THEN '正解' ELSE '誤答' || n END, n = 1 "
                + "FROM public.\"JPN_単語問題情報\" CROSS JOIN generate_series(1, 5) n WHERE \"単語ID\" = " + wordId);
        var created = japaneseService.createTest(user, new JapaneseModels.TestCreateRequest("C", null, book, "Unit001", "Unit001", null, "ALL", 0));
        assertThat(created.questions()).hasSize(1);
        assertThat(created.questions().getFirst().choices()).hasSize(4);
        assertThat(created.test().state()).isEqualTo("CREATED");
        assertThat(management.conditions(book).get("categories")).containsExactly("Unit001");
        assertThat(management.search(accountId, "CREATED", "C", book, "Unit001", "Unit001", 1, 15).totalElements()).isEqualTo(1);
        assertThat(management.search(accountId, "CREATED", "C", "別の教材", null, null, 1, 15).totalElements()).isZero();
        assertThat(testMapper.pickQuestions("C", null, book, "Unit002", "Unit003", null, false, accountId, 100)).isEmpty();
        assertThat(japaneseService.startTest(accountId, created.test().testId()).test().state()).isEqualTo("RUNNING");
        sql.execute("UPDATE public.\"JPN_単語問題情報\" SET \"問題文_日本語\" = '変更後', \"正解値\" = '変更後' WHERE \"単語ID\" = " + wordId);
        var reopened = japaneseService.testDetail(accountId, created.test().testId());
        assertThat(reopened.questions().getFirst().question().questionText()).isEqualTo(created.questions().getFirst().question().questionText());
        var correct = reopened.questions().getFirst().choices().stream().filter(JapaneseModels.ChoiceRow::correct).findFirst().orElseThrow();
        assertThat(japaneseService.answer(user, created.test().testId(), new JapaneseModels.AnswerRequest(1, correct.choiceKey(), null, 1500L)).correct()).isTrue();
        assertThat(japaneseService.testDetail(accountId, created.test().testId()).questions().getFirst().history()).hasSize(1);
        assertThatThrownBy(() -> japaneseService.answer(user, created.test().testId(), new JapaneseModels.AnswerRequest(1, correct.choiceKey(), null, 1500L)))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class);
    }

    @Test
    void inputRetriesPersistAndSkillsAreJudgedIndependently() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String book = registerTestWords(user);
        var word = searchTestWords(accountId, book).items().getFirst();
        insertAiDetail(word.wordId(), "名詞", "検証の中国語意味");
        var created = japaneseService.createTest(user, new JapaneseModels.TestCreateRequest("B", null, book, null, null, null, "ALL", 1));
        for (int attempt = 1; attempt <= 3; attempt++) {
            var result = japaneseService.answer(user, created.test().testId(), new JapaneseModels.AnswerRequest(1, null, word.word(), 1000L, "誤った読み"));
            assertThat(result.answered()).isEqualTo(attempt == 3);
            var reopened = japaneseService.testDetail(accountId, created.test().testId());
            assertThat(reopened.questions().getFirst().history()).hasSize(attempt);
            assertThat(reopened.test().doneCount()).isEqualTo(attempt == 3 ? 1 : 0);
        }
        var skills = japaneseService.skills(accountId, "B", null, 1, 20).items();
        assertThat(skills).filteredOn(x -> x.skillCode().equals("B_ORTHOGRAPHY")).singleElement().satisfies(x -> assertThat(x.correctCount()).isEqualTo(1));
        assertThat(skills).filteredOn(x -> x.skillCode().equals("B_READING_RECALL")).singleElement().satisfies(x -> assertThat(x.wrongCount()).isEqualTo(1));
        assertThat(japaneseService.status(accountId, null, null, 1, 20).summary().todayActiveMs()).isEqualTo(3000L);
    }
}
