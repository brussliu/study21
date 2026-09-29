package com.study21.admin.japanesewordai;

import com.study21.admin.testing.TestDatabase;
import com.study21.admin.testing.TestSqlMapper;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 詳細の版の書き込みを<b>真 DB</b>で確かめる。
 *
 * <p>MyBatis の層でしか分からないことを見る（モックのテストでは通っても動かない部分）:</p>
 * <ol>
 *   <li>{@code insertDetailVersion} の {@code useGeneratedKeys}（VALUES ＋ 副問い合わせ）が
 *       採番した {@code 詳細ID} を返すか</li>
 *   <li>11 の子テーブルへの INSERT のパラメータ（{@code #{row.*}}）が解決するか</li>
 *   <li>2 回目で古い版が ARCHIVED・新しい版だけ ACTIVE になるか（部分 UNIQUE 索引に当たらないか）</li>
 *   <li>人が入れた行（{@code 登録元コード='APP'}）が新しい版へ複製されるか</li>
 *   <li>書き込んだ版を組み立て直して読めるか（AI に渡す入力・画面が読む形）</li>
 * </ol>
 *
 * <p><b>接続先は専用のテスト DB だけ</b>（{@code @ActiveProfiles("testdb")}）。日常の開発 DB へは
 * 繋がない（起動時の自動バッチ＝{@code study21.batch.auto-run.*} も testdb では止まる）。
 * {@code STUDY21_TEST_DATASOURCE_URL} が無ければスキップする。
 * 検証データの作成・確認も {@link TestSqlMapper}（MyBatis 経由＝SQL ログに残る）で行う。</p>
 *
 * <p>{@code @Transactional} なのでテストの最後に必ず ROLLBACK する（真 DB を汚さない）。</p>
 */
@SpringBootTest
@ActiveProfiles("testdb")
@Transactional
@EnabledIfEnvironmentVariable(named = TestDatabase.URL_VARIABLE, matches = ".+",
        disabledReason = "専用のテスト DB（STUDY21_TEST_DATASOURCE_URL）が未設定のためスキップ")
class JapaneseWordAiVersionRepositoryTest {

    @Autowired
    private JapaneseWordAiMapper mapper;

    @Autowired
    private JapaneseWordAiDetailWriter writer;

    /** テスト専用の SQL（MyBatis 経由なので SQL ログに残る。JdbcTemplate は使わない）。 */
    @Autowired
    private TestSqlMapper sql;

    /** 接続先を専用のテスト DB に固定する（{@code STUDY21_DATASOURCE_*} へは落とさない）。 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.override(registry);
    }

    /** SELECT の 1 列を long で取る（無ければ例外）。 */
    private long queryLong(String statement) {
        Long value = queryLongOrNull(statement);
        if (value == null) {
            throw new IllegalStateException("検証用の SELECT が値を返しませんでした: " + statement);
        }
        return value;
    }

    /** SELECT の 1 列を long で取る（NULL は null）。 */
    private Long queryLongOrNull(String statement) {
        List<Map<String, Object>> rows = sql.query(statement);
        if (rows.isEmpty() || rows.get(0).values().iterator().next() == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(rows.get(0).values().iterator().next()));
    }

    /** 語を 1 つ作る（このテストの中だけ。ROLLBACK で消える）。 */
    private long createWord() {
        return queryLong(
                "INSERT INTO public.\"JPN_単語情報\" (\"見出し語\", \"読み\", \"見出し語キー\", \"読みキー\","
                        + " \"状態コード\", \"登録元コード\")"
                        + " VALUES ('版の確認', 'はんのかくにん', '版の確認', 'はんのかくにん', 'ACTIVE', 'BATCH')"
                        + " RETURNING \"単語ID\"");
    }

    private static String detailJson(String coreMeaning) {
        JapaneseWordAiDtoMapper.DetailResult parsed = JapaneseWordAiDtoMapper.parseDetail("""
                {"detail":{
                 "coreMeaning":"%s","descriptionJa":"説明。","descriptionZh":"说明。",
                 "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":3,
                 "senses":[{"number":1,"japanese":"意味。","chinese":"意思。"}],
                 "examples":[{"japanese":"例文1。","chinese":"例句1。"},{"japanese":"例文2。","chinese":"例句2。"}],
                 "patterns":[{"pattern":"型","chinese":"型"}],
                 "dialogs":[{"scene":"場面","lines":[{"speaker":"A","japanese":"せりふ。","chinese":"台词。"}]}],
                 "synonyms":[{"heading":"類語","chinese":"近义词"}],
                 "cautions":[{"kind":"MEANING","title":"見出し","wrong":"誤り","correct":"正しい","reason":"理由"}],
                 "collocations":[{"expression":"言い回し1","chinese":"搭配1"},{"expression":"言い回し2","chinese":"搭配2"}],
                 "relatedWords":[{"relation":"類義語","heading":"関連","chinese":"关联"}],
                 "usageNotes":[{"register":"どちらも","politeness":"普通","audience":"家族に"}],
                 "practices":[{"kind":"PARTICLE","question":"問題。","choices":["に","を"],"answer":"を"}],
                 "pronunciation":{"reading":"はん","accentType":1,"hint":"ヒント","hasAudioSample":false}}}
                """.formatted(coreMeaning));
        assertThat(parsed.isSuccess()).isTrue();
        return parsed.detailJson();
    }

    private int count(String table, long detailId) {
        return (int) queryLong("SELECT count(*) AS \"件数\" FROM public.\"" + table + "\" WHERE \"詳細ID\" = "
                + detailId);
    }

    @Test
    @DisplayName("2 回取得すると 2 版になり、古い版は ARCHIVED・新しい版だけ ACTIVE。人の行は複製される")
    void createsVersionsOnRealDatabase() {
        long wordId = createWord();

        // 1 回目（初版）。11 の子テーブルが 1 回の書き込みでそろう
        JapaneseWordAiDetailWriter.CreatedVersion first =
                writer.createVersion(wordId, detailJson("一つ目"), 7001L, "qwen", "qwen3.7-plus");
        assertThat(first.detailId()).isNotNull();
        assertThat(first.originDetailId()).isNull();
        assertThat(count("JPN_単語詳細_語義情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_例文情報", first.detailId())).isEqualTo(2);
        assertThat(count("JPN_単語詳細_文型情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_会話情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_類義語情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_注意情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_コロケーション情報", first.detailId())).isEqualTo(2);
        assertThat(count("JPN_単語詳細_関連語情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_使用場面情報", first.detailId())).isEqualTo(1);
        assertThat(count("JPN_単語詳細_練習情報", first.detailId())).isEqualTo(1);

        // 人が例文を 1 行だけ直した（表示順 2。AI の例文の後ろ）
        sql.execute("INSERT INTO public.\"JPN_単語詳細_例文情報\" (\"詳細ID\", \"表示順\", \"例文_日本語\","
                + " \"例文_中国語\", \"手修正フラグ\", \"登録元コード\")"
                + " VALUES (" + first.detailId() + ", 2, '人が直した例文。', '人修改的例句。', true, 'APP')");

        // 2 回目（新しい版）
        JapaneseWordAiDetailWriter.CreatedVersion second =
                writer.createVersion(wordId, detailJson("二つ目"), 7002L, "qwen", "qwen3.7-plus");
        assertThat(second.originDetailId()).isEqualTo(first.detailId());
        assertThat(second.maxKeptOrderNo()).isEqualTo(2);

        // 版の状態（1 語 1 版の ACTIVE）
        Integer active = (int) queryLong("SELECT count(*) AS \"件数\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"単語ID\" = " + wordId + " AND \"状態コード\" = 'ACTIVE'");
        Integer archived = (int) queryLong("SELECT count(*) AS \"件数\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"単語ID\" = " + wordId + " AND \"状態コード\" = 'ARCHIVED'");
        assertThat(active).isEqualTo(1);
        assertThat(archived).isEqualTo(1);
        // 生成ID と 元詳細ID が版のヘッダに入っている
        assertThat(queryLongOrNull("SELECT \"生成ID\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"詳細ID\" = " + second.detailId())).isEqualTo(7002L);
        assertThat(queryLongOrNull("SELECT \"元詳細ID\" FROM public.\"JPN_単語詳細情報\""
                + " WHERE \"詳細ID\" = " + second.detailId())).isEqualTo(first.detailId());

        // 人が直した例文が新しい版にも残っている（内容・出所・手修正フラグ そのまま）
        Integer kept = (int) queryLong("SELECT count(*) AS \"件数\" FROM public.\"JPN_単語詳細_例文情報\""
                + " WHERE \"詳細ID\" = " + second.detailId() + " AND \"例文_日本語\" = '人が直した例文。'"
                + " AND \"手修正フラグ\" AND \"登録元コード\" = 'APP'");
        assertThat(kept).isEqualTo(1);
        // AI の例文（2 件）＋ 人の例文（1 件）
        assertThat(count("JPN_単語詳細_例文情報", second.detailId())).isEqualTo(3);
        // 人が入れた行は新しい版でも 表示順 2 のまま、AI の行は 3 から
        assertThat(queryLongOrNull("SELECT \"表示順\" FROM public.\"JPN_単語詳細_例文情報\""
                + " WHERE \"詳細ID\" = " + second.detailId() + " AND \"登録元コード\" = 'APP'"))
                .isEqualTo(2L);
        assertThat(queryLongOrNull("SELECT min(\"表示順\") AS \"表示順\" FROM public.\"JPN_単語詳細_例文情報\""
                + " WHERE \"詳細ID\" = " + second.detailId() + " AND \"登録元コード\" = 'BATCH'"))
                .isEqualTo(3L);
        // 版のヘッダの 手修正フラグ（この版に人の編集が含まれるか）。
        // 人が入れた行を複製した版は true、AI だけの版は false（SQL が値をそのまま書く）
        assertThat(manualCorrected(first.detailId())).isFalse();
        assertThat(manualCorrected(second.detailId())).isTrue();
    }

    /** 版のヘッダの {@code 手修正フラグ}（この版に人の編集が含まれるか）。 */
    private boolean manualCorrected(long detailId) {
        return queryLong("SELECT CASE WHEN \"手修正フラグ\" THEN 1 ELSE 0 END AS \"手修正\""
                + " FROM public.\"JPN_単語詳細情報\" WHERE \"詳細ID\" = " + detailId) == 1L;
    }

    @Test
    @DisplayName("書き込んだ版を組み立て直して読める（AI に渡す入力・画面が読む形）")
    void readsBackAssembledDetail() {
        long wordId = createWord();
        JapaneseWordAiDetailWriter.CreatedVersion created =
                writer.createVersion(wordId, detailJson("読み戻し"), 7003L, "qwen", "qwen3.7-plus");

        JpnWordDetailEntity header = mapper.findActiveDetail(wordId);
        assertThat(header.getDetailId()).isEqualTo(created.detailId());
        assertThat(header.getCoreMeaning()).isEqualTo("読み戻し");
        assertThat(header.getJlptLevel()).isEqualTo("N3");
        // JSONB は DB が整形して返す（空白の入り方は問わない）
        assertThat(header.getPronunciationJson()).contains("はん").contains("reading");

        // 11 の子テーブルが読める（組み立ての入力がそろう）
        assertThat(mapper.listDetailSenses(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailExamples(created.detailId())).hasSize(2);
        assertThat(mapper.listDetailPatterns(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailDialogs(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailDialogLines(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailSynonyms(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailCautions(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailCollocations(created.detailId())).hasSize(2);
        assertThat(mapper.listDetailRelatedWords(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailUsageNotes(created.detailId())).hasSize(1);
        assertThat(mapper.listDetailPractices(created.detailId())).hasSize(1);
        // 会話行は親の 会話ID でぶら下がる
        JpnWordDetailChildren.Dialog dialog = mapper.listDetailDialogs(created.detailId()).get(0);
        assertThat(mapper.listDetailDialogLines(created.detailId()).get(0).getDialogId())
                .isEqualTo(dialog.getDialogId());
        // 表示順は 1 から（初版は人が入れた行が無い）
        assertThat(mapper.listDetailExamples(created.detailId()).get(0).getOrderNo()).isEqualTo(1);
        assertThat(mapper.listDetailExamples(created.detailId()).get(1).getOrderNo()).isEqualTo(2);
    }
}
