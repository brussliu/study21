package com.study21.admin.japanesewordai;

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
 * 日本語単語 AI 取得の SQL（{@code JapaneseWordAiMapper.xml}）。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>対象は「まだ成功していない語」だけ（{@code JPN_AI生成履歴情報} の
 *       {@code 状態コード='SUCCEEDED'} を除く）。失敗した語はもう一度拾える</li>
 *   <li>画面から選んだ語（{@code wordIds}）があるときは、その語だけを対象にする</li>
 *   <li><b>問題と選択肢を DELETE しない。</b>2.0 は 6 つの子テーブルを消していたが、
 *       2.1 は {@code ARCHIVED} にして残す（{@code JPN_テスト出題情報.問題ID} が参照している）</li>
 *   <li>詳細は<b>新しい版を作る</b>（1 語 1 行の UPSERT はやめた。内容版数は「その語の最大 + 1」）</li>
 *   <li>まだ読みが無い語には、AI の読みを書き戻す（既にある読みは触らない）</li>
 *   <li>まだ JLPT レベルが無い語には、AI のレベル（N1〜N5）を書き戻す（既にある値は触らない）</li>
 *   <li>書き込みは「バッチが書いた」ことが分かる（{@code 登録元コード='BATCH'}）</li>
 * </ol>
 */
class JapaneseWordAiMapperSqlTest {

    private static final String NAMESPACE = "com.study21.admin.japanesewordai.JapaneseWordAiMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JapaneseWordAiMapperSqlTest.class
                .getResourceAsStream("/mapper/JapaneseWordAiMapper.xml")) {
            assertThat(input).as("JapaneseWordAiMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JapaneseWordAiMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = JapaneseWordAiMapperSqlTest.class
                .getResourceAsStream("/mapper/JapaneseWordAiMapper.xml")) {
            assertThat(stream).as("JapaneseWordAiMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 文の SQL（MyBatis が組み立てた形。パラメータは ? になる）。 */
    private static String boundSql(String id, Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + id);
        return statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }

    /** 静的な文（動的 SQL でないもの）。 */
    private static String boundSql(String id) {
        return boundSql(id, new HashMap<>());
    }

    @Test
    @DisplayName("対象は「まだ成功していない語」だけ。失敗した語はもう一度拾える")
    void selectsOnlyUnfinishedWords() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contentType", "A_DETAIL");
        parameters.put("wordIds", List.of());
        parameters.put("limit", 20);

        String sql = boundSql("findTargets", parameters);

        assertThat(sql).contains("FROM public.\"JPN_単語情報\"");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("a.\"内容種別コード\" = ?");
        assertThat(sql).contains("a.\"状態コード\" = 'SUCCEEDED'");
        // 引数のある語は対象にしない（画面から選んだときだけ）
        assertThat(sql).doesNotContain(" IN (");
        assertThat(sql).contains("LIMIT ?");
    }

    @Test
    @DisplayName("画面から選んだ語があるときは、その語だけを対象にする")
    void selectsChosenWords() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contentType", "C1_READING");
        parameters.put("wordIds", List.of(101L, 102L));
        parameters.put("limit", 20);

        String sql = boundSql("findTargets", parameters);

        assertThat(sql).contains("w.\"単語ID\" IN ( ? , ? )");
    }

    @Test
    @DisplayName("問題と選択肢を DELETE しない（履歴を消さない）")
    void neverDeletesQuestions() {
        assertThat(XML).doesNotContain("<delete");
        // 問題は ARCHIVED にしてから入れる
        String archive = boundSql("archiveQuestions");
        assertThat(archive).contains("UPDATE public.\"JPN_単語問題情報\"");
        assertThat(archive).contains("SET \"状態コード\" = 'ARCHIVED'");
        assertThat(archive).contains("AND \"状態コード\" = 'ACTIVE'");
    }

    @Test
    @DisplayName("詳細の上書き（upsertDetail）は無い。版は積み上げる（切片3）")
    void doesNotOverwriteDetailAnymore() {
        // 1 語 1 行の UPSERT はやめた。新しい版を作る SQL は
        // JapaneseWordAiVersionSqlTest が確かめる
        assertThat(configuration.hasStatement(NAMESPACE + "upsertDetail")).isFalse();
        assertThat(configuration.hasStatement(NAMESPACE + "insertDetailVersion")).isTrue();
    }

    @Test
    @DisplayName("問題と選択肢の INSERT は、バッチが書いたと分かる形にする")
    void insertsAsBatch() {
        assertThat(boundSql("insertQuestion")).contains("'BATCH'");
        assertThat(boundSql("insertQuestion")).contains("CAST(? AS jsonb)");
        assertThat(boundSql("insertChoice")).contains("'BATCH'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertQuestion").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertChoice").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    @DisplayName("まだ読みが無い語にだけ、AI の読みを入れる（既にある読みは上書きしない）")
    void fillsOnlyBlankReading() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("wordId", 101L);
        parameters.put("reading", "あい");

        String sql = boundSql("updateReadingIfBlank", parameters);

        assertThat(sql).contains("UPDATE public.\"JPN_単語情報\"");
        assertThat(sql).contains("SET \"読み\" = ?");
        // 読みキーは NOT NULL。読みと同じ値を入れる
        assertThat(sql).contains("\"読みキー\" = ?");
        // 読みが空の語だけ（画面で入れた読みを AI で潰さない）
        assertThat(sql).contains("AND NULLIF(BTRIM(COALESCE(w.\"読み\", '')), '') IS NULL");
        // 同じ見出し語・同じ読みの語が既にあるときは入れない（部分 UNIQUE に当てない）
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("o.\"見出し語キー\" = w.\"見出し語キー\"");
        assertThat(sql).contains("o.\"読みキー\" = ?");
        // バッチが書いたことが分かる
        assertThat(sql).contains("'BATCH'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "updateReadingIfBlank").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    @DisplayName("まだ JLPT レベルが無い語にだけ、AI のレベルを入れる（既にある値は上書きしない）")
    void fillsOnlyBlankJlpt() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("wordId", 101L);
        parameters.put("jlpt", "N3");

        String sql = boundSql("updateJlptIfBlank", parameters);

        assertThat(sql).contains("UPDATE public.\"JPN_単語情報\"");
        assertThat(sql).contains("SET \"JLPTレベル\" = ?");
        assertThat(sql).contains("AND NULLIF(BTRIM(COALESCE(\"JLPTレベル\", '')), '') IS NULL");
        assertThat(sql).contains("'BATCH'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "updateJlptIfBlank").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    @DisplayName("生成履歴は 1 語 × 1 内容種別。実行中の行を探してから作る")
    void tracksGeneration() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("wordId", 101L);
        parameters.put("contentType", "A_DETAIL");

        String active = boundSql("findActiveGeneration", parameters);
        assertThat(active).contains("a.\"状態コード\" IN ('QUEUED', 'RUNNING')");
        assertThat(active).contains("LIMIT 1");

        assertThat(boundSql("insertGeneration")).contains("'RUNNING'");
        assertThat(boundSql("markGenerationSucceeded"))
                .contains("\"状態コード\" = 'SUCCEEDED'")
                .contains("\"生成件数\" = ?");
        assertThat(boundSql("markGenerationFailed"))
                .contains("\"状態コード\" = 'FAILED'")
                .contains("\"失敗件数\" = \"失敗件数\" + 1");
    }

    @Test
    @DisplayName("受付は QUEUED の行を作る（AI を呼ぶ前に、実行待ちとして積む）")
    void insertsQueuedGeneration() {
        String sql = boundSql("insertQueuedGeneration");

        assertThat(sql).contains("INSERT INTO public.\"JPN_AI生成履歴情報\"");
        assertThat(sql).contains("'QUEUED'");
        // 受付は画面から（定時バッチではない）
        assertThat(sql).contains("'APP'");
        // **実行中の行が無いときだけ**入れる（同時の受付で二重に積まない＝AI を二重に呼ばない）
        assertThat(sql).contains("WHERE NOT EXISTS");
        assertThat(sql).contains("a.\"状態コード\" IN ('QUEUED', 'RUNNING')");
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertQueuedGeneration").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    @DisplayName("受付けた取得の進み具合は、生の状態を数える（成功ありのビューを使わない）")
    void countsProgressByState() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("generationIds", List.of(7L, 8L));

        String sql = boundSql("countGenerationsByState", parameters);

        assertThat(sql).contains("FROM public.\"JPN_AI生成履歴情報\"");
        assertThat(sql).contains("COUNT(*) FILTER (WHERE a.\"状態コード\" IN ('QUEUED', 'RUNNING'))");
        assertThat(sql).contains("'SUCCEEDED'");
        assertThat(sql).contains("'FAILED'");
        assertThat(sql).contains("WHERE a.\"生成ID\" IN ( ? , ? )");
    }

    @Test
    @DisplayName("働き手の取り出しは 1 件をロックして確保する（二重に走らせない）")
    void claimsQueuedGeneration() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("staleMinutes", 5);

        String sql = boundSql("findClaimableGenerationId", parameters);

        // 待機中と、落ちたままの実行中（一定時間より古いもの）を拾う
        assertThat(sql).contains("a.\"状態コード\" = 'QUEUED'");
        assertThat(sql).contains("a.\"状態コード\" = 'RUNNING'");
        assertThat(sql).contains("INTERVAL '1 minute'");
        // 古い順に 1 件。取り合いにならないようロックを飛ばす
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("LIMIT 1");
        assertThat(sql).contains("FOR UPDATE SKIP LOCKED");
        // 失敗は拾わない（利用者がやり直す）
        assertThat(sql).doesNotContain("'FAILED'");
    }

    @Test
    @DisplayName("取り出しの確定は状態を条件にした更新（他の働き手が先に取っていたら 0 行）")
    void marksClaimedConditionally() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("generationId", 77L);
        parameters.put("fromStatuses", List.of("QUEUED", "RUNNING"));

        String sql = boundSql("markGenerationClaimed", parameters);

        assertThat(sql).contains("UPDATE public.\"JPN_AI生成履歴情報\"");
        assertThat(sql).contains("\"状態コード\" = 'RUNNING'");
        assertThat(sql).contains("WHERE \"生成ID\" = ?");
        assertThat(sql).contains("AND \"状態コード\" IN ( ? , ? )");
        assertThat(sql).contains("'BATCH'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "markGenerationClaimed").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }
}
