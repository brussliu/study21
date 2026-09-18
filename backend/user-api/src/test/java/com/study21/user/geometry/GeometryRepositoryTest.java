package com.study21.user.geometry;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountService;
import com.study21.user.account.AccountType;
import com.study21.user.account.RegisterRequest;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する図形管理の検証。
 *
 * ・2.0（study2 DB の TRN_図形作成情報）から移行した図形が読めること（GeoGebraXML とサムネイル含む）
 * ・保存 → 更新（楽観的ロック）→ コピー → 表示順 → 論理削除 まで通ること
 * ・タグの候補が取れること
 * テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class GeometryRepositoryTest {

    @Autowired
    private GeometryService geometryService;

    @Autowired
    private AccountService accountService;

    /** 検証用のアカウントをこのテストの中で作る（実在のアカウントを汚さない）。 */
    private UserPrincipal createStudent() {
        String email = "e2e-geo-test-" + System.nanoTime() + "@example.com";
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
        long accountId = accountService.register(request).getStudentAccountId();
        return new UserPrincipal(accountId, "s-" + email, "検証 生徒", AccountType.STUDENT);
    }

    // ---------------------------------------------------------- 移行データ

    @Test
    void migratedFiguresAreReadable() {
        GeometryModels.FigureListResult result = geometryService.search(null, null, null, "updatedDesc", false, 1, 100);

        assertThat(result.totalElements()).isGreaterThan(0L);
        assertThat(result.totals().figureCount()).isEqualTo(result.totalElements());
        assertThat(result.totals().geometryCount() + result.totals().functionCount())
                .isEqualTo(result.totals().figureCount());
        // 2.0 で使われていた図形は「幾何図形」が多い
        assertThat(result.totals().geometryCount()).isGreaterThan(0L);
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.figureId()).isGreaterThan(0L);
            assertThat(item.figureNo()).startsWith("GEO");
            assertThat(item.status()).isEqualTo("ACTIVE");
            assertThat(item.version()).isGreaterThanOrEqualTo(1);
            assertThat(item.constructionLength()).isGreaterThan(0);
        });
    }

    @Test
    void migratedFiguresKeepConstructionAndThumbnail() {
        GeometryModels.FigureListResult list = geometryService.search(null, null, null, "updatedDesc", false, 1, 100);

        List<GeometryModels.FigureRow> withThumbnail = list.items().stream()
                .filter(GeometryModels.FigureRow::hasThumbnail)
                .toList();
        assertThat(withThumbnail).isNotEmpty();

        GeometryModels.FigureDetail detail = geometryService.detail(withThumbnail.get(0).figureId());
        assertThat(detail.construction()).contains("<construction");
        assertThat(detail.figure().constructionLength()).isEqualTo(detail.construction().length());

        byte[] png = geometryService.thumbnail(detail.figure().figureId());
        assertThat(png).isNotNull();
        // PNG のシグネチャ（89 50 4E 47）
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 0x50);
        assertThat(new String(png, 1, 3, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PNG");
    }

    @Test
    void keywordAndTypeFilterNarrowTheResult() {
        GeometryModels.FigureListResult all = geometryService.search(null, null, null, "updatedDesc", false, 1, 100);
        GeometryModels.FigureRow sample = all.items().get(0);

        GeometryModels.FigureListResult byKeyword =
                geometryService.search(sample.title(), null, null, "updatedDesc", false, 1, 100);
        assertThat(byKeyword.items()).extracting(GeometryModels.FigureRow::figureId).contains(sample.figureId());

        GeometryModels.FigureListResult byType =
                geometryService.search(null, sample.figureType(), null, "updatedDesc", false, 1, 100);
        assertThat(byType.items()).allSatisfy(item -> assertThat(item.figureType()).isEqualTo(sample.figureType()));
    }

    @Test
    void tagSuggestionsComeFromMigratedTags() {
        GeometryModels.FigureListResult all = geometryService.search(null, null, null, "updatedDesc", false, 1, 100);
        String tag = all.items().stream()
                .flatMap(item -> item.tags().stream())
                .findFirst()
                .orElse(null);
        assertThat(tag).isNotNull();

        assertThat(geometryService.tags(false)).extracting(GeometryModels.TagRow::tag).contains(tag);

        GeometryModels.FigureListResult byTag = geometryService.search(null, null, tag, "updatedDesc", false, 1, 100);
        assertThat(byTag.items()).isNotEmpty();
        assertThat(byTag.items()).allSatisfy(item -> assertThat(item.tags()).contains(tag));
    }

    @Test
    void sortOptionsAreSupported() {
        for (String sort : GeometryModels.SORTS) {
            assertThat(geometryService.search(null, null, null, sort, false, 1, 5).items()).isNotEmpty();
        }
    }

    // ------------------------------------------------------ 保存 → 削除

    @Test
    void createUpdateCopyReorderAndDelete() {
        UserPrincipal student = createStudent();

        GeometryModels.FigureMutationResult created = geometryService.create(student,
                new GeometryModels.FigureSaveRequest("検証用の三角形", "geometry", "リポジトリテスト",
                        List.of("検証", "三角形"), "<construction><element/></construction>",
                        Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}),
                        null));
        long figureId = created.figure().figureId();
        assertThat(created.figure().title()).isEqualTo("検証用の三角形");
        assertThat(created.figure().tags()).containsExactly("検証", "三角形");
        assertThat(created.figure().hasThumbnail()).isTrue();
        assertThat(created.figure().constructionLength()).isEqualTo("<construction><element/></construction>".length());

        // 更新（楽観的ロック）
        GeometryModels.FigureMutationResult updated = geometryService.update(student, figureId,
                new GeometryModels.FigureSaveRequest("検証用の三角形（更新）", "geometry", "メモ更新",
                        List.of("検証"), "<construction><element/></construction>", null,
                        created.figure().version()));
        assertThat(updated.figure().title()).isEqualTo("検証用の三角形（更新）");
        assertThat(updated.figure().tags()).containsExactly("検証");
        assertThat(updated.figure().version()).isGreaterThan(created.figure().version());

        // 古いバージョンで更新すると競合
        assertThatThrownBy(() -> geometryService.update(student, figureId,
                new GeometryModels.FigureSaveRequest("競合", "geometry", null, List.of(),
                        "<construction/>", null, created.figure().version())))
                .isInstanceOf(ConflictException.class);

        // コピー
        GeometryModels.FigureMutationResult copied = geometryService.duplicate(student, figureId);
        assertThat(copied.figure().figureId()).isNotEqualTo(figureId);
        assertThat(copied.figure().title()).isEqualTo("検証用の三角形（更新） のコピー");
        assertThat(copied.figure().kind()).isEqualTo("saved");
        assertThat(copied.figure().constructionLength()).isEqualTo(updated.figure().constructionLength());

        // 表示順
        GeometryModels.FigureMutationResult reordered = geometryService.updateOrder(student, copied.figure().figureId(),
                1, copied.figure().version());
        assertThat(reordered.figure().displayOrder()).isEqualTo(1);

        // コピーを削除（論理削除）
        assertThat(geometryService.delete(student, copied.figure().figureId()).message()).contains("削除しました");
        assertThat(geometryService.search("検証用の三角形（更新） のコピー", null, null, "updatedDesc", false, 1, 100)
                .items()).isEmpty();
        assertThat(geometryService.search("検証用の三角形（更新） のコピー", null, null, "updatedDesc", true, 1, 100)
                .items()).hasSize(1)
                .allSatisfy(item -> assertThat(item.status()).isEqualTo("DELETED"));
        assertThatThrownBy(() -> geometryService.delete(student, copied.figure().figureId()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void demoFiguresKeepTheirKindAfterMigration() {
        GeometryModels.FigureListResult list = geometryService.search(null, null, null, "createdDesc", false, 1, 100);
        assertThat(list.items()).allSatisfy(item ->
                assertThat(item.kind()).isIn(GeometryModels.KINDS));
    }
}
