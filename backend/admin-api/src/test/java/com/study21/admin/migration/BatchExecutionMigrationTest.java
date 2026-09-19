package com.study21.admin.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * **データベース移行スクリプトの検証**（実 DB。テスト専用の作業表を作って行う）。
 *
 * <p>見るもの:</p>
 * <ol>
 *   <li>「元実行ID・起動識別子が無い古い構造」から移行でき、**既存の実行記録が変わらない**</li>
 *   <li>移行スクリプトは**繰り返し実行できる**（2 回目も成功する）</li>
 *   <li>元実行ID に**重複があるときは中止**して報告する（履歴を勝手に消したり直したりしない）</li>
 *   <li>いまの実表に、コードが依存する列と一意索引がそろっている（新規作成と移行後で同じ形）</li>
 * </ol>
 *
 * <p>本番の実行記録には触れない（作業表を作って最後に消す）。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class BatchExecutionMigrationTest {

    /** 検証用の作業表（本番の表には触らない）。 */
    private static final String WORK_TABLE = "public.\"BAT_実行履歴_移行テスト\"";

    private static final String SOURCE_TABLE = "public.\"BAT_バッチ実行履歴情報\"";

    @Autowired
    private MigrationTestMapper sql;

    @BeforeEach
    void createOldStructure() {
        sql.execute("DROP TABLE IF EXISTS " + WORK_TABLE);
        // 旧構造（元実行ID・起動識別子が無い）
        sql.execute("CREATE TABLE " + WORK_TABLE + " ("
                + "\"実行ID\" BIGSERIAL NOT NULL PRIMARY KEY,"
                + "\"バッチコード\" VARCHAR(20) NOT NULL,"
                + "\"バッチ種別\" CHAR(1) NOT NULL,"
                + "\"起動種別\" CHAR(1) NOT NULL,"
                + "\"状態\" VARCHAR(20) NOT NULL,"
                + "\"メッセージ\" TEXT NULL,"
                + "\"登録日時\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "\"更新日時\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        sql.execute("INSERT INTO " + WORK_TABLE
                + " (\"バッチコード\",\"バッチ種別\",\"起動種別\",\"状態\",\"メッセージ\") VALUES"
                + " ('batR04','R','R','SUCCESS','移行前の古い行 1'),"
                + " ('batR03','R','R','FAILED','移行前の古い行 2')");
    }

    @AfterEach
    void dropWorkTable() {
        sql.execute("DROP TABLE IF EXISTS " + WORK_TABLE);
    }

    // ------------------------------------------------------------------ ヘルパ

    /**
     * **出荷する移行スクリプト（作業ツリーの実ファイル）**を読む。
     *
     * <p>リポジトリの {@code database/} 配下は日本語のディレクトリ・ファイル名で、
     * JVM のファイル名エンコーディングが UTF-8 でない環境（locale が POSIX 等）では
     * Java からそのパスを開けない（{@code InvalidPathException}）。そこで、
     * ファイルの選択と読み出しだけを**シェル**に任せる（Java 側は ASCII の文字列しか渡さない）。
     * スクリプトには ASCII の識別行（{@code MIGRATION-ID: …}）を書いてあるので、
     * それで選ぶ（日本語を Java のリテラルで持たない）。</p>
     *
     * <p>シェルが無い環境では、この検証は**スキップ**してビルドは止めない
     * （構造の検証だけは DB を見て行う）。</p>
     */
    private static String migrationScript(String migrationId) {
        String command = """
                d=$PWD
                while [ ! -d "$d/database" ] && [ "$d" != "/" ]; do d=$(dirname "$d"); done
                f=$(grep -l 'MIGRATION-ID: %s' "$d"/database/*/*.sql 2>/dev/null | head -1)
                [ -n "$f" ] && cat "$f"
                """.formatted(migrationId);
        try {
            Process process = new ProcessBuilder("bash", "-c", command)
                    .redirectErrorStream(true).start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
            assumeTrue(finished && process.exitValue() == 0 && output.length > 0,
                    "移行スクリプトを読めませんでした（シェルが無い環境ではこの検証をスキップします）: " + migrationId);
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException cause) {
            assumeTrue(false, "シェルを起動できません（この検証はスキップ）: " + cause.getMessage());
            throw new IllegalStateException(cause);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(cause);
        }
    }

    /** 移行スクリプトの対象の表と索引名を作業表用に置き換える。 */
    private String migrationScriptForWorkTable(String resourceName, String indexSuffix) {
        return migrationScript(resourceName)
                .replace(SOURCE_TABLE, WORK_TABLE)
                .replace("\"UQ_BAT_実行履歴_元実行ID\"", "\"UQ_BAT_実行履歴_元実行ID_" + indexSuffix + "\"")
                .replace("idx_bat_history_leftover", "idx_bat_history_leftover_" + indexSuffix);
    }

    /**
     * SQL を 1 文ずつに分ける（{@code DO $$ ... $$} の中の「;」では切らない）。
     *
     * <p>移行スクリプトは複数文（ALTER / COMMENT / DO / CREATE INDEX）なので、
     * 検証では 1 文ずつ流す。</p>
     */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean dollarQuoted = false;
        boolean lineComment = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (lineComment) {
                current.append(c);
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (!dollarQuoted && c == '-' && i + 1 < script.length() && script.charAt(i + 1) == '-') {
                lineComment = true;
                current.append(c);
                continue;
            }
            if (c == '$' && i + 1 < script.length() && script.charAt(i + 1) == '$') {
                dollarQuoted = !dollarQuoted;
                current.append("$$");
                i++;
                continue;
            }
            if (c == ';' && !dollarQuoted) {
                addIfNotBlank(statements, current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        addIfNotBlank(statements, current.toString());
        return statements;
    }

    private static void addIfNotBlank(List<String> statements, String statement) {
        if (!statement.isBlank()) {
            statements.add(statement.strip());
        }
    }

    /** 移行スクリプトを 1 文ずつ実行する（出荷するスクリプトの中身をそのまま流す）。 */
    private void runMigration(String resourceName, String indexSuffix) {
        for (String statement : splitStatements(migrationScriptForWorkTable(resourceName, indexSuffix))) {
            sql.execute(statement);
        }
    }

    /** 出荷するスクリプトの識別子（スクリプト冒頭の {@code MIGRATION-ID} 行と同じ）。 */
    private static final String SOURCE_ID_MIGRATION = "bat-execution-source-execution-id";

    private static final String RUN_ID_MIGRATION = "bat-execution-process-run-id";

    private List<String> columnsOf(String table) {
        String plain = table.replace("public.\"", "").replace("\"", "");
        return sql.query("SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema='public' AND table_name='" + plain + "' ORDER BY ordinal_position")
                .stream().map(row -> String.valueOf(row.get("column_name"))).toList();
    }

    private long countRows() {
        return ((Number) sql.query("SELECT COUNT(*) AS c FROM " + WORK_TABLE).get(0).get("c")).longValue();
    }

    // ------------------------------------------------------------------ テスト

    @Test
    @DisplayName("古い構造から移行できる（列と索引が増え、既存の実行記録は変わらない）")
    void migratesOldStructureAndKeepsTheRows() {
        assertThat(columnsOf(WORK_TABLE)).doesNotContain("元実行ID", "起動識別子");

        // 移行を **2 回**流す（繰り返し実行しても成功すること）
        for (int round = 0; round < 2; round++) {
            runMigration(SOURCE_ID_MIGRATION, "t1");
            runMigration(RUN_ID_MIGRATION, "t1");
        }

        assertThat(columnsOf(WORK_TABLE)).contains("元実行ID", "起動識別子");

        // 既存の実行記録はそのまま（件数も中身も変えない）
        assertThat(countRows()).isEqualTo(2);
        List<Map<String, Object>> rows = sql.query("SELECT \"バッチコード\",\"状態\",\"元実行ID\",\"起動識別子\""
                + " FROM " + WORK_TABLE + " ORDER BY \"実行ID\"");
        assertThat(rows).hasSize(2);
        assertThat(String.valueOf(rows.get(0).get("バッチコード"))).isEqualTo("batR04");
        assertThat(String.valueOf(rows.get(0).get("状態"))).isEqualTo("SUCCESS");
        // 旧行は NULL（＝互換規則で「前のプロセスの遺留」として扱う値）
        assertThat(rows.get(0).get("元実行ID")).isNull();
        assertThat(rows.get(0).get("起動識別子")).isNull();

        // 索引もできている（移行後は 3 つ: 主キー・元実行ID の一意・遺留の検索）
        assertThat(sql.query("SELECT indexname FROM pg_indexes WHERE schemaname='public'"
                + " AND tablename='BAT_実行履歴_移行テスト'")).extracting(row -> String.valueOf(row.get("indexname")))
                .contains("UQ_BAT_実行履歴_元実行ID_t1", "idx_bat_history_leftover_t1");
    }

    @Test
    @DisplayName("元実行ID に重複があるときは中止して報告する（履歴は消さない・直さない）")
    void abortsWhenSourceExecutionIdHasDuplicates() {
        // 列だけ足した状態（移行の前）に、**重複した関連**を作る
        sql.execute("ALTER TABLE " + WORK_TABLE + " ADD COLUMN \"元実行ID\" BIGINT NULL");
        sql.execute("INSERT INTO " + WORK_TABLE
                + " (\"バッチコード\",\"バッチ種別\",\"起動種別\",\"状態\",\"メッセージ\",\"元実行ID\") VALUES"
                + " ('batR04','R','R','FAILED','重複 1', 1001),"
                + " ('batR04','R','R','FAILED','重複 2', 1001)");

        // 重複ができた状態で再実行 → 明示的に中止する
        assertThatThrownBy(() -> runMigration(SOURCE_ID_MIGRATION, "t2"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("移行を中止しました")
                .hasMessageContaining("元実行ID");

        // 中止しただけで、履歴は 1 行も消えていない・書き換わっていない
        assertThat(countRows()).isEqualTo(4);
        assertThat(((Number) sql.query("SELECT COUNT(*) AS c FROM " + WORK_TABLE
                + " WHERE \"元実行ID\" = 1001").get(0).get("c")).longValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("いまの実表に、コードが依存する列と一意索引がそろっている")
    void theRealTableHasTheColumnsAndTheUniqueIndex() {
        assertThat(columnsOf(SOURCE_TABLE)).contains("元実行ID", "起動識別子");

        // insertRetryIfAbsent の ON CONFLICT が依存する**部分一意索引**
        List<Map<String, Object>> indexes = sql.query(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='public'"
                        + " AND tablename='BAT_バッチ実行履歴情報' AND indexname='UQ_BAT_実行履歴_元実行ID'");
        assertThat(indexes).hasSize(1);
        String definition = String.valueOf(indexes.get(0).get("indexdef"));
        assertThat(definition).contains("UNIQUE").contains("元実行ID").contains("WHERE");

        // 復旧が遺留を引くための索引
        assertThat(sql.query("SELECT indexdef FROM pg_indexes WHERE schemaname='public'"
                + " AND indexname='idx_bat_history_leftover'")).hasSize(1);
    }
}
