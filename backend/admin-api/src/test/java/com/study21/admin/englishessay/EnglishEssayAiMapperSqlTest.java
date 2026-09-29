package com.study21.admin.englishessay;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 英作文 AI 添削の SQL（{@code EnglishEssayAiMapper.xml}）の契約。
 *
 * <p>実 DB は使わない（MyBatis に XML を読ませて SQL を固定する）。確かめる接縫:</p>
 * <ol>
 *   <li>受付は <b>そのときの写し</b>を {@code QUEUED} で入れる（列の対応を間違えない）</li>
 *   <li>回数は「その作文の最大 + 1」</li>
 *   <li>働き手の取り出しは 1 件をロックして確保し、{@code FAILED} は拾わない</li>
 *   <li>成功で 得点・満点・{@code 添削結果JSON}(jsonb)・{@code AI呼出履歴ID} を書き、失敗で理由を残す</li>
 *   <li>OCR の結果は画像の行と作文の行へ書き戻す（{@code 登録元コード='BATCH'}）</li>
 *   <li><b>DELETE しない</b>（添削の履歴は消さない）</li>
 * </ol>
 */
class EnglishEssayAiMapperSqlTest {

    private static final String NAMESPACE = "com.study21.admin.englishessay.EnglishEssayAiMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = EnglishEssayAiMapperSqlTest.class
                .getResourceAsStream("/mapper/EnglishEssayAiMapper.xml")) {
            assertThat(input).as("EnglishEssayAiMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "EnglishEssayAiMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = EnglishEssayAiMapperSqlTest.class
                .getResourceAsStream("/mapper/EnglishEssayAiMapper.xml")) {
            assertThat(stream).as("EnglishEssayAiMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    private static String boundSql(String id, Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + id);
        return statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }

    private static String boundSql(String id) {
        return boundSql(id, new HashMap<>());
    }

    @Test
    @DisplayName("履歴を DELETE しない（添削は積み上げる）")
    void neverDeletes() {
        assertThat(XML).doesNotContain("<delete");
    }

    @Test
    @DisplayName("作文（写しの元）を読む")
    void findsEssay() {
        String sql = boundSql("findEssay", Map.of("essayId", 900001L));

        assertThat(sql).contains("FROM public.\"ENG_英作文情報\"");
        assertThat(sql).contains("\"英検級\" AS level");
        assertThat(sql).contains("\"題\" AS titleJa").contains("\"題_中国語\" AS titleZh");
        assertThat(sql).contains("\"設問文\" AS questionText").contains("\"作文本文\" AS essayText");
        assertThat(sql).contains("\"語数\" AS wordCount").contains("\"状態コード\" AS stateCode");
        assertThat(sql).contains("WHERE e.\"英作文ID\" = ?");
    }

    @Test
    @DisplayName("画像の一覧は表示順（設問 → 答案）で読む")
    void listsImages() {
        String sql = boundSql("listEssayImages", Map.of("essayId", 900001L));

        assertThat(sql).contains("FROM public.\"ENG_英作文画像情報\"");
        assertThat(sql).contains("\"画像区分\" AS category").contains("\"保存ファイル名\" AS savedFileName");
        assertThat(sql).contains("\"相対パス\" AS relativePath").contains("\"MIMEタイプ\" AS mimeType");
        assertThat(sql).contains("ORDER BY i.\"表示順\"");
    }

    @Test
    @DisplayName("受付は写しを QUEUED で入れる（採番した 添削ID を返す）")
    void insertsQueuedGrading() {
        String sql = boundSql("insertGrading");

        assertThat(sql).contains("INSERT INTO public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("'QUEUED'");
        assertThat(sql).contains("\"英検級\", \"題_日本語\", \"題_中国語\"");
        assertThat(sql).contains("\"設問文\", \"作文本文\", \"語数\"");
        assertThat(sql).contains("\"登録者アカウントID\"");
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertGrading").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    @DisplayName("受付は実行中の添削を探す（二重に積まない）")
    void findsActiveGrading() {
        String sql = boundSql("findActiveGrading", Map.of("essayId", 900001L));

        assertThat(sql).contains("FROM public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("\"状態コード\" IN ('QUEUED', 'RUNNING')");
        assertThat(sql).contains("LIMIT 1");
    }

    @Test
    @DisplayName("回数は「その作文の最大 + 1」")
    void computesNextRound() {
        String sql = boundSql("nextRound", Map.of("essayId", 900001L));

        assertThat(sql).contains("COALESCE(MAX(g.\"回数\"), 0) + 1");
        assertThat(sql).contains("WHERE g.\"英作文ID\" = ?");
    }

    @Test
    @DisplayName("働き手の取り出しは 1 件をロックして確保する（FAILED は拾わない）")
    void claimsQueuedGrading() {
        String sql = boundSql("findClaimableGradingId", Map.of("staleMinutes", 5));

        assertThat(sql).contains("FROM public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("g.\"状態コード\" = 'QUEUED'");
        assertThat(sql).contains("g.\"状態コード\" = 'RUNNING'");
        assertThat(sql).contains("INTERVAL '1 minute'");
        assertThat(sql).contains("FOR UPDATE SKIP LOCKED");
        assertThat(sql).contains("LIMIT 1");
        assertThat(sql).doesNotContain("'FAILED'");
    }

    @Test
    @DisplayName("取り出しの確定は状態を条件にした更新（開始日時を今回の試行の時刻へ入れ直す）")
    void marksClaimed() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("gradingId", 77L);
        parameters.put("fromStatuses", List.of("QUEUED", "RUNNING"));

        String sql = boundSql("markGradingClaimed", parameters);

        assertThat(sql).contains("UPDATE public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("\"状態コード\" = 'RUNNING'");
        // この表には 更新日時 が無い。「落ちたままの RUNNING」の判定に使う 開始日時 を
        // ここで入れ直さないと、他の働き手がすぐ拾い直して AI を二重に呼ぶ
        assertThat(sql).contains("\"開始日時\" = CURRENT_TIMESTAMP");
        assertThat(sql).contains("AND \"状態コード\" IN ( ? , ? )");
    }

    @Test
    @DisplayName("成功は 得点・満点・結果JSON・呼出履歴ID を書く（JSON は jsonb へ）")
    void marksSucceeded() {
        String sql = boundSql("markGradingSucceeded");

        assertThat(sql).contains("UPDATE public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("\"状態コード\" = 'SUCCEEDED'");
        assertThat(sql).contains("\"総合得点\" = ?").contains("\"満点\" = ?");
        assertThat(sql).contains("CAST(? AS jsonb)");
        assertThat(sql).contains("\"AI呼出履歴ID\" = ?");
        assertThat(sql).contains("\"失敗理由\" = NULL");
        assertThat(sql).contains("\"終了日時\" = CURRENT_TIMESTAMP");
    }

    @Test
    @DisplayName("失敗は理由と終了日時を残す")
    void marksFailed() {
        String sql = boundSql("markGradingFailed");

        assertThat(sql).contains("\"状態コード\" = 'FAILED'");
        assertThat(sql).contains("\"失敗理由\" = ?");
        assertThat(sql).contains("\"終了日時\" = CURRENT_TIMESTAMP");
    }

    @Test
    @DisplayName("OCR の結果は画像の行へ書き戻す（文字と信頼度）")
    void updatesImageRecognized() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("imageId", 11L);
        parameters.put("text", "Do you agree?");
        parameters.put("confidence", 95);

        String sql = boundSql("updateImageRecognized", parameters);

        assertThat(sql).contains("UPDATE public.\"ENG_英作文画像情報\"");
        assertThat(sql).contains("\"認識テキスト\" = ?").contains("\"認識信頼度\" = ?");
        assertThat(sql).contains("WHERE \"英作文画像ID\" = ?");
    }

    @Test
    @DisplayName("OCR の結果は作文の行へ書き戻す（設問文・作文本文・語数。バッチが書いたと分かる）")
    void updatesEssayRecognized() {
        String sql = boundSql("updateEssayRecognized", Map.of(
                "essayId", 900001L, "questionText", "q", "essayText", "e", "wordCount", 7));

        assertThat(sql).contains("UPDATE public.\"ENG_英作文情報\"");
        assertThat(sql).contains("\"設問文\" = ?").contains("\"作文本文\" = ?").contains("\"語数\" = ?");
        assertThat(sql).contains("'BATCH'");
    }

    @Test
    @DisplayName("題（日本語・中国語）を書き戻す")
    void updatesEssayTitle() {
        String sql = boundSql("updateEssayTitle", Map.of(
                "essayId", 900001L, "titleJa", "読書", "titleZh", "读书"));

        assertThat(sql).contains("\"題\" = ?").contains("\"題_中国語\" = ?");
        assertThat(sql).contains("'BATCH'");
    }

    @Test
    @DisplayName("画面のポーリング用に 状態・回数・得点・失敗理由 を返す")
    void findsGradingStatus() {
        String sql = boundSql("findGradingStatus", Map.of("gradingId", 501L));

        assertThat(sql).contains("FROM public.\"ENG_AI添削履歴情報\"");
        assertThat(sql).contains("\"回数\" AS round");
        assertThat(sql).contains("\"状態コード\" AS statusCode");
        assertThat(sql).contains("\"総合得点\" AS score");
        assertThat(sql).contains("\"満点\" AS maxScore");
        assertThat(sql).contains("\"失敗理由\" AS failureReason");
        assertThat(sql).contains("WHERE g.\"添削ID\" = ?");
    }
}
