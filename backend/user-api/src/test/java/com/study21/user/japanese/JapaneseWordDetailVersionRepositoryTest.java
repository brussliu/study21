package com.study21.user.japanese;

import com.study21.common.core.japanese.JpnWordDetailAssembler;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import com.study21.user.testing.TestSqlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 詳細の版の書き込み・一覧・切り替えを<b>真 DB</b>で確かめる（切片4）。
 *
 * <p>MyBatis の層と SQL でしか分からないことを見る（モックのテストでは通っても動かない部分）:</p>
 * <ol>
 *   <li>{@code insertDetailVersion} の {@code useGeneratedKeys} が採番した {@code 詳細ID} を返すか</li>
 *   <li>段落の行の INSERT（MyBatis の {@code <foreach>} のまとめ入れ）が本当に動くか。
 *       JSONB のキャスト・{@code 表示順}・{@code 登録元コード} が入るか</li>
 *   <li>編集の保存で新しい版ができ、古い版が {@code ARCHIVED} になるか
 *       （部分 UNIQUE 索引 {@code uq_jpn_detail_active} に当たらないか）</li>
 *   <li>「変わっていない AI の行は BATCH のまま」「直した行は APP」が DB に入るか</li>
 *   <li>版の一覧（新しい順・段落の行数）と、{@code バージョン} での切り替えが動くか</li>
 * </ol>
 *
 * <p>AI の版は {@code JapaneseWordAiDetailWriter}（admin-api）と同じ形のデータを、このモジュールの
 * {@link JpnWordDetailMapper} で直接入れて作る（user-api から admin-api は参照できないため。
 * AI 側の書き込みの順番と出所の規則は admin-api のテストが見ている）。</p>
 *
 * <p>{@code @Transactional} なのでテストの最後に必ず ROLLBACK する（真 DB を汚さない）。
 * パスワード（{@code STUDY21_DATASOURCE_PASSWORD}）が無い環境では自動でスキップする。
 * 検証データの作成・確認は {@link TestSqlMapper}（MyBatis 経由＝SQL ログに残る）で行う。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class JapaneseWordDetailVersionRepositoryTest {

    @Autowired
    private JapaneseService service;

    @Autowired
    private JpnWordDetailMapper detailMapper;

    /** テスト専用の SQL（MyBatis 経由なので SQL ログに残る。JdbcTemplate は使わない）。 */
    @Autowired
    private TestSqlMapper sql;

    /** SELECT の 1 列を long で取る（無ければ例外）。 */
    private long queryLong(String statement) {
        List<Map<String, Object>> rows = sql.query(statement);
        if (rows.isEmpty() || rows.get(0).values().iterator().next() == null) {
            throw new IllegalStateException("検証用の SELECT が値を返しませんでした: " + statement);
        }
        return Long.parseLong(String.valueOf(rows.get(0).values().iterator().next()));
    }

    private int queryInt(String statement) {
        return (int) queryLong(statement);
    }

    private static UserPrincipal student() {
        return new UserPrincipal(2L, "ricky.jingze@gmail.com", "試験 生徒", AccountType.STUDENT);
    }

    /** テストごとに違う語を作る（同じ見出し語・読みは uq_jpn_word_key で作れない）。 */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    /** 作った語（編集の要求には 見出し語 と 読み が要る）。 */
    private record CreatedWord(long wordId, String word, String reading) {
    }

    /** 語を 1 つ作る（このテストの中だけ。ROLLBACK で消える）。 */
    private CreatedWord createWord() {
        int seq = SEQ.incrementAndGet();
        String word = "版の編集確認" + seq;
        String reading = "はんのへんしゅうかくにん" + seq;
        long wordId = queryLong(
                "INSERT INTO public.\"JPN_単語情報\" (\"見出し語\", \"読み\", \"見出し語キー\", \"読みキー\","
                        + " \"JLPTレベル\", \"品詞\", \"状態コード\", \"登録元コード\")"
                        + " VALUES ('" + word + "', '" + reading + "', '" + word + "', '" + reading
                        + "', 'N3', '名詞', 'ACTIVE', 'APP')"
                        + " RETURNING \"単語ID\"");
        return new CreatedWord(wordId, word, reading);
    }

    private static JpnWordDetailChildren.Example batchExample(int orderNo, String japanese, String chinese) {
        JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
        row.setOrderNo(orderNo);
        row.setJapanese(japanese);
        row.setChinese(chinese);
        row.setSourceCode("BATCH");
        row.setManualCorrected(false);
        return row;
    }

    /**
     * AI の版を 1 つ入れる（1 語 1 版の ACTIVE を守るため、先に古い版を ARCHIVED にする）。
     *
     * <p>出所はすべて {@code BATCH}（AI が作った行）。人が直した行が混ざる場合の規則は
     * 単体テスト（{@code JapaneseServiceImplTest}）で確かめている。</p>
     */
    private long createAiVersion(long wordId, String coreMeaning, Long generationId) {
        JpnWordDetailEntity origin = detailMapper.findActiveDetail(wordId);
        if (origin != null) {
            detailMapper.archiveActiveDetail(wordId, 2L);
        }
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        header.setWordId(wordId);
        header.setStateCode("ACTIVE");
        header.setOriginDetailId(origin == null ? null : origin.getDetailId());
        header.setGenerationId(generationId);
        header.setAiProvider("qwen");
        header.setAiModel("qwen3.7-plus");
        header.setFetchedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        header.setManualCorrected(false);
        header.setCoreMeaning(coreMeaning);
        header.setDescriptionJa("AI の説明。");
        header.setDescriptionZh("AI 的说明。");
        header.setPartOfSpeech("名詞");
        header.setJlptLevel("N3");
        header.setConjugation("なし");
        header.setTransitivity("NONE");
        header.setImportance(3);
        header.setMemoryHint("ヒント");
        header.setMemoryHintBasis("根拠");
        header.setPronunciationJson("{\"reading\":\"はん\",\"accentType\":1,\"hasAudioSample\":false}");
        header.setConjugationsJson("[]");
        header.setStructuredJson("{}");
        detailMapper.insertDetailVersion(header);
        long detailId = header.getDetailId();

        JpnWordDetailChildren.Sense sense = new JpnWordDetailChildren.Sense();
        sense.setOrderNo(1);
        sense.setSenseNumber(1);
        sense.setJapanese("AI の語義。");
        sense.setChinese("AI 的语义。");
        sense.setSourceCode("BATCH");
        sense.setManualCorrected(false);
        detailMapper.insertSenses(detailId, List.of(sense));

        detailMapper.insertExamples(detailId, List.of(
                batchExample(1, "AI の例文1。", "AI 例句1。"), batchExample(2, "AI の例文2。", "AI 例句2。")));

        JpnWordDetailChildren.Pattern pattern = new JpnWordDetailChildren.Pattern();
        pattern.setOrderNo(1);
        pattern.setPattern("AI の型");
        pattern.setChinese("AI 的句型");
        pattern.setSourceCode("BATCH");
        pattern.setManualCorrected(false);
        detailMapper.insertPatterns(detailId, List.of(pattern));

        JpnWordDetailChildren.Dialog dialog = new JpnWordDetailChildren.Dialog();
        dialog.setOrderNo(1);
        dialog.setScene("店");
        dialog.setSourceCode("BATCH");
        dialog.setManualCorrected(false);
        detailMapper.insertDialogs(detailId, List.of(dialog));
        // 発言は採番された 会話ID が要る（本番は JpnWordDetailMapper.listDialogs で引き直している）
        JpnWordDetailChildren.DialogLine line = new JpnWordDetailChildren.DialogLine();
        line.setDialogId(detailMapper.listDialogs(detailId).get(0).getDialogId());
        line.setOrderNo(1);
        line.setSpeaker("A");
        line.setJapanese("いらっしゃい。");
        line.setChinese("欢迎。");
        line.setSourceCode("BATCH");
        line.setManualCorrected(false);
        detailMapper.insertDialogLines(detailId, List.of(line));
        return detailId;
    }

    /** 画面が読み込む detail（組み立て）。 */
    private Map<String, Object> activeDetailMap(long wordId) {
        JpnWordDetailEntity header = detailMapper.findActiveDetail(wordId);
        return new LinkedHashMap<>(JpnWordDetailAssembler.assemble(header, activeRows(header)));
    }

    private JpnWordDetailChildren.Rows activeRows(JpnWordDetailEntity header) {
        return new JpnWordDetailChildren.Rows(
                detailMapper.listSenses(header.getDetailId()),
                detailMapper.listExamples(header.getDetailId()),
                detailMapper.listPatterns(header.getDetailId()),
                detailMapper.listDialogs(header.getDetailId()),
                detailMapper.listSynonyms(header.getDetailId()),
                detailMapper.listCautions(header.getDetailId()),
                detailMapper.listCollocations(header.getDetailId()),
                detailMapper.listRelatedWords(header.getDetailId()),
                detailMapper.listUsageNotes(header.getDetailId()),
                detailMapper.listPractices(header.getDetailId()));
    }

    private int countActive(long wordId) {
        return queryInt("SELECT count(*) AS \"件数\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"単語ID\" = " + wordId + " AND \"状態コード\" = 'ACTIVE'");
    }

    private int countArchived(long wordId) {
        return queryInt("SELECT count(*) AS \"件数\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"単語ID\" = " + wordId + " AND \"状態コード\" = 'ARCHIVED'");
    }

    private int versionOf(long detailId) {
        return queryInt("SELECT \"バージョン\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"詳細ID\" = " + detailId);
    }

    private static Map<String, Object> row(String japanese, String chinese) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("japanese", japanese);
        item.put("chinese", chinese);
        return item;
    }

    /** 画面から送られてくる detail（語レベルの値 ＋ 段落）。 */
    private Map<String, Object> editorDetail(String descriptionJa, List<Map<String, Object>> examples) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("descriptionJa", descriptionJa);
        detail.put("descriptionZh", "AI 的说明。");
        detail.put("partOfSpeech", "名詞");
        detail.put("jlptLevel", "N3");
        detail.put("conjugation", "なし");
        detail.put("transitivity", "NONE");
        detail.put("importance", 3);
        detail.put("pronunciation", Map.of("reading", "はん", "accentType", 1, "hasAudioSample", false));
        detail.put("senses", List.of(row("AI の語義。", "AI 的语义。")));
        detail.put("examples", examples);
        // 文型は送らない＝画面で消した（要求が版の内容そのもの）
        return detail;
    }

    private JapaneseModels.WordEditorRequest editorRequest(CreatedWord target, int contentVersion,
                                                           Map<String, Object> detail) {
        return new JapaneseModels.WordEditorRequest(
                new JapaneseModels.WordSaveRequest(target.word(), target.reading(), "N3", "名詞", "ACTIVE", null, 1),
                contentVersion, detail);
    }

    @Test
    @DisplayName("AI 版 → 編集で人工版 → AI 版に戻す → 人工版に戻す。どの時点でも ACTIVE は 1 版だけ")
    void createsEditsSwitchesAndKeepsOneActiveVersion() {
        CreatedWord target = createWord();
        long wordId = target.wordId();

        // 1) AI の取得（batC41）で最初の版
        long aiDetailId = createAiVersion(wordId, "AI の核心", 8801L);
        assertThat(countActive(wordId)).isEqualTo(1);
        assertThat(activeDetailMap(wordId)).containsEntry("coreMeaning", "AI の核心");

        // 2) 画面の編集: 例文1 はそのまま、例文2 は直して、文型は消す
        List<Map<String, Object>> editedExamples = new ArrayList<>();
        editedExamples.add(row("AI の例文1。", "AI 例句1。"));
        editedExamples.add(row("AI の例文2。", "人が直した例句2。")); // 中国語だけ直した
        JapaneseModels.WordDetailResult saved = service.saveWordEditor(student(), wordId,
                editorRequest(target, 1, editorDetail("人が直した説明。", editedExamples)));

        // 新しい版が有効・古い版は履歴（消えていない）
        assertThat(countActive(wordId)).isEqualTo(1);
        assertThat(countArchived(wordId)).isEqualTo(1);
        JpnWordDetailEntity manual = detailMapper.findActiveDetail(wordId);
        assertThat(manual.getDetailId()).isNotEqualTo(aiDetailId);
        assertThat(manual.getContentVersion()).isEqualTo(2);
        assertThat(manual.getOriginDetailId()).isEqualTo(aiDetailId);
        assertThat(manual.getGenerationId()).isNull();
        assertThat(manual.getManualCorrected()).isTrue();
        // AI の版から引き継ぐ（どの AI の応答から生まれた版か）と、要求の値で置き換わる語レベルの値
        assertThat(manual.getAiProvider()).isEqualTo("qwen");
        assertThat(manual.getDescriptionJa()).isEqualTo("人が直した説明。");
        assertThat(saved.detail().detail()).containsEntry("coreMeaning", "AI の核心");
        assertThat(saved.detail().detail()).containsEntry("descriptionJa", "人が直した説明。");
        assertThat(saved.detail().detail()).containsEntry("manuallyCorrected", true);

        // 例文: 1 行目は AI のまま（BATCH）・2 行目は人（APP）。文型は送らなかったので消えている
        List<JpnWordDetailChildren.Example> examples = detailMapper.listExamples(manual.getDetailId());
        assertThat(examples).hasSize(2);
        assertThat(examples.get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(examples.get(0).getManualCorrected()).isFalse();
        assertThat(examples.get(1).getSourceCode()).isEqualTo("APP");
        assertThat(examples.get(1).getManualCorrected()).isTrue();
        assertThat(examples.get(1).getChinese()).isEqualTo("人が直した例句2。");
        assertThat(examples).extracting(JpnWordDetailChildren.Example::getOrderNo).containsExactly(1, 2);
        assertThat(detailMapper.listPatterns(manual.getDetailId())).isEmpty();
        assertThat(detailMapper.listDialogs(manual.getDetailId())).isEmpty();
        // 語義は内容が同じなので AI の出所を引き継ぐ（次の AI 取得で普段どおり置き換わる）
        assertThat(detailMapper.listSenses(manual.getDetailId()).get(0).getSourceCode()).isEqualTo("BATCH");

        // 3) 版の一覧（新しい順・段落の行数つき）
        JapaneseModels.WordDetailVersions versions = service.detailVersions(2L, wordId);
        assertThat(versions.items()).hasSize(2);
        assertThat(versions.items().get(0).contentVersion()).isEqualTo(2);
        assertThat(versions.items().get(0).active()).isTrue();
        assertThat(versions.items().get(0).manual()).isTrue();
        assertThat(versions.items().get(0).counts().examples()).isEqualTo(2);
        assertThat(versions.items().get(0).counts().patterns()).isZero();
        assertThat(versions.items().get(1).contentVersion()).isEqualTo(1);
        assertThat(versions.items().get(1).active()).isFalse();
        assertThat(versions.items().get(1).manual()).isFalse();
        assertThat(versions.items().get(1).counts().patterns()).isEqualTo(1);
        // 版の一覧は版ごとの バージョン（楽観ロック）を返す。画面はこれをそのまま切り替えに送る
        assertThat(versions.items().get(0).version()).isEqualTo(versionOf(manual.getDetailId()));
        assertThat(versions.items().get(1).version()).isEqualTo(versionOf(aiDetailId));

        // 4) AI の版へ戻す（内容も AI のものに戻る）
        JapaneseModels.WordDetailResult backToAi = service.activateDetailVersion(student(), wordId,
                aiDetailId, new JapaneseModels.ActivateVersionRequest(versionOf(aiDetailId)));

        assertThat(countActive(wordId)).isEqualTo(1);
        assertThat(countArchived(wordId)).isEqualTo(1);
        assertThat(backToAi.detail().detailId()).isEqualTo(aiDetailId);
        // 詳細にもその版の バージョン が入る（画面が控えて切り替えに使う）
        assertThat(backToAi.detail().version()).isEqualTo(versionOf(aiDetailId));
        assertThat(backToAi.detail().detail()).containsEntry("descriptionJa", "AI の説明。");
        assertThat(backToAi.detail().detail()).containsEntry("manuallyCorrected", false);
        assertThat(detailMapper.listPatterns(aiDetailId)).hasSize(1);

        // 5) もう一度 人工版へ戻す（編集の内容がそのまま残っている）
        JapaneseModels.WordDetailResult backToManual = service.activateDetailVersion(student(), wordId,
                manual.getDetailId(), new JapaneseModels.ActivateVersionRequest(versionOf(manual.getDetailId())));

        assertThat(countActive(wordId)).isEqualTo(1);
        assertThat(backToManual.detail().detailId()).isEqualTo(manual.getDetailId());
        assertThat(backToManual.detail().detail()).containsEntry("descriptionJa", "人が直した説明。");
        List<JpnWordDetailChildren.Example> kept = detailMapper.listExamples(manual.getDetailId());
        assertThat(kept).hasSize(2);
        assertThat(kept.get(1).getSourceCode()).isEqualTo("APP");
    }

    @Test
    @DisplayName("版数が古い編集は 409（新しい版は作られない）")
    void rejectsStaleEditorSaveOnRealDatabase() {
        CreatedWord target = createWord();
        long wordId = target.wordId();
        createAiVersion(wordId, "AI の核心", 8802L);

        // 画面が読んだのは版数 1 のつもりだが、もう版が進んでいる（ここでは 5 を渡す）
        assertThatThrownBy(() -> service.saveWordEditor(student(), wordId,
                editorRequest(target, 5, editorDetail("だめな保存。", List.of()))))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class);
        assertThat(countActive(wordId)).isEqualTo(1);
        assertThat(countArchived(wordId)).isZero();
    }

    @Test
    @DisplayName("有効版の切り替えは バージョン が古ければ 409（その語の版でなければ 404）")
    void rejectsStaleAndForeignVersionOnRealDatabase() {
        CreatedWord target = createWord();
        long wordId = target.wordId();
        long aiDetailId = createAiVersion(wordId, "AI の核心", 8803L);
        JapaneseModels.WordDetailResult saved = service.saveWordEditor(student(), wordId,
                editorRequest(target, 1, editorDetail("人が直した説明。", List.of(row("AI の例文1。", "AI 例句1。")))));
        long manualDetailId = saved.detail().detailId();

        // 別の語の版を指定したら 404（版自体は存在するが、その語のものではない）
        CreatedWord other = createWord();
        long otherDetailId = createAiVersion(other.wordId(), "別の語", 8804L);
        assertThatThrownBy(() -> service.activateDetailVersion(student(), wordId, otherDetailId,
                new JapaneseModels.ActivateVersionRequest(1)))
                .isInstanceOf(com.study21.common.core.exception.NotFoundException.class);

        // 古い バージョン で切り替えようとしたら 409（他の操作が先に更新した）
        assertThatThrownBy(() -> service.activateDetailVersion(student(), wordId, aiDetailId,
                new JapaneseModels.ActivateVersionRequest(99)))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class);
        // 失敗しても状態は変わらない（元の有効版のまま。楽観的ロックを先に見てから切り替える）
        assertThat(detailMapper.findActiveDetail(wordId).getDetailId()).isEqualTo(manualDetailId);
    }
}
