package com.study21.user.english;

import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountService;
import com.study21.user.account.AccountType;
import com.study21.user.account.RegisterRequest;
import com.study21.user.account.RegisterResponse;
import com.study21.user.security.UserPrincipal;
import com.study21.user.testing.TestSqlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する英作文の検証。
 *
 * <p><strong>データに依存しない</strong>: このテストが自分でアカウントと作文を作り、
 * 「読める・行動が正しい」ことだけを確かめる（見本データの件数は断言しない）。</p>
 *
 * <p>画像の実体は**一時ディレクトリ**へ置く（`study21.english-essay.storage-root` を差し替える）。
 * テストはロールバックするので DB は汚れない（置いたファイルは一時ディレクトリなので残ってよい）。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class EnglishEssayRepositoryTest {

    /** 画像の置き場（実体はリポジトリの外に置く）。1 回だけ作って使い回す。 */
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("study21.english-essay.storage-root", () -> STORAGE_ROOT.toString());
    }

    @Autowired
    private EnglishEssayService englishEssayService;

    @Autowired
    private AccountService accountService;

    /** 添削履歴（user-api は書かないので、検証ではテストから直接入れる）。 */
    @Autowired
    private TestSqlMapper sql;

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("study21-english-essay-test");
        } catch (IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private static UserPrincipal student(long accountId) {
        return new UserPrincipal(accountId, "e2e-eng@example.com", "検証 生徒", AccountType.STUDENT);
    }

    private static UserPrincipal guardian(long accountId) {
        return new UserPrincipal(accountId, "e2e-eng-parent@example.com", "検証 保護者", AccountType.GUARDIAN);
    }

    /** 検証用のアカウントをこのテストの中で作る（実在のアカウントの作文を汚さない）。 */
    private long createStudentAccountId() {
        return registerFamily().getStudentAccountId();
    }

    /**
     * 保護者＋初期生徒の 2 行を作る（家族スコープの検証に要るのは保護者のアカウントID）。
     *
     * <p>紐付けは登録が作る {@code ACC_アカウント.保護者ID}（生徒 → 保護者）。新しい関係表は作らない。</p>
     */
    private RegisterResponse registerFamily() {
        String email = "e2e-eng-" + System.nanoTime() + "@example.com";
        RegisterRequest request = new RegisterRequest();
        request.setParentEmail(email);
        request.setParentPassword("Parent1234");
        request.setSei("検証");
        request.setMei("保護者");
        request.setSeiKana("けんしょう");
        request.setMeiKana("ほごしゃ");
        request.setGrade("中学1年生");
        request.setStudentEmail("s-" + email);
        request.setStudentPassword("Student1234");
        request.setAgreed(true);
        return accountService.register(request);
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static MockMultipartFile png(String name) throws IOException {
        return new MockMultipartFile("file", name, "image/png", pngBytes());
    }

    private EnglishEssayModels.EssayRow rowOf(long accountId, long essayId) {
        return englishEssayService.search(accountId, null, null, null, null, 1, 100).items().stream()
                .filter(item -> item.essayId() == essayId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("一覧に作文がありません: " + essayId));
    }

    /** 添削履歴を 1 行入れる（働き手 = admin-api の代わり。user-api は参照だけ）。 */
    private void insertGrading(long essayId, int round, String status, Integer score, Integer maxScore,
                               String reportJson) {
        sql.execute("INSERT INTO public.\"ENG_AI添削履歴情報\" (\"英作文ID\", \"回数\", \"状態コード\","
                + " \"英検級\", \"題_日本語\", \"題_中国語\", \"設問文\", \"作文本文\", \"語数\","
                + " \"総合得点\", \"満点\", \"添削結果JSON\", \"登録者アカウントID\")"
                + " VALUES (" + essayId + ", " + round + ", '" + status + "', 'GRADE1', '題', '標題',"
                + " '設問', '本文', 5, " + (score == null ? "NULL" : score) + ","
                + (maxScore == null ? "NULL" : maxScore) + ","
                + (reportJson == null ? "NULL" : "'" + reportJson + "'::jsonb") + ", 2)");
    }

    @Test
    @DisplayName("作文を作り、画像を上げ、読み、更新し、論理削除できる")
    void createsReadsUpdatesAndDeletes() throws IOException {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);

        // ---- 作成（語数は本文から数える） ----
        EnglishEssayModels.EssayDetail created = englishEssayService.create(user,
                new EnglishEssayModels.CreateRequest("GRADE1", "環境問題について", "关于环境问题",
                        "Do you agree with this idea?", "I have a dream today"));

        assertThat(created.essayId()).isPositive();
        assertThat(created.stateCode()).isEqualTo("A");
        assertThat(created.wordCount()).isEqualTo(5);
        assertThat(created.titleZh()).isEqualTo("关于环境问题");
        assertThat(created.images()).isEmpty();
        assertThat(created.gradings()).isEmpty();
        long essayId = created.essayId();

        // ---- 画像を上げる（設問 → 答案の順） ----
        EnglishEssayModels.ImageUploadResult question =
                englishEssayService.uploadImage(user, essayId, png("question.png"), "question", 1);
        EnglishEssayModels.ImageUploadResult answer =
                englishEssayService.uploadImage(user, essayId, png("answer.png"), "answer", 2);

        assertThat(question.order()).isEqualTo(1);
        assertThat(question.category()).isEqualTo("question");
        assertThat(question.originalFileName()).isEqualTo("question.png");
        assertThat(question.mimeType()).isEqualTo("image/png");
        assertThat(answer.order()).isEqualTo(2);

        // ---- 読める（詳細は 表示順 の昇順） ----
        EnglishEssayModels.EssayDetail detail = englishEssayService.detail(accountId, essayId);
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images()).extracting(EnglishEssayModels.ImageRow::category)
                .containsExactly("question", "answer");
        assertThat(detail.images()).extracting(EnglishEssayModels.ImageRow::order)
                .containsExactly(1, 2);
        assertThat(detail.images().get(0).imageId()).isEqualTo(question.imageId());

        // ---- 一覧（画像枚数は LATERAL の FILTER で数える） ----
        EnglishEssayModels.EssayRow row = rowOf(accountId, essayId);
        assertThat(row.imageCount()).isEqualTo(2);
        assertThat(row.questionImageCount()).isEqualTo(1);
        assertThat(row.answerImageCount()).isEqualTo(1);
        assertThat(row.latestGrading()).isNull();
        assertThat(row.createdAt()).isNotBlank();
        assertThat(row.updatedAt()).isNotBlank();

        // ---- 画像の実体が配信できる ----
        EnglishEssayModels.ImageFile file = englishEssayService.image(accountId, essayId, question.imageId());
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(file.path())).isEqualTo(pngBytes());

        // ---- 添削の履歴（参照のみ。日付・レポートはそのまま返す） ----
        insertGrading(essayId, 1, "SUCCEEDED", 28, 32, "{\"score\":28,\"rubric\":[{\"point\":\"内容\"}]}");
        insertGrading(essayId, 2, "FAILED", null, null, null);

        // 一覧の「最新の添削」は 回数 が大きいほう（回数 DESC LIMIT 1）
        EnglishEssayModels.LatestGrading latest = rowOf(accountId, essayId).latestGrading();
        assertThat(latest).isNotNull();
        assertThat(latest.round()).isEqualTo(2);
        assertThat(latest.statusCode()).isEqualTo("FAILED");
        assertThat(latest.score()).isNull();

        EnglishEssayModels.EssayDetail withGradings = englishEssayService.detail(accountId, essayId);
        assertThat(withGradings.gradings()).extracting(EnglishEssayModels.GradingRow::round)
                .containsExactly(1, 2);
        assertThat(withGradings.gradings().get(0).level()).isEqualTo("GRADE1");
        assertThat(withGradings.gradings().get(0).score()).isEqualTo(28);
        assertThat(withGradings.gradings().get(0).maxScore()).isEqualTo(32);
        // レポート（JSONB）は JSON のまま展開される
        assertThat(withGradings.gradings().get(0).report().path("score").asInt()).isEqualTo(28);
        assertThat(withGradings.gradings().get(0).report().path("rubric").get(0).path("point").asText())
                .isEqualTo("内容");
        assertThat(withGradings.gradings().get(1).report()).isNull();
        assertThat(withGradings.gradings().get(1).statusCode()).isEqualTo("FAILED");

        // ---- 更新（語数を数え直し、画像の区分と順を入れ替える） ----
        EnglishEssayModels.EssayDetail updated = englishEssayService.update(user, essayId,
                new EnglishEssayModels.UpdateRequest("PRE1", "新しい題", "新标题", "設問を直した",
                        "one two three four", java.util.List.of(
                                new EnglishEssayModels.ImageRef(answer.imageId(), "question", 1),
                                new EnglishEssayModels.ImageRef(question.imageId(), "answer", 2))));

        assertThat(updated.level()).isEqualTo("PRE1");
        assertThat(updated.title()).isEqualTo("新しい題");
        assertThat(updated.wordCount()).isEqualTo(4);
        // 表示順 1 が「元 answer」の行（区分と順が入れ替わった）
        assertThat(updated.images()).extracting(EnglishEssayModels.ImageRow::imageId)
                .containsExactly(answer.imageId(), question.imageId());
        assertThat(updated.images()).extracting(EnglishEssayModels.ImageRow::category)
                .containsExactly("question", "answer");
        // 添削の履歴は更新でも消えない（写しを持っている）
        assertThat(updated.gradings()).hasSize(2);

        // ---- 更新（要求に含めなかった画像は消える） ----
        EnglishEssayModels.EssayDetail reduced = englishEssayService.update(user, essayId,
                new EnglishEssayModels.UpdateRequest("PRE1", "新しい題", "新标题", "設問を直した",
                        "one two three four",
                        java.util.List.of(new EnglishEssayModels.ImageRef(answer.imageId(), "question", 1))));

        assertThat(reduced.images()).hasSize(1);
        assertThat(reduced.images().get(0).imageId()).isEqualTo(answer.imageId());
        // 消えた画像の実体は配信できない（404）
        assertThatThrownBy(() -> englishEssayService.image(accountId, essayId, question.imageId()))
                .isInstanceOf(NotFoundException.class);

        // ---- 論理削除（一覧から消える。行と履歴は残る） ----
        englishEssayService.delete(user, essayId);

        assertThat(englishEssayService.search(accountId, null, null, null, null, 1, 100).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).doesNotContain(essayId);
        // 削除済みは「見えない」＝詳細も 404（行と添削の履歴は DB に残る）
        assertThatThrownBy(() -> englishEssayService.detail(accountId, essayId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("画像の OCR の生の結果を保存し、送らなかった欄の既存値は残す（0 と 100 の境界も通る）")
    void storesImageRecognition() throws IOException {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        long essayId = englishEssayService.create(user, new EnglishEssayModels.CreateRequest(
                "GRADE1", "OCR の生の結果", null, "Do you agree with this idea?",
                "I have a dream today")).essayId();

        // ---- 画像 1 枚を OCR の生の結果つきで上げる（設計 §2 の 認識テキスト / 認識信頼度） ----
        long imageId = englishEssayService.uploadImage(user, essayId, png("question.png"), "question", 1,
                "Do you agree with this idea?", 93).imageId();

        EnglishEssayModels.ImageRow uploaded = englishEssayService.detail(accountId, essayId).images().get(0);
        assertThat(uploaded.imageId()).isEqualTo(imageId);
        assertThat(uploaded.recognizedText()).isEqualTo("Do you agree with this idea?");
        assertThat(uploaded.confidence()).isEqualTo(93);

        // ---- 更新で認識テキストだけ送る: 信頼度は**消えない**（省略した欄は既存値のまま） ----
        englishEssayService.update(user, essayId, new EnglishEssayModels.UpdateRequest("GRADE1", "題",
                null, "設問", "one two three", java.util.List.of(
                        new EnglishEssayModels.ImageRef(imageId, "question", 1, "直した認識テキスト", null))));
        EnglishEssayModels.ImageRow textOnly = englishEssayService.detail(accountId, essayId).images().get(0);
        assertThat(textOnly.recognizedText()).isEqualTo("直した認識テキスト");
        assertThat(textOnly.confidence()).isEqualTo(93);

        // ---- 何も送らない: 両方そのまま ----
        englishEssayService.update(user, essayId, new EnglishEssayModels.UpdateRequest("GRADE1", "題",
                null, "設問", "one two three", java.util.List.of(
                        new EnglishEssayModels.ImageRef(imageId, "question", 1))));
        EnglishEssayModels.ImageRow kept = englishEssayService.detail(accountId, essayId).images().get(0);
        assertThat(kept.recognizedText()).isEqualTo("直した認識テキスト");
        assertThat(kept.confidence()).isEqualTo(93);

        // ---- 境界（0 と 100）は通り、指定したら書き換わる ----
        englishEssayService.update(user, essayId, new EnglishEssayModels.UpdateRequest("GRADE1", "題",
                null, "設問", "one two three", java.util.List.of(
                        new EnglishEssayModels.ImageRef(imageId, "question", 1, null, 0))));
        assertThat(englishEssayService.detail(accountId, essayId).images().get(0).confidence()).isZero();
        assertThat(englishEssayService.detail(accountId, essayId).images().get(0).recognizedText())
                .as("信頼度だけ送っても認識テキストは残る").isEqualTo("直した認識テキスト");

        englishEssayService.update(user, essayId, new EnglishEssayModels.UpdateRequest("GRADE1", "題",
                null, "設問", "one two three", java.util.List.of(
                        new EnglishEssayModels.ImageRef(imageId, "question", 1, null, 100))));
        assertThat(englishEssayService.detail(accountId, essayId).images().get(0).confidence()).isEqualTo(100);
    }

    @Test
    @DisplayName("他人の作文は 見えない・更新できない・消せない・画像も取れない（すべて 404）")
    void hidesOtherAccountsEssays() throws IOException {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        long otherAccountId = createStudentAccountId();
        UserPrincipal other = student(otherAccountId);

        long essayId = englishEssayService.create(user, new EnglishEssayModels.CreateRequest(
                "GRADE2", "他人には見えない題", null, null, "secret text")).essayId();
        long imageId = englishEssayService.uploadImage(user, essayId, png("secret.png"), "answer", 1).imageId();

        assertThatThrownBy(() -> englishEssayService.detail(otherAccountId, essayId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.image(otherAccountId, essayId, imageId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.update(other, essayId,
                new EnglishEssayModels.UpdateRequest("GRADE2", "乗っ取り", null, null, "x", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.delete(other, essayId))
                .isInstanceOf(NotFoundException.class);

        // 本人からは見える（他人の操作で消えていない）
        assertThat(englishEssayService.detail(accountId, essayId).stateCode()).isEqualTo("A");
    }

    @Test
    @DisplayName("一覧は キーワード・級・期間で絞れ、ページで切れる（自分の分だけ）")
    void listsWithFilters() {
        long accountId = createStudentAccountId();
        UserPrincipal user = student(accountId);
        String today = LocalDate.now().toString();

        long first = englishEssayService.create(user, new EnglishEssayModels.CreateRequest(
                "GRADE1", "環境問題について", null, null, "I like clean air")).essayId();
        long second = englishEssayService.create(user, new EnglishEssayModels.CreateRequest(
                "GRADE2", "好きな食べ物", "喜欢的食物", null, "I like sushi")).essayId();

        // 全体（このアカウントの 2 件）
        EnglishEssayModels.EssayListResult all =
                englishEssayService.search(accountId, null, null, null, null, 1, 20);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).extracting(EnglishEssayModels.EssayRow::essayId)
                .containsExactlyInAnyOrder(first, second);
        // 並びは 登録日時 DESC（2 件目が先）
        assertThat(all.items().get(0).essayId()).isEqualTo(second);

        // キーワード（題・中国語の題・本文を横断）
        assertThat(englishEssayService.search(accountId, "環境", null, null, null, 1, 20).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(first);
        assertThat(englishEssayService.search(accountId, "sushi", null, null, null, 1, 20).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(second);
        assertThat(englishEssayService.search(accountId, "喜欢的", null, null, null, 1, 20).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(second);

        // 級
        assertThat(englishEssayService.search(accountId, null, "GRADE2", null, null, 1, 20).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(second);

        // 期間（今日は入る。昨日までなら入らない）
        assertThat(englishEssayService.search(accountId, null, null, today, today, 1, 20).total()).isEqualTo(2);
        String yesterday = LocalDate.now().minusDays(1).toString();
        assertThat(englishEssayService.search(accountId, null, null, null, yesterday, 1, 20).total()).isZero();

        // ページ（1 件ずつ）
        EnglishEssayModels.EssayListResult page =
                englishEssayService.search(accountId, null, null, null, null, 1, 1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(englishEssayService.search(accountId, null, null, null, null, 2, 1).items().get(0).essayId())
                .isEqualTo(first);
    }

    /* ---------------------------------------------------------------- 家族（保護者）のスコープ */

    @Test
    @DisplayName("保護者は 自分の子どもの作文を 一覧・詳細・画像で見られる（誰の作文かも分かる）")
    void guardianReadsTheChildsEssays() throws IOException {
        RegisterResponse family = registerFamily();
        long guardianId = family.getGuardianAccountId();
        long childId = family.getStudentAccountId();
        UserPrincipal child = student(childId);

        long essayId = englishEssayService.create(child, new EnglishEssayModels.CreateRequest(
                "GRADE1", "子どもの作文", null, "Do you agree?", "I have a dream today")).essayId();
        long imageId = englishEssayService.uploadImage(child, essayId, png("child.png"), "answer", 1).imageId();

        // ---- 一覧に出る（自分の作文と同じ見え方） ----
        EnglishEssayModels.EssayListResult list =
                englishEssayService.search(guardianId, null, null, null, null, 1, 100);
        assertThat(list.items()).extracting(EnglishEssayModels.EssayRow::essayId).contains(essayId);
        EnglishEssayModels.EssayRow row = list.items().stream()
                .filter(item -> item.essayId() == essayId).findFirst().orElseThrow();
        assertThat(row.imageCount()).isEqualTo(1);
        // 誰の作文かが分かる（保護者の画面で「子どもの作文」と出せる）
        assertThat(row.ownerAccountId()).isEqualTo(childId);
        assertThat(row.ownerName()).contains("検証");

        // ---- 詳細（画像・添削つき） ----
        EnglishEssayModels.EssayDetail detail = englishEssayService.detail(guardianId, essayId);
        assertThat(detail.title()).isEqualTo("子どもの作文");
        assertThat(detail.images()).extracting(EnglishEssayModels.ImageRow::imageId).containsExactly(imageId);
        assertThat(detail.ownerAccountId()).isEqualTo(childId);
        assertThat(detail.ownerName()).contains("検証");

        // ---- 画像の実体 ----
        EnglishEssayModels.ImageFile file = englishEssayService.image(guardianId, essayId, imageId);
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(file.path())).isEqualTo(pngBytes());

        // ---- 本人（子ども）の見え方は変わらない ----
        assertThat(englishEssayService.detail(childId, essayId).ownerAccountId()).isEqualTo(childId);
    }

    @Test
    @DisplayName("保護者は 他人の家庭の子の作文を見られない（一覧に出ない・詳細と画像は 404）")
    void guardianCannotReadAnotherFamily() throws IOException {
        RegisterResponse family = registerFamily();
        long guardianId = family.getGuardianAccountId();
        UserPrincipal child = student(family.getStudentAccountId());

        RegisterResponse otherFamily = registerFamily();
        UserPrincipal otherChild = student(otherFamily.getStudentAccountId());

        long essayId = englishEssayService.create(otherChild, new EnglishEssayModels.CreateRequest(
                "GRADE2", "他人の家庭の作文", null, null, "secret text")).essayId();
        long imageId = englishEssayService
                .uploadImage(otherChild, essayId, png("secret.png"), "answer", 1).imageId();

        assertThat(englishEssayService.search(guardianId, null, null, null, null, 1, 100).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).doesNotContain(essayId);
        assertThatThrownBy(() -> englishEssayService.detail(guardianId, essayId))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.image(guardianId, essayId, imageId))
                .isInstanceOf(NotFoundException.class);
        // 自分の子の作文は見える（家族の取り違えが無い）
        long ownEssayId = englishEssayService.create(child, new EnglishEssayModels.CreateRequest(
                "GRADE1", "自分の子の作文", null, null, "my child text")).essayId();
        assertThat(englishEssayService.search(guardianId, null, null, null, null, 1, 100).items())
                .extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(ownEssayId);
    }

    @Test
    @DisplayName("生徒は 自分の作文だけ（他の家庭の子の作文は 404）")
    void studentReadsOnlyOwnEssays() throws IOException {
        RegisterResponse family = registerFamily();
        UserPrincipal child = student(family.getStudentAccountId());

        RegisterResponse otherFamily = registerFamily();
        UserPrincipal otherChild = student(otherFamily.getStudentAccountId());

        long mine = englishEssayService.create(child, new EnglishEssayModels.CreateRequest(
                "GRADE1", "自分の作文", null, null, "mine")).essayId();
        long theirs = englishEssayService.create(otherChild, new EnglishEssayModels.CreateRequest(
                "GRADE2", "他人の作文", null, null, "theirs")).essayId();
        long theirImageId = englishEssayService
                .uploadImage(otherChild, theirs, png("theirs.png"), "answer", 1).imageId();

        assertThat(englishEssayService.search(family.getStudentAccountId(), null, null, null, null, 1, 100)
                .items()).extracting(EnglishEssayModels.EssayRow::essayId).containsExactly(mine);
        assertThatThrownBy(() -> englishEssayService.detail(family.getStudentAccountId(), theirs))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.image(family.getStudentAccountId(), theirs, theirImageId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("保護者は 子どもの作文に 受付・更新・削除・画像アップができない（閲覧のみ。すべて 404）")
    void guardianCannotWriteTheChildsEssays() throws IOException {
        RegisterResponse family = registerFamily();
        long guardianId = family.getGuardianAccountId();
        UserPrincipal child = student(family.getStudentAccountId());
        UserPrincipal parent = guardian(guardianId);

        long essayId = englishEssayService.create(child, new EnglishEssayModels.CreateRequest(
                "GRADE1", "子どもの作文", null, null, "I have a dream today")).essayId();

        // 更新・削除（代理で直さない）
        assertThatThrownBy(() -> englishEssayService.update(parent, essayId,
                new EnglishEssayModels.UpdateRequest("GRADE1", "乗っ取り", null, null, "x", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> englishEssayService.delete(parent, essayId))
                .isInstanceOf(NotFoundException.class);
        // 画像アップ（子ども名義で上げさせない）
        assertThatThrownBy(() -> englishEssayService.uploadImage(parent, essayId, png("proxy.png"), "answer", 1))
                .isInstanceOf(NotFoundException.class);
        // 添削の受付（代理で AI 費用を使わせない）
        assertThatThrownBy(() -> englishEssayService.acceptGrading(parent, essayId, null))
                .isInstanceOf(NotFoundException.class);

        // 子どもの作文は変わっていない（見ることはできる）
        EnglishEssayModels.EssayDetail detail = englishEssayService.detail(guardianId, essayId);
        assertThat(detail.stateCode()).isEqualTo("A");
        assertThat(detail.title()).isEqualTo("子どもの作文");
        assertThat(detail.images()).isEmpty();
    }
}
