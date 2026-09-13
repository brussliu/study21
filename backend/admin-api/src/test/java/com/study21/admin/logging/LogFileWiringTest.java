package com.study21.admin.logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.study21.common.core.logging.SqlLoggingInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * logback-spring.xml（common-core）が期待どおりに組み込まれているかの確認。
 *
 * <p>ファイルが 1 つも作られない設定ミス（XML の書き間違い等）は起動時に気づきにくいため、
 * app / sql / error の 3 ファイルとサービス名プレフィックス、出力先フォルダをテストで固定する。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogFileWiringTest {

    @Test
    void writesAppSqlAndErrorLogsUnderBackendFolder() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(filePaths(root))
                .anySatisfy(path -> assertThat(path).endsWith("/backend/admin-api-app.log"))
                .anySatisfy(path -> assertThat(path).endsWith("/backend/admin-api-error.log"));
        // SQL ログはルート（app.log / コンソール）には出さない
        assertThat(filePaths(root)).noneSatisfy(path ->
                assertThat(path).endsWith("admin-api-sql.log"));
    }

    @Test
    void sqlLoggerGoesOnlyToSqlFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger sqlLogger = context.getLogger(SqlLoggingInterceptor.SQL_LOGGER_NAME);
        assertThat(sqlLogger.isAdditive()).isFalse();
        assertThat(filePaths(sqlLogger)).singleElement()
                .satisfies(path -> assertThat(path).endsWith("/backend/admin-api-sql.log"));
    }

    private List<String> filePaths(Logger logger) {
        List<String> paths = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders(); it.hasNext(); ) {
            Appender<ILoggingEvent> appender = it.next();
            if (appender instanceof FileAppender<?> fileAppender && fileAppender.getFile() != null) {
                paths.add(Paths.get(fileAppender.getFile()).toAbsolutePath().toString().replace('\\', '/'));
            }
        }
        return paths;
    }

    @Test
    void sqlLogLineGoesOnlyToSqlFile() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger sqlLogger = context.getLogger(SqlLoggingInterceptor.SQL_LOGGER_NAME);
        String marker = "wiring-check-" + System.nanoTime();

        sqlLogger.info("op=SELECT mapper=wiring rows=1 marker={}", marker);

        Path sqlFile = logFile(sqlLogger, "admin-api-sql.log");
        Path appFile = sqlFile.resolveSibling("admin-api-app.log");
        assertThat(Files.readString(sqlFile)).contains(marker);
        assertThat(Files.readString(appFile)).doesNotContain(marker);
    }

    /** ロガーに紐づくファイル appender の出力パス。 */
    private Path logFile(Logger logger, String fileName) {
        return filePaths(logger).stream()
                .filter(path -> path.endsWith(fileName))
                .findFirst()
                .map(Paths::get)
                .orElseThrow(() -> new AssertionError(fileName + " の出力先が見つかりません"));
    }
}
