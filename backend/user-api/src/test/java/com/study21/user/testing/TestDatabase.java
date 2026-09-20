package com.study21.user.testing;

import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * **DB を使う検証は、専用のテスト DB だけを使う**ための小さな入口。
 *
 * <p>検証は本番（配備先）の DB へ書いてはいけない。ここでは環境変数
 * {@code STUDY21_TEST_DATASOURCE_URL} を**唯一の合図**にして、</p>
 * <ol>
 *   <li>設定が無ければ検証を**スキップ**（`Assumptions`）</li>
 *   <li>設定されていれば `spring.datasource.url/username/password` をそれで**上書き**する
 *       （`STUDY21_DATASOURCE_*` や既定値へは**落とさない**）</li>
 * </ol>
 * <p>を行う。「DB 名に test が入っているから安全」といった推測には頼らない。</p>
 *
 * <p>使い方（テストクラス）:</p>
 * <pre>
 * &#64;EnabledIfEnvironmentVariable(named = TestDatabase.URL_VARIABLE, matches = ".+", ...)
 * class SomethingIT {
 *     &#64;DynamicPropertySource
 *     static void database(DynamicPropertyRegistry registry) { TestDatabase.override(registry); }
 * }
 * </pre>
 */
public final class TestDatabase {

    /** 専用のテスト DB の URL（これが無ければ DB を使う検証は動かさない）。 */
    public static final String URL_VARIABLE = "STUDY21_TEST_DATASOURCE_URL";
    /** テスト DB の利用者（任意。無ければ本番と同じ利用者名を使う）。 */
    public static final String USERNAME_VARIABLE = "STUDY21_TEST_DATASOURCE_USERNAME";
    /** テスト DB のパスワード（任意。無ければ本番と同じパスワードを使う）。 */
    public static final String PASSWORD_VARIABLE = "STUDY21_TEST_DATASOURCE_PASSWORD";

    private TestDatabase() {
    }

    /** 専用のテスト DB が設定されているか。 */
    public static boolean configured() {
        String url = System.getenv(URL_VARIABLE);
        return url != null && !url.isBlank();
    }

    /** 設定が無ければ検証をスキップする（`@BeforeAll` の頭で呼ぶ）。 */
    public static void skipUnlessConfigured() {
        Assumptions.assumeTrue(configured(),
                "専用のテスト DB（" + URL_VARIABLE + "）が未設定のためスキップします。");
    }

    /**
     * 接続先を**テスト DB に固定**する。
     *
     * <p>パスワードは {@code STUDY21_TEST_DATASOURCE_PASSWORD} を優先し、無ければ
     * {@code STUDY21_DATASOURCE_PASSWORD}（既存の下見用 DB の運用に合わせる）。
     * **どちらも無ければ空**になり、接続に失敗する＝設定漏れが「通ってしまう」ことはない。</p>
     */
    public static void override(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv(URL_VARIABLE));
        registry.add("spring.datasource.username", () -> {
            String username = System.getenv(USERNAME_VARIABLE);
            if (username != null && !username.isBlank()) {
                return username;
            }
            return System.getenv("STUDY21_DATASOURCE_USERNAME");
        });
        registry.add("spring.datasource.password", () -> {
            String password = System.getenv(PASSWORD_VARIABLE);
            if (password != null && !password.isBlank()) {
                return password;
            }
            String fallback = System.getenv("STUDY21_DATASOURCE_PASSWORD");
            return fallback == null ? "" : fallback;
        });
    }

    /** テスト DB の場所（ログ用。**パスワードは出さない**）。 */
    public static String describe() {
        return "HOST=" + System.getenv(URL_VARIABLE);
    }
}
