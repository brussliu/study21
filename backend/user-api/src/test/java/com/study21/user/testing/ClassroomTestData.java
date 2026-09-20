package com.study21.user.testing;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 権限・契約の検証で使う**専用のテストデータ**（毎回作って、毎回消す）。
 *
 * <p><b>既存のデータを一切使わない</b>。日常使っているアカウント・授業・まとめを選んで使ったり、
 * そのパスワードを書き換えたりしない（下見用の DB でも、他のテストや作業中のデータを壊し得る）。
 * ここでは</p>
 * <ol>
 *   <li>**テスト専用の保護者→生徒**を作る（DDL の制約を満たすため保護者は 1 人ずつ。ログインID は
 *       毎回一意）、</li>
 *   <li>その生徒の**授業とまとめ**を作る（ID は作った直後に控える）、</li>
 *   <li>後片付けは**控えた ID だけ**を、依存の逆順に消す（まとめ → 授業 → 生徒 → 保護者）。</li>
 * </ol>
 *
 * <p>「最近作った N 件」「役割でまとめて」のような広い消し方は**しない**。</p>
 *
 * <p>SQL は**テスト専用の MyBatis Mapper**（{@link TestSqlMapper}）で流す。JdbcTemplate のように
 * ログを素通りさせない（既存の SQL ログの決まりをテストでも守る）。**平文パスワード・合言葉・
 * セッションの値はログに出さない**。</p>
 */
public final class ClassroomTestData {

    private final TestSqlMapper sql;
    /** パスワードのハッシュ化（本番と同じ encoder を使う）。 */
    private final PasswordEncoder passwordEncoder;
    /**
     * 検証データの**通し番号**（同じ実行の中で必ず違う値にする）。
     *
     * <p>`System.nanoTime()` は分解能が足りず、同じミリ秒に作った 2 つが**同じログインID**に
     * なり得る（並行・連続で作ると一意キーに当たる）。連番と組み合わせて必ず分ける。</p>
     */
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();
    /** この検証が作ったものの ID（**控えるだけ**。消すのは呼び側のロールバック）。 */
    private final List<String> created = new ArrayList<>();

    public ClassroomTestData(TestSqlMapper sql, PasswordEncoder passwordEncoder) {
        this.sql = sql;
        this.passwordEncoder = passwordEncoder;
    }

    /** 検証用の生徒（ログインできる）と、その保護者。 */
    public record Student(long accountId, long guardianId, String loginId, String password) {
    }

    /** 検証用の授業。 */
    public record Lesson(long recordId) {
    }

    /** 検証用のまとめ。 */
    public record Note(long noteId, long recordId, String kind, String status) {
    }

    /**
     * 検証用の生徒を作る（**毎回新しいログインID**）。
     *
     * @param tag 何のテストかがログで分かる短い印
     */
    public Student createStudent(String tag) {
        long unique = System.nanoTime() * 1_000 + SEQUENCE.incrementAndGet();
        String password = "It-" + unique + "-Pw";   // 毎回違う（既存の値を使い回さない）
        String guardianLogin = "it-" + tag + "-guardian-" + unique + "@example.com";
        String studentLogin = "it-" + tag + "-student-" + unique + "@example.com";

        long guardianId = insert("""
                INSERT INTO public."ACC_アカウント"
                    ("ログインID", "パスワードハッシュ", "アカウント種別", "状態",
                     "姓", "名", "有効期限")
                VALUES ('%s', '%s', 'GUARDIAN', '1', '検証', '保護者', CURRENT_DATE + 365)
                RETURNING "アカウントID"
                """.formatted(guardianLogin, passwordEncoder.encode(password)));
        long studentId = insert("""
                INSERT INTO public."ACC_アカウント"
                    ("ログインID", "パスワードハッシュ", "アカウント種別", "状態",
                     "姓", "名", "姓かな", "名かな", "学年", "有効期限", "保護者ID")
                VALUES ('%s', '%s', 'STUDENT', '1', '検証', '利用者', 'けんしょう', 'りようしゃ',
                        '中学1年生', CURRENT_DATE + 365, %d)
                RETURNING "アカウントID"
                """.formatted(studentLogin, passwordEncoder.encode(password), guardianId));

        created.add("acc:" + studentId);
        created.add("acc:" + guardianId);
        return new Student(studentId, guardianId, studentLogin, password);
    }

    /** 検証用の授業を作る（**作った ID を控える**）。 */
    public Lesson createLesson(long ownerAccountId) {
        long recordId = insert("""
                INSERT INTO public."CR_授業記録情報"
                    ("授業記録番号", "登録者アカウントID", "学生ID", "状態", "バージョン", "登録元コード")
                VALUES ('IT-%d', %d, %d, 'STOPPED', 1, 'APP')
                RETURNING "授業記録ID"
                """.formatted(System.nanoTime() * 1_000 + SEQUENCE.incrementAndGet(),
                ownerAccountId, ownerAccountId));
        created.add("record:" + recordId);
        return new Lesson(recordId);
    }

    /** 検証用のまとめを作る（`FINAL` / `PHASE`）。 */
    public Note createNote(long recordId, String kind, String status) {
        long noteId = insert("""
                INSERT INTO public."CR_授業ノート情報"
                    ("授業記録ID", "種別", "フェーズ番号", "対象開始連番", "対象終了連番", "生成状態",
                     "エラーコード", "再試行回数", "バージョン", "登録元コード")
                VALUES (%d, '%s', %s, 1, 3, '%s', %s, 0, 1, 'APP')
                RETURNING "授業ノートID"
                """.formatted(recordId, kind, "PHASE".equals(kind) ? "1" : "NULL", status,
                "FAILED".equals(status) ? "'AI_ERROR'" : "NULL"));
        created.add("note:" + noteId);
        return new Note(noteId, recordId, kind, status);
    }

    /** バッチ実行記録を作る（まとめの実行の帰属を確かめる検証で使う）。 */
    public long createExecution(String batchCode, String status, String runId) {
        long executionId = insert("""
                INSERT INTO public."BAT_バッチ実行履歴情報"
                    ("バッチコード", "バッチ種別", "起動種別", "状態", "起動識別子", "開始時刻")
                VALUES ('%s', 'C', 'R', '%s', '%s', CURRENT_TIMESTAMP)
                RETURNING "実行ID"
                """.formatted(batchCode, status, runId));
        created.add("execution:" + executionId);
        return executionId;
    }

    /** まとめを実行記録へ結び付ける（本番の `markGenerating` と同じ形にする）。 */
    public void bindExecution(long noteId, long executionId) {
        sql.execute("UPDATE public.\"CR_授業ノート情報\" SET \"生成実行ID\" = " + executionId
                + " WHERE \"授業ノートID\" = " + noteId);
    }

    /** 検証用のアカウントの**いまのパスワードハッシュ**（既存の値を壊していないかの確認用）。 */
    public String passwordHashOf(long accountId) {
        List<Map<String, Object>> rows = sql.query(
                "SELECT \"パスワードハッシュ\" FROM public.\"ACC_アカウント\" WHERE \"アカウントID\" = "
                        + accountId);
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("パスワードハッシュ"));
    }

    /** そのアカウントがまだ在るか。 */
    public boolean accountExists(long accountId) {
        return !sql.query("SELECT 1 FROM public.\"ACC_アカウント\" WHERE \"アカウントID\" = "
                + accountId).isEmpty();
    }

    /** そのまとめがまだ在るか。 */
    public boolean noteExists(long noteId) {
        return !sql.query("SELECT 1 FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = "
                + noteId).isEmpty();
    }

    /** そのまとめのいまの生成状態（在らなければ null）。 */
    public String noteStatus(long noteId) {
        List<Map<String, Object>> rows = sql.query(
                "SELECT \"生成状態\" FROM public.\"CR_授業ノート情報\" WHERE \"授業ノートID\" = " + noteId);
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("生成状態"));
    }

    /**
     * この検証が作ったものの ID（**控えただけ**。消すのは呼び側のトランザクション）。
     *
     * <p>アカウントの表には「保護者には生徒が 1 人以上」の**遅延トリガ**があり、消す順を工夫しても
     * 途中の状態で引っかかる。検証は 1 つのトランザクションの中で組み立てているので、
     * **ロールバック**で丸ごと戻す方が確実（中間状態を見せず、消し漏れも残らない）。</p>
     */
    public List<String> createdIds() {
        return List.copyOf(created);
    }

    /** `RETURNING` の 1 列を取る。 */    /** `RETURNING` の 1 列を取る。 */
    private long insert(String statement) {
        List<Map<String, Object>> rows = sql.query(statement);
        if (rows.isEmpty() || rows.get(0).isEmpty()) {
            throw new IllegalStateException("検証データを作れませんでした。");
        }
        return Long.parseLong(String.valueOf(rows.get(0).values().iterator().next()));
    }

    private void delete(String statement) {
        sql.execute(statement);
    }
}
