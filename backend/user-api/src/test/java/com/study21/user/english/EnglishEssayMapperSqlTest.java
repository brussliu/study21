package com.study21.user.english;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 英作文の SQL 契約（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>一覧・詳細は <b>自分の作文＋自分の子どもの作文</b>（{@code 利用者アカウントID}）で、
 *       <b>論理削除（状態コード 'X'）は出さない</b></li>
 *   <li>家族（自分の子ども）は <b>SQL の中で</b> {@code ACC_アカウント.保護者ID} から引く（新しい表は作らない）</li>
 *   <li>一覧は <b>1 クエリ</b>で、最新の添削（回数 DESC LIMIT 1）と画像枚数（FILTER）を LATERAL でまとめる
 *       （1 件ずつ引かない。LATERAL も 2 つのまま）</li>
 *   <li>並びは {@code 登録日時 DESC, 英作文ID DESC}</li>
 *   <li>画像の並べ替えは {@code UNIQUE(英作文ID, 表示順)} に当たらないよう、いったん退避してから確定する</li>
 *   <li>添削履歴は <b>参照のみ</b>（user-api から書き込む文が無いこと）</li>
 * </ol>
 */
class EnglishEssayMapperSqlTest {

    private static final String NAMESPACE = "com.study21.user.english.EnglishEssayMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = EnglishEssayMapperSqlTest.class
                .getResourceAsStream("/mapper/EnglishEssayMapper.xml")) {
            assertThat(input).as("EnglishEssayMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "EnglishEssayMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = EnglishEssayMapperSqlTest.class
                .getResourceAsStream("/mapper/EnglishEssayMapper.xml")) {
            assertThat(stream).as("EnglishEssayMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 指定した id の要素の中身（開始タグの次から**対応する**終了タグまで。入れ子も数える）。 */
    private static String statement(String tag, String id) {
        String start = "<" + tag + " id=\"" + id + "\"";
        int from = XML.indexOf(start);
        assertThat(from).as("%s id=%s", tag, id).isNotNegative();
        int to = matchingClose(tag, from);
        assertThat(to).as("%s id=%s の終了タグ", tag, id).isNotNegative();
        return XML.substring(from, to);
    }

    /**
     * 開始タグと同じ種類の終了タグの位置（**入れ子は飛ばす**）。
     *
     * <p>`EssayScope` は中に `<select>` を持つので、単純に次の `&lt;/sql&gt;` を探すだけでは
     * 手前で切れてしまう（切れた断片を「SQL の契約」として見てしまう）。</p>
     */
    private static int matchingClose(String tag, int cursor) {
        int rootEnd = XML.indexOf('>', cursor);
        if (rootEnd < 0) {
            return -1;
        }
        int at = rootEnd + 1;
        int depth = 0;
        while (at < XML.length()) {
            int nextClose = XML.indexOf("</" + tag + ">", at);
            int nextOpen = openingTagAt(tag, at);
            if (nextClose < 0) {
                return -1;
            }
            if (nextOpen < 0 || nextOpen > nextClose) {
                if (depth == 0) {
                    return nextClose;
                }
                depth--;
                at = nextClose + tag.length() + 3;
                continue;
            }
            int openEnd = XML.indexOf('>', nextOpen);
            if (openEnd < 0) {
                return -1;
            }
            if (!XML.startsWith("/>", openEnd - 1)) {
                depth++;
            }
            at = openEnd + 1;
        }
        return -1;
    }

    /** `cursor` 以降で最初に現れる `&lt;tag ...&gt;` の「`<` の位置」（無ければ -1）。 */
    private static int openingTagAt(String tag, int cursor) {
        String open = "<" + tag;
        int at = XML.indexOf(open, cursor);
        while (at >= 0) {
            if (isTagStart(XML, at + open.length())) {
                return at;
            }
            at = XML.indexOf(open, at + open.length());
        }
        return -1;
    }

    /** タグ名の直後が「名前の続き」でないこと（`<sql` が `<sqlex` に当たらないように）。 */
    private static boolean isTagStart(String xml, int afterName) {
        if (afterName >= xml.length()) {
            return false;
        }
        char next = xml.charAt(afterName);
        return next == '>' || next == ' ' || next == '\n' || next == '\r' || next == '\t';
    }

    /** **バインド後**の SQL（{@code <where>} や {@code <if>} の結果まで見る）。 */
    private static String boundSql(String id, Map<String, Object> params) {
        Map<String, Object> all = new java.util.HashMap<>();
        all.put("accountId", 2L);
        all.put("keyword", null);
        all.put("level", null);
        all.put("dateFrom", null);
        all.put("dateTo", null);
        all.put("limit", 20);
        all.put("offset", 0);
        all.put("essayId", 501L);
        all.put("imageId", 9001L);
        all.put("imageIds", java.util.List.of(9001L));
        all.put("orderNo", 1);
        all.put("offset", 0);
        all.put("recognizedText", "Do you agree?");
        all.put("confidence", 93);
        all.putAll(params);
        return configuration.getMappedStatement(NAMESPACE + id).getBoundSql(all).getSql()
                .replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("一覧・詳細は自分の作文と自分の子どもの作文（利用者アカウントID）で、論理削除は出さない")
    void restrictsToOwnAndChildActiveEssays() {
        String scope = statement("sql", "EssayScope");

        assertThat(scope).contains("e.\"利用者アカウントID\" = #{accountId}");
        assertThat(scope).contains("e.\"状態コード\" = 'A'");
        // 家族（保護者）のスコープ: 同じ SQL の中で ACC_アカウント.保護者ID から子どもを引く
        assertThat(scope).contains("public.\"ACC_アカウント\"");
        assertThat(scope).contains("c.\"保護者ID\" = #{accountId}");
        assertThat(scope).contains("c.\"アカウントID\"");
        assertThat(scope).contains("SELECT");
        // 子どもは生徒だけ（保護者の行を作文の持ち主にしない）
        assertThat(scope).contains("c.\"アカウント種別\" = 'STUDENT'");
        assertThat(scope).contains("c.\"状態\" = '1'");
        // 1 文の中で解決する（問い合わせを増やさない）
        assertThat(scope).doesNotContain("JOIN LATERAL");
    }

    @Test
    @DisplayName("家族の条件は一覧・件数・詳細のすべてに入る（見え方が食い違わない）")
    void appliesTheFamilyScopeToEveryRead() {
        assertThat(statement("select", "count")).contains("<include refid=\"EssayScope\"/>");
        assertThat(statement("select", "search")).contains("<include refid=\"EssayScope\"/>");
        assertThat(statement("select", "findById")).contains("<include refid=\"EssayScope\"/>");
    }

    @Test
    @DisplayName("一覧・詳細は持ち主（誰の作文か）を返す。名前は ACC_アカウント から 1 回だけ引く")
    void returnsTheOwnerOfEachEssay() {
        String search = statement("select", "search");
        assertThat(search).contains("e.\"利用者アカウントID\" AS \"ownerAccountId\"");
        assertThat(search).contains("AS \"ownerName\"");
        assertThat(search).contains("LEFT JOIN public.\"ACC_アカウント\" o");
        // 行ごとに引かない（LATERAL を増やさない）
        assertThat(search.split("LEFT JOIN LATERAL", -1).length - 1).isEqualTo(2);

        String findById = statement("select", "findById");
        assertThat(findById).contains("e.\"利用者アカウントID\" AS \"ownerAccountId\"");
        assertThat(findById).contains("AS \"ownerName\"");
        assertThat(findById).contains("LEFT JOIN public.\"ACC_アカウント\" o");
        assertThat(findById).doesNotContain("LATERAL");
    }

    @Test
    @DisplayName("最新の添削は LATERAL で 1 行（回数 DESC LIMIT 1）")
    void joinsTheLatestGradingWithLateral() {
        String sql = statement("select", "search");

        assertThat(sql).contains("LEFT JOIN LATERAL");
        assertThat(sql).contains("public.\"ENG_AI添削履歴情報\" h");
        assertThat(sql).contains("ORDER BY h.\"回数\" DESC");
        assertThat(sql).contains("LIMIT 1");
        // 回数で最新を決める（登録日時ではない。あとから積んでも回数が正）
        assertThat(sql).contains("h.\"添削ID\", h.\"回数\", h.\"状態コード\", h.\"総合得点\", h.\"満点\"");
    }

    @Test
    @DisplayName("画像枚数は LATERAL の count(*) FILTER でまとめる（1 件ずつ引かない）")
    void countsImagesInOneLateral() {
        String sql = statement("select", "search");

        assertThat(sql).contains("public.\"ENG_英作文画像情報\" i");
        assertThat(sql).contains("count(*) AS \"imageCount\"");
        assertThat(sql).contains("count(*) FILTER (WHERE i.\"画像区分\" = 'question') AS \"questionImageCount\"");
        assertThat(sql).contains("count(*) FILTER (WHERE i.\"画像区分\" = 'answer') AS \"answerImageCount\"");
        // 行ごとに SELECT を打たない（LATERAL は 2 つだけ）
        assertThat(sql.split("LEFT JOIN LATERAL", -1).length - 1).isEqualTo(2);
    }

    @Test
    @DisplayName("一覧の並びは 登録日時 DESC → 英作文ID DESC")
    void ordersNewestFirst() {
        String sql = statement("select", "search");

        assertThat(sql).contains("ORDER BY e.\"登録日時\" DESC, e.\"英作文ID\" DESC");
        assertThat(sql).contains("LIMIT #{limit} OFFSET #{offset}");
    }

    @Test
    @DisplayName("絞り込みは必須条件の後ろに付く（絞り込みが空でも JOIN の ON に食い込まない）")
    void filtersStayAfterTheRequiredConditions() {
        String sql = boundSql("search", Map.of());

        assertThat(sql).contains("WHERE");
        assertThat(sql).contains("e.\"利用者アカウントID\" = ?");
        assertThat(sql).contains("e.\"状態コード\" = 'A'");
        // 家族の条件は **同じ 1 文の中**で展開される（別の SELECT を増やさない）。
        // 条件の全文を固定する: 自分の分 OR 自分の子どもの分（保護者ID で引く）
        assertThat(sql).contains("WHERE (e.\"利用者アカウントID\" = ?"
                + " OR e.\"利用者アカウントID\" = (SELECT c.\"アカウントID\""
                + " FROM public.\"ACC_アカウント\" c"
                + " WHERE c.\"保護者ID\" = ?"
                + " AND c.\"アカウント種別\" = 'STUDENT'"
                + " AND c.\"状態\" = '1'"
                + " ORDER BY c.\"アカウントID\" LIMIT 1))"
                + " AND e.\"状態コード\" = 'A'");
        assertThat(sql).doesNotContain(";");
        // LEFT JOIN LATERAL ... ON TRUE の直後に素の AND が付いていない
        assertThat(sql).doesNotContain("ON TRUE AND");
        // 絞り込みの条件は LATERAL の後ろ（WHERE 句の中）に出す。先頭の持ち主の列ではない
        assertThat(sql.indexOf("WHERE (e.\"利用者アカウントID\""))
                .isGreaterThan(sql.indexOf("LEFT JOIN LATERAL"));
    }

    @Test
    @DisplayName("キーワード・級・期間の絞り込みが SQL になる")
    void bindsFilters() {
        String sql = boundSql("search", Map.of("keyword", "dream", "level", "GRADE1",
                "dateFrom", "2026-01-01", "dateTo", "2026-01-31"));

        assertThat(sql).contains("e.\"題\" ILIKE '%' || ? || '%'");
        assertThat(sql).contains("e.\"英検級\" = ?");
        assertThat(sql).contains("e.\"登録日時\" >= CAST(? AS date)");
        // 「その日まで」は翌日未満で見る（時刻を落とさない）
        assertThat(sql).contains("e.\"登録日時\" < CAST(? AS date) + INTERVAL '1 day'");
    }

    @Test
    @DisplayName("件数は一覧と同じ絞り込みで数える")
    void countsWithTheSameFilters() {
        String sql = statement("select", "count");

        assertThat(sql).contains("SELECT count(*)");
        assertThat(sql).contains("<include refid=\"EssayScope\"/>");
        assertThat(sql).contains("<include refid=\"EssayWhere\"/>");
        assertThat(sql).doesNotContain("LATERAL");
    }

    @Test
    @DisplayName("詳細は本人（または自分の子ども）の有効な行を引く（他人・削除済みは返らない＝404 にできる）")
    void findsOwnEssayById() {
        String sql = statement("select", "findById");

        assertThat(sql).contains("e.\"英作文ID\" = #{essayId}");
        // 自分の作文と、自分の子どもの作文（EssayScope が同じ条件を出す）
        assertThat(sql).contains("<include refid=\"EssayScope\"/>");
        // 論理削除済み（'X'）は返さない＝見えないものは 404 にする規約に揃える
        // （状態コードの条件は EssayScope の中にある）
        assertThat(statement("sql", "EssayScope")).contains("e.\"状態コード\" = 'A'");
        assertThat(boundSql("findById", Map.of())).contains("WHERE e.\"英作文ID\" = ?");
    }

    @Test
    @DisplayName("登録は 語数・状態コード・バージョン・登録元を SQL 側で固定する")
    void insertsEssay() {
        String sql = statement("insert", "insert");

        assertThat(sql).contains("INSERT INTO public.\"ENG_英作文情報\"");
        assertThat(sql).contains("#{wordCount}");
        assertThat(sql).contains("'A'");
        assertThat(sql).contains("'APP'");
        assertThat(sql).contains("#{createdBy}");
    }

    @Test
    @DisplayName("更新は 語数を数え直し、バージョンを +1 する（本人の行だけ）")
    void updatesEssay() {
        String sql = statement("update", "update");

        assertThat(sql).contains("UPDATE public.\"ENG_英作文情報\"");
        assertThat(sql).contains("\"語数\"             = #{wordCount}");
        assertThat(sql).contains("\"バージョン\"       = \"バージョン\" + 1");
        assertThat(sql).contains("WHERE \"英作文ID\" = #{essayId}");
        assertThat(sql).contains("AND \"利用者アカウントID\" = #{accountId}");
    }

    @Test
    @DisplayName("削除は論理削除（状態コード 'X'。行は残す）")
    void deletesLogically() {
        String sql = statement("update", "logicalDelete");

        assertThat(sql).contains("UPDATE public.\"ENG_英作文情報\"");
        assertThat(sql).contains("SET \"状態コード\"       = 'X'");
        assertThat(sql).contains("\"バージョン\"       = \"バージョン\" + 1");
        assertThat(sql).contains("AND \"利用者アカウントID\" = #{accountId}");
        assertThat(sql).doesNotContain("DELETE FROM public.\"ENG_英作文情報\"");
    }

    @Test
    @DisplayName("画像の登録は 表示順・区分・保存先・MIME・大きさを持つ")
    void insertsImage() {
        String sql = statement("insert", "insertImage");

        assertThat(sql).contains("INSERT INTO public.\"ENG_英作文画像情報\"");
        for (String column : new String[]{"英作文ID", "表示順", "画像区分", "原本ファイル名", "保存ファイル名",
                "相対パス", "MIMEタイプ", "ファイルサイズ", "登録者アカウントID"}) {
            assertThat(sql).as("画像の列 %s", column).contains("\"" + column + "\"");
        }
        assertThat(sql).contains("#{orderNo}");
        assertThat(sql).contains("#{category}");
    }

    @Test
    @DisplayName("画像の登録は OCR の生の結果（認識テキスト・認識信頼度）も持てる")
    void insertsImageRecognition() {
        String sql = statement("insert", "insertImage");

        assertThat(sql).contains("\"認識テキスト\"").contains("\"認識信頼度\"");
        assertThat(sql).contains("#{recognizedText,jdbcType=VARCHAR}");
        assertThat(sql).contains("#{confidence,jdbcType=INTEGER}");
    }

    @Test
    @DisplayName("OCR の生の結果は 渡された欄だけを更新する（省略した欄の既存値を消さない）")
    void updatesImageRecognitionOnlyForGivenColumns() {
        String both = boundSql("updateImageRecognition",
                Map.of("recognizedText", "Do you agree?", "confidence", 93));
        assertThat(both).contains("UPDATE public.\"ENG_英作文画像情報\"");
        assertThat(both).contains("SET \"認識テキスト\" = ?");
        assertThat(both).contains("\"認識信頼度\" = ?");
        // その作文の画像だけ（他人の作文の画像に書けない）
        assertThat(both).contains("WHERE \"英作文画像ID\" = ?");
        assertThat(both).contains("AND \"英作文ID\" = ?");

        Map<String, Object> textOnly = new java.util.HashMap<>();
        textOnly.put("recognizedText", "Do you agree?");
        textOnly.put("confidence", null);
        assertThat(boundSql("updateImageRecognition", textOnly))
                .contains("\"認識テキスト\" = ?").doesNotContain("認識信頼度");

        Map<String, Object> confidenceOnly = new java.util.HashMap<>();
        confidenceOnly.put("recognizedText", null);
        confidenceOnly.put("confidence", 0);
        assertThat(boundSql("updateImageRecognition", confidenceOnly))
                .contains("\"認識信頼度\" = ?").doesNotContain("認識テキスト");
    }

    @Test
    @DisplayName("並べ替えは いったん退避してから確定する（UNIQUE(英作文ID, 表示順) に当たらない）")
    void shiftsOrdersBeforeReordering() {
        String sql = statement("update", "shiftImageOrders");

        assertThat(sql).contains("UPDATE public.\"ENG_英作文画像情報\"");
        assertThat(sql).contains("\"表示順\" = \"表示順\" + #{offset}");
        assertThat(sql).contains("WHERE \"英作文ID\" = #{essayId}");
        // CHECK(表示順 >= 1) があるので、負の値へ逃がさない（必ず増やす）
        assertThat(sql).doesNotContain("- #{offset}");
    }

    @Test
    @DisplayName("要求に含まれない画像の行は消す（含まれない＝空なら全部消える）")
    void deletesImagesNotInTheRequest() {
        String sql = statement("delete", "deleteImagesNotIn");

        assertThat(sql).contains("DELETE FROM public.\"ENG_英作文画像情報\"");
        assertThat(sql).contains("WHERE \"英作文ID\" = #{essayId}");
        assertThat(sql).contains("<when test=\"imageIds == null or imageIds.isEmpty()\">");
        assertThat(sql).contains("NOT IN");
        assertThat(sql).contains("<foreach collection=\"imageIds\"");
    }

    @Test
    @DisplayName("画像の区分と表示順だけを更新する（ファイルの情報は変えない）")
    void updatesImagePlacement() {
        String sql = statement("update", "updateImagePlacement");

        assertThat(sql).contains("SET \"画像区分\" = #{category}");
        assertThat(sql).contains("\"表示順\"   = #{orderNo}");
        assertThat(sql).contains("WHERE \"英作文画像ID\" = #{imageId}");
        assertThat(sql).contains("AND \"英作文ID\" = #{essayId}");
        assertThat(sql).doesNotContain("相対パス");
    }

    @Test
    @DisplayName("添削履歴は 回数の昇順（古い順）で、レポートの JSON をそのまま返す")
    void listsGradingsOldestFirst() {
        String sql = statement("select", "listGradings");

        assertThat(sql).contains("public.\"ENG_AI添削履歴情報\" h");
        assertThat(sql).contains("h.\"添削結果JSON\"");
        assertThat(sql).contains("ORDER BY h.\"回数\" ASC");
        assertThat(sql).contains("h.\"失敗理由\"");
        assertThat(sql).contains("h.\"開始日時\"");
    }

    @Test
    @DisplayName("画像は 表示順の昇順で返す")
    void listsImagesInDisplayOrder() {
        String sql = statement("select", "listImages");

        assertThat(sql).contains("ORDER BY i.\"表示順\" ASC");
        assertThat(sql).contains("i.\"認識テキスト\"");
        assertThat(sql).contains("i.\"認識信頼度\"");
    }

    @Test
    @DisplayName("user-api は ENG_AI添削履歴情報 に書き込まない（参照のみ）")
    void neverWritesGradingHistory() {
        assertThat(XML).doesNotContain("INSERT INTO public.\"ENG_AI添削履歴情報\"");
        assertThat(XML).doesNotContain("UPDATE public.\"ENG_AI添削履歴情報\"");
        assertThat(XML).doesNotContain("DELETE FROM public.\"ENG_AI添削履歴情報\"");
    }
}
