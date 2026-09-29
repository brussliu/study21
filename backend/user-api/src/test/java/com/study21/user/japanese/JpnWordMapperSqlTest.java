package com.study21.user.japanese;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 単語の読みが「未取得（NULL）」でも登録・検索できることの SQL 契約。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>登録・修正は {@code 読み} を NULL のまま入れられる（{@code jdbcType=VARCHAR} を明示。
 *       付けないと型が決まらず、NULL のときにドライバが失敗することがある）</li>
 *   <li>{@code 読みキー} は NOT NULL なので、読みが無いときは空文字を入れる（SQL はそのまま受ける）</li>
 *   <li>重複の判定は {@code IS NOT DISTINCT FROM}（{@code =} だと NULL 同士が一致せず、
 *       読みなしの重複を見逃す）</li>
 * </ol>
 */
class JpnWordMapperSqlTest {

    private static final String NAMESPACE = "com.study21.user.japanese.JpnWordMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JpnWordMapperSqlTest.class
                .getResourceAsStream("/mapper/JpnWordMapper.xml")) {
            assertThat(input).as("JpnWordMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JpnWordMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = JpnWordMapperSqlTest.class
                .getResourceAsStream("/mapper/JpnWordMapper.xml")) {
            assertThat(stream).as("JpnWordMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 指定した id の要素の中身（開始タグの次から終了タグまで）。 */
    private static String statement(String tag, String id) {
        String start = "<" + tag + " id=\"" + id + "\"";
        int from = XML.indexOf(start);
        assertThat(from).as("%s id=%s", tag, id).isNotNegative();
        int to = XML.indexOf("</" + tag + ">", from);
        return XML.substring(from, to);
    }

    /**
     * **バインド後**の SQL（MyBatis に組ませる）。
     *
     * <p>XML の文字列一致では「{@code <where>} が WHERE を出すか」が分からない。実際に
     * 「絞り込みが空だと条件が JOIN の ON に付いて効かない」バグを文字列一致のテストが
     * 見逃した（2026-09-27）ので、構造はここで確かめる。</p>
     */
    private static String boundSql(String id, java.util.Map<String, Object> params) {
        java.util.Map<String, Object> all = new java.util.HashMap<>();
        // findAiTargets が参照する引数（絞り込みは空＝null にする）
        all.put("keyword", null);
        all.put("reading", null);
        all.put("jlpt", null);
        all.put("part", null);
        all.put("state", null);
        all.put("book", null);
        all.put("categoryFrom", null);
        all.put("categoryTo", null);
        all.put("learnState", null);
        all.put("accountId", 2L);
        all.put("kind", "DETAIL");
        all.put("skipAcquired", true);
        all.put("limit", 20);
        all.putAll(params);
        return configuration.getMappedStatement(NAMESPACE + id).getBoundSql(all).getSql()
                .replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("登録は 読み を NULL で入れられる（jdbcType を明示する）")
    void allowsNullReadingOnInsert() {
        String sql = statement("insert", "insert");

        assertThat(sql).contains("#{reading,jdbcType=VARCHAR}");
        assertThat(sql).contains("#{jlptLevel,jdbcType=VARCHAR}");
        // 読みキーは NOT NULL なので、空文字を受ける（NULL を許す形にしない）
        assertThat(sql).contains("#{readingKey}");
        assertThat(sql).doesNotContain("#{readingKey,jdbcType");
    }

    @Test
    @DisplayName("修正でも 読み を NULL に戻せる")
    void allowsNullReadingOnUpdate() {
        assertThat(statement("update", "update")).contains("\"読み\"             = #{reading,jdbcType=VARCHAR}");
    }

    @Test
    @DisplayName("重複の判定は NULL 同士でも一致する（IS NOT DISTINCT FROM）")
    void duplicateCheckTreatsNullsAsEqual() {
        // バインド後の SQL はパラメータが ? になるので、本文（XML）で確かめる
        assertThat(statement("select", "findByWordAndReading"))
                .contains("\"読み\" IS NOT DISTINCT FROM #{reading}");
        assertThat(statement("select", "findByWordAndReading")).doesNotContain("\"読み\" = #{reading}");
    }

    @Test
    @DisplayName("AI 取得の対象は、一覧と同じ絞り込み・同じ並びで ID だけを選ぶ（ページは関係しない）")
    void selectsAiTargetsByTheSameFilters() {
        String sql = statement("select", "findAiTargets");

        // 絞り込みは一覧と同じ断片を使う（独自の WHERE を書かない＝画面と食い違わない）
        assertThat(sql).contains("<include refid=\"WordJoins\"/>");
        assertThat(sql).contains("<include refid=\"WordWhere\"/>");
        // 並びも一覧と同じ（表示順の先頭から N 語）
        assertThat(sql).contains("ORDER BY col.\"書籍\" ASC NULLS LAST");
        assertThat(sql).contains("LIMIT #{limit}");
        // ID だけ（詳細・状態の列は取らない）
        assertThat(sql).contains("SELECT w.\"単語ID\"");
        assertThat(sql).doesNotContain("OFFSET");
    }

    @Test
    @DisplayName("「取得済みをスキップ」の条件は WHERE の中に入る（絞り込みが空でも効く）")
    void skipConditionsStayInsideWhere() {
        // 絞り込みを 1 つも渡さない（画面で条件を入れずに【AI 取得】を押した場合）
        String sql = boundSql("findAiTargets", java.util.Map.of());

        // <where> が WHERE を出し、条件がその後ろに来る。
        // 素の AND を <where> の外に書くと、WHERE が出ずに直前の LEFT JOIN LATERAL ... ON TRUE へ
        // 付いてしまい、LEFT JOIN では行が消えないので**条件が効かない**（実際に踏んだ）
        assertThat(sql).contains("WHERE");
        assertThat(sql.indexOf("IS DISTINCT FROM 'SUCCEEDED'")).isGreaterThan(sql.indexOf("WHERE"));
        assertThat(sql).doesNotContain("ON TRUE AND");
        // 上限はプレースホルダ（値はバインドされる）
        assertThat(sql).contains("LIMIT ?");
    }

    @Test
    @DisplayName("絞り込みが空で「すべて再取得」のときも、素の AND を出さない（正しい SQL になる）")
    void allTargetsWithoutFiltersHasNoBareAnd() {
        String sql = boundSql("findAiTargets", java.util.Map.of("skipAcquired", false));

        assertThat(sql).doesNotContain("IS DISTINCT FROM");
        assertThat(sql).doesNotContain("ON TRUE AND");
        assertThat(sql).contains("ORDER BY col.\"書籍\"");
    }

    @Test
    @DisplayName("C の区画は C1 と C2 をまとめる（一覧の orElseState と同じ規則）")
    void mergesReadingAndKanjiForKindC() {
        String area = statement("sql", "AiAreaState");

        // どちらか成功なら SUCCEEDED、でなければ値のあるほう（NULL の扱いまで同じにする）
        assertThat(area).contains("WHEN ai.\"読問題AI状態\" = 'SUCCEEDED' OR ai.\"漢字読問題AI状態\" = 'SUCCEEDED' THEN 'SUCCEEDED'");
        assertThat(area).contains("WHEN ai.\"読問題AI状態\" IS NOT NULL THEN ai.\"読問題AI状態\"");
        assertThat(area).contains("ELSE ai.\"漢字読問題AI状態\" END");
        // 区分ごとの出し分け（A・B は詳細、D は文脈、E は漢字）
        assertThat(area).contains("ai.\"詳細AI状態\"");
        assertThat(area).contains("ai.\"文脈問題AI状態\"");
        assertThat(area).contains("ai.\"漢字問題AI状態\"");
    }

    @Test
    @DisplayName("件数は一致総数・取得済み・今回取得できる数（一覧と同じビューを見る）")
    void countsAiTargets() {
        String sql = statement("select", "countAiTargets");

        assertThat(sql).contains("count(*) AS \"total\"");
        assertThat(sql).contains("AS \"acquired\"");
        assertThat(sql).contains("AS \"candidates\"");
        // 一覧と同じ絞り込み（＝画面の「取得状態」と食い違わない）
        assertThat(sql).contains("<include refid=\"WordJoins\"/>");
        assertThat(sql).contains("<include refid=\"WordWhere\"/>");
    }

    @Test
    @DisplayName("読みがまだ無い語も、キーワード検索の対象から外れない")
    void searchDoesNotRequireReading() {
        // 検索条件は「読み が空なら条件に加えない」形（読みなしの語が消えない）
        assertThat(XML).contains("<if test=\"reading != null and reading != ''\">");
    }

    @Test
    @DisplayName("一覧の並びは 書籍 → 分類 → 単語SEQ → 単語ID（教材の並びで読める）")
    void ordersByTextbookPosition() {
        String order = statement("select", "search");

        // 見出し語の五十音順ではない
        assertThat(order).doesNotContain("ORDER BY w.\"見出し語キー\"");
        assertThat(order).contains("ORDER BY col.\"書籍\" ASC NULLS LAST");
        assertThat(order).contains("col.\"分類\" ASC NULLS LAST");
        assertThat(order).contains("COALESCE(col.\"単語SEQ\", 0) ASC");
        assertThat(order).contains("w.\"単語ID\" ASC");
        // 収録が無い語（書籍・分類が NULL）は後ろにまとめる
        assertThat(order).contains("NULLS LAST");
    }

    @Test
    @DisplayName("一覧の「取得状態」は、AI 生成履歴の最新ビューを 4 区画にまとめて返す")
    void selectsAiStateForList() {
        // 列と JOIN は共通の断片（WordColumns / WordJoins）にある。検索はそれを include する
        String columns = statement("sql", "WordColumns");
        String joins = statement("sql", "WordJoins");
        String select = statement("select", "search");

        assertThat(select).contains("<include refid=\"WordColumns\"/>");
        assertThat(select).contains("<include refid=\"WordJoins\"/>");

        // 履歴は 1 語 1 内容種別に何行もできるので、最新を出すビューを使う
        assertThat(joins).contains("public.\"v_jpn_word_ai_state\"");
        for (String column : new String[]{"詳細AI状態", "読問題AI状態", "漢字読問題AI状態",
                "文脈問題AI状態", "漢字問題AI状態"}) {
            assertThat(columns).as("取得状態の列 %s", column).contains("\"" + column + "\"");
        }
        // 一度でも成功していれば「取得済」に見せる（内容は DB に残っている）
        assertThat(joins).contains("WHEN a.\"成功あり\" THEN 'SUCCEEDED'");
        // 内容種別ごとに 1 つにまとめる
        assertThat(joins).contains("FILTER (WHERE a.\"内容種別コード\" = 'A_DETAIL')");
        assertThat(joins).contains("FILTER (WHERE a.\"内容種別コード\" = 'E_KANJI_USAGE')");
    }

    @Test
    @DisplayName("一覧の「品詞」「中国語訳」は有効版の詳細から補う（人が入れた品詞が優先）")
    void fillsPartOfSpeechAndChineseFromTheActiveDetail() {
        String columns = statement("sql", "WordColumns");
        String joins = statement("sql", "WordJoins");
        String where = statement("sql", "WordWhere");

        // 品詞: 単語情報（人が直した値）→ 無ければ AI 詳細（有効版）の品詞
        assertThat(columns).contains("COALESCE(NULLIF(BTRIM(w.\"品詞\"), ''), d.\"品詞\") AS \"品詞\"");
        // 中国語訳: 有効版の最初の語義の中国語（語義が無ければ詳細の説明_中国語）
        assertThat(columns).contains("AS \"中国語訳\"");
        assertThat(joins).contains("public.\"JPN_単語詳細情報\"");
        assertThat(joins).contains("dv.\"状態コード\" = 'ACTIVE'");
        assertThat(joins).contains("public.\"JPN_単語詳細_語義情報\"");
        assertThat(joins).contains("ORDER BY sn.\"表示順\"");
        assertThat(joins).contains("LIMIT 1");
        // 絞り込みも同じ値を見る（AI が入れた品詞でも絞り込める。単語情報が空でも消えない）
        assertThat(where).contains("COALESCE(NULLIF(BTRIM(w.\"品詞\"), ''), d.\"品詞\") ILIKE");
    }

    @Test
    @DisplayName("分類は From と To の範囲で絞り込める（片方だけでも効く）")
    void filtersByCategoryRange() {
        String where = statement("sql", "WordWhere");

        // 同じ収録の中で範囲を見る（From と To を別々の EXISTS にすると、
        // 「別の Unit で From を満たし、さらに別の Unit で To を満たす語」まで拾ってしまう）
        assertThat(where).contains("public.\"JPN_単語収録情報\" cr");
        assertThat(where).contains("cr.\"分類\" &gt;= #{categoryFrom}");
        assertThat(where).contains("cr.\"分類\" &lt;= #{categoryTo}");
        // どちらか片方だけのときも同じ断片で受ける（無い条件は足さない）
        assertThat(where).contains("<if test=\"categoryFrom != null and categoryFrom != ''\">");
        assertThat(where).contains("<if test=\"categoryTo != null and categoryTo != ''\">");
    }

    @Test
    @DisplayName("問題（C/D/E）の版の一覧は、AI の取得履歴を正として新しい順に返す")
    void listsQuestionVersions() {
        String sql = statement("select", "listQuestionVersions");

        // 1 行 = AI の取得 1 回（生成履歴）。2.0 の「詳細情報取得履歴」と同じ考え
        assertThat(sql).contains("public.\"JPN_AI生成履歴情報\"");
        assertThat(sql).contains("g.\"内容版数\"");
        // 対象は C/D/E の 4 種別だけ（A_DETAIL は詳細の版で見る）
        assertThat(sql).contains("'C1_READING', 'C2_KANJI', 'D_CONTEXT_MEANING', 'E_KANJI_USAGE'");
        assertThat(sql).doesNotContain("'A_DETAIL'");
        // その版で書かれた問題の数と、今使われているかを返す（0 件の版は切り替えられない）
        assertThat(sql).contains("AS \"questionCount\"");
        assertThat(sql).contains("bool_or(q.\"状態コード\" = 'ACTIVE')");
        // 新しい版が先
        assertThat(sql).contains("ORDER BY g.\"内容種別コード\", g.\"内容版数\" DESC");
    }

    @Test
    @DisplayName("問題の版の切り替えは、指定した版だけ ACTIVE にする（問題は消さない）")
    void activatesQuestionVersion() {
        String sql = statement("update", "activateQuestionVersion");

        assertThat(sql).contains("UPDATE public.\"JPN_単語問題情報\"");
        // その種別の中で、指定した版だけ ACTIVE・ほかは ARCHIVED
        assertThat(sql).contains("CASE WHEN \"内容版数\" = #{contentVersion} THEN 'ACTIVE' ELSE 'ARCHIVED' END");
        assertThat(sql).contains("\"問題種別\" = #{questionType}");
        assertThat(sql).contains("\"単語ID\" = #{wordId}");
        // 問題は消さない（テストの出題が参照している）
        assertThat(sql).doesNotContain("DELETE");
    }

    @Test
    @DisplayName("同じ見出し語で別の読みを持つ語を引く（画面の「別の読みもあります」）")
    void findsAlternateReading() {
        String sql = statement("select", "findAlternateReading");

        assertThat(sql).contains("other.\"見出し語キー\" = w.\"見出し語キー\"");
        // 自分自身は除く（&lt;&gt; は XML でエスケープ済み）
        assertThat(sql).contains("other.\"単語ID\" &lt;&gt; w.\"単語ID\"");
        // 読みが未取得の語は出さない
        assertThat(sql).contains("NULLIF(BTRIM(COALESCE(other.\"読み\", '')), '') IS NOT NULL");
        assertThat(sql).contains("LIMIT 1");
    }

    @Test
    @DisplayName("書籍マスタ: 無い書籍名のときだけ作り、コードは数字の続きを振る")
    void managesBookMaster() {
        String find = statement("select", "findBookIdByName");
        assertThat(find).contains("FROM public.\"JPN_書籍情報\"");
        assertThat(find).contains("b.\"書籍名\" = #{book}");
        // 同じ名前が複数あっても 1 件に決める
        assertThat(find).contains("ORDER BY b.\"書籍ID\"");

        // 数字でないコード（移行前の名前など）は無視して、数字の最大 +1 を振る
        assertThat(statement("select", "maxBookCode")).contains("\"書籍コード\" ~ '^[0-9]+$'");

        String insert = statement("insert", "insertBook");
        assertThat(insert).contains("INSERT INTO public.\"JPN_書籍情報\"");
        assertThat(insert).contains("'ACTIVE'");
        assertThat(insert).contains("'APP'");
        assertThat(insert).contains("#{accountId}");
        // 分類数・収録語数は 0 で入れて、収録を入れたあとに数え直す
        assertThat(insert).contains("0, 0,");
    }

    @Test
    @DisplayName("書籍マスタ: 分類数と収録語数は収録から数え直す")
    void refreshesBookCounts() {
        String sql = statement("update", "refreshBookCounts");

        assertThat(sql).contains("UPDATE public.\"JPN_書籍情報\"");
        assertThat(sql).contains("COUNT(DISTINCT col.\"分類\")");
        assertThat(sql).contains("COUNT(*)");
        assertThat(sql).contains("col.\"状態コード\" = 'ACTIVE'");
        assertThat(sql).contains("WHERE b.\"書籍名\" = #{book}");
    }
}
