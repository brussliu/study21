package com.study21.user.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 英作文（作文データの層）の業務ルール。Mapper は mock し、**画像の置き場だけ本物**を使う
 * （大きさ・形式の拒否は Storage の中身なので、mock すると何も検証できない）。
 *
 * <p>確かめる公開の振る舞い:</p>
 * <ol>
 *   <li>一覧・詳細は**自分の作文だけ**（他人のは 404 で存在も隠す）</li>
 *   <li>作成で**語数**が入る（空なら 0）</li>
 *   <li>更新で**要求に無い画像が消え**、含まれる画像の**区分と表示順が変わる**</li>
 *   <li>論理削除（状態コード 'X'）</li>
 *   <li>枚数・大きさ・形式の上限を**日本語の理由**で断る</li>
 * </ol>
 */
class EnglishEssayServiceTest {

    private static final long ACCOUNT_ID = 2L;
    private static final long OTHER_ACCOUNT_ID = 99L;
    private static final long ESSAY_ID = 501L;
    private static final long IMAGE_ID_1 = 9001L;
    private static final long IMAGE_ID_2 = 9002L;

    @TempDir
    Path tempDir;

    private EnglishEssayMapper mapper;
    private EnglishEssaySettingMapper settingMapper;
    private EnglishEssayAiAdminClient aiAdminClient;
    private EnglishEssayServiceImpl service;
    private EnglishEssayStorage storage;

    @BeforeEach
    void setUp() {
        mapper = mock(EnglishEssayMapper.class);
        settingMapper = mock(EnglishEssaySettingMapper.class);
        aiAdminClient = mock(EnglishEssayAiAdminClient.class);
        storage = new EnglishEssayStorage(tempDir.resolve("english-essay-root").toString());
        EnglishEssaySettings settings = new EnglishEssaySettings(settingMapper);
        service = new EnglishEssayServiceImpl(mapper, storage, settings, aiAdminClient, new ObjectMapper());

        // 設定（DB の ENGLISH_ESSAY ページ）: 2 枚・1MB。上限の検証を小さく済ませるため
        when(settingMapper.findByKeys(anyString(), anyList())).thenReturn(List.of(
                setting("ENGLISH_ESSAY_MAX_IMAGES", "2"),
                setting("ENGLISH_ESSAY_MAX_IMAGE_MB", "1")));

        // insert は採番した主キーを書き戻す（useGeneratedKeys）
        doAnswer(invocation -> {
            EnglishEssayEntity entity = invocation.getArgument(0);
            entity.setEssayId(ESSAY_ID);
            return 1;
        }).when(mapper).insert(any());
        doAnswer(invocation -> {
            EnglishEssayImageEntity entity = invocation.getArgument(0);
            entity.setImageId(IMAGE_ID_1);
            return 1;
        }).when(mapper).insertImage(any());
    }

    private static UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "s-e2e@example.com", "検証 生徒", AccountType.STUDENT);
    }

    private static EnglishEssaySettingEntity setting(String key, String value) {
        EnglishEssaySettingEntity entity = new EnglishEssaySettingEntity();
        entity.setSettingKey(key);
        entity.setSettingValue(value);
        return entity;
    }

    private static EnglishEssayEntity essay() {
        EnglishEssayEntity entity = new EnglishEssayEntity();
        entity.setEssayId(ESSAY_ID);
        entity.setAccountId(ACCOUNT_ID);
        entity.setLevel("GRADE1");
        entity.setTitle("環境問題について");
        entity.setTitleZh("关于环境问题");
        entity.setQuestionText("Do you think ...?");
        entity.setEssayText("I have a dream today");
        entity.setWordCount(5);
        entity.setStateCode("A");
        entity.setVersion(1);
        return entity;
    }

    private static EnglishEssayImageEntity image(long imageId, int order, String category) {
        EnglishEssayImageEntity entity = new EnglishEssayImageEntity();
        entity.setImageId(imageId);
        entity.setEssayId(ESSAY_ID);
        entity.setOrderNo(order);
        entity.setCategory(category);
        entity.setOriginalFileName("photo.png");
        entity.setStoredFileName("abc" + imageId + ".png");
        entity.setRelativePath("english-essay/" + ACCOUNT_ID + "/202609");
        entity.setMimeType("image/png");
        entity.setFileSize(1234L);
        entity.setCreatedBy(ACCOUNT_ID);
        return entity;
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

    /* ---------------------------------------------------------------- 一覧 */

    @Test
    @DisplayName("一覧は ログイン中のアカウントの作文だけを引く（ページ・件数も返す）")
    void searchesOnlyOwnEssays() {
        EnglishEssayListEntity row = new EnglishEssayListEntity();
        row.setEssayId(ESSAY_ID);
        row.setLevel("GRADE1");
        row.setTitle("環境問題について");
        row.setWordCount(5);
        row.setImageCount(2);
        row.setQuestionImageCount(1);
        row.setAnswerImageCount(1);
        row.setLatestGradingId(7001L);
        row.setLatestGradingRound(2);
        row.setLatestGradingStatus("SUCCEEDED");
        row.setLatestGradingScore(28);
        row.setLatestGradingMaxScore(32);
        when(mapper.count(eq(ACCOUNT_ID), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.search(eq(ACCOUNT_ID), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        EnglishEssayModels.EssayListResult result = service.search(ACCOUNT_ID, null, null, null, null, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).imageCount()).isEqualTo(2);
        assertThat(result.items().get(0).latestGrading().round()).isEqualTo(2);
        assertThat(result.items().get(0).latestGrading().score()).isEqualTo(28);
        // 一覧は本人の分だけ（アカウントを SQL に渡す）
        verify(mapper).search(eq(ACCOUNT_ID), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("添削がまだ無い作文の latestGrading は null（画面は「未添削」を出せる）")
    void returnsNullLatestGradingWhenNoGrading() {
        EnglishEssayListEntity row = new EnglishEssayListEntity();
        row.setEssayId(ESSAY_ID);
        row.setLevel("PRE1");
        row.setTitle("題");
        row.setWordCount(0);
        row.setImageCount(0);
        row.setQuestionImageCount(0);
        row.setAnswerImageCount(0);
        when(mapper.count(anyLong(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.search(anyLong(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of(row));

        EnglishEssayModels.EssayListResult result = service.search(ACCOUNT_ID, null, null, null, null, 1, 20);

        assertThat(result.items().get(0).latestGrading()).isNull();
        assertThat(result.items().get(0).imageCount()).isZero();
    }

    @Test
    @DisplayName("一覧の絞り込み（級・期間）は検査してから SQL へ渡す")
    void validatesListFilters() {
        when(mapper.count(anyLong(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.search(anyLong(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> service.search(ACCOUNT_ID, null, "GRADE3", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("英検級");
        assertThatThrownBy(() -> service.search(ACCOUNT_ID, null, null, "2026/01/01", null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("日付");

        // 正しい指定は通る（ISO に直して渡す）
        service.search(ACCOUNT_ID, "dream", "GRADE2", "2026-01-01", "2026-01-31", 1, 20);
        verify(mapper).search(eq(ACCOUNT_ID), eq("dream"), eq("GRADE2"), eq("2026-01-01"), eq("2026-01-31"),
                anyInt(), anyInt());
    }

    /* ---------------------------------------------------------------- 詳細 */

    @Test
    @DisplayName("詳細: 他人の作文は 404（存在も隠す）")
    void hidesOtherAccountsEssay() {
        when(mapper.findById(ESSAY_ID, OTHER_ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(OTHER_ACCOUNT_ID, ESSAY_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("見つかりません");
    }

    @Test
    @DisplayName("詳細: 画像は 表示順・添削は 回数の昇順で、レポートの JSON をそのまま返す")
    void returnsImagesAndGradings() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(
                image(IMAGE_ID_1, 1, "question"), image(IMAGE_ID_2, 2, "answer")));
        EnglishEssayGradingEntity grading = new EnglishEssayGradingEntity();
        grading.setGradingId(7001L);
        grading.setEssayId(ESSAY_ID);
        grading.setRoundNo(1);
        grading.setStatusCode("SUCCEEDED");
        grading.setLevel("GRADE1");
        grading.setTitleJa("環境問題について");
        grading.setTitleZh("关于环境问题");
        grading.setWordCount(5);
        grading.setScore(28);
        grading.setMaxScore(32);
        grading.setReport("{\"score\":28,\"rubric\":[{\"point\":\"内容\"}]}");
        grading.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        when(mapper.listGradings(ESSAY_ID)).thenReturn(List.of(grading));

        EnglishEssayModels.EssayDetail detail = service.detail(ACCOUNT_ID, ESSAY_ID);

        assertThat(detail.essayId()).isEqualTo(ESSAY_ID);
        assertThat(detail.level()).isEqualTo("GRADE1");
        assertThat(detail.stateCode()).isEqualTo("A");
        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images().get(0).order()).isEqualTo(1);
        assertThat(detail.images().get(0).category()).isEqualTo("question");
        assertThat(detail.images().get(0).mimeType()).isEqualTo("image/png");
        assertThat(detail.gradings()).hasSize(1);
        assertThat(detail.gradings().get(0).round()).isEqualTo(1);
        assertThat(detail.gradings().get(0).score()).isEqualTo(28);
        // レポートは JSON のまま（Jackson が展開する）
        assertThat(detail.gradings().get(0).report().path("score").asInt()).isEqualTo(28);
        assertThat(detail.gradings().get(0).report().path("rubric").get(0).path("point").asText())
                .isEqualTo("内容");
    }

    /* ---------------------------------------------------------------- 家族（保護者）のスコープ */

    @Test
    @DisplayName("家族: 保護者は子どもの作文を読めるが、書き込み（更新・削除・画像・受付）は 404")
    void guardianReadsButCannotWriteTheChildsEssay() {
        long guardianId = 30L;
        long childId = 31L;
        UserPrincipal parent = new UserPrincipal(guardianId, "parent@example.com", "検証 保護者",
                AccountType.GUARDIAN);
        // SQL（findById）が子どもの作文を返す＝保護者から見える
        EnglishEssayEntity childs = essay();
        childs.setAccountId(childId);
        childs.setOwnerAccountId(childId);
        childs.setOwnerName("検証 生徒");
        when(mapper.findById(ESSAY_ID, guardianId)).thenReturn(childs);
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of());
        when(mapper.listGradings(ESSAY_ID)).thenReturn(List.of());

        // 読める（誰の作文かも返す）
        EnglishEssayModels.EssayDetail detail = service.detail(guardianId, ESSAY_ID);
        assertThat(detail.ownerAccountId()).isEqualTo(childId);
        assertThat(detail.ownerName()).isEqualTo("検証 生徒");

        // 書けない（見えても操作は本人だけ。存在を隠して 404）
        assertThatThrownBy(() -> service.update(parent, ESSAY_ID, new EnglishEssayModels.UpdateRequest(
                "GRADE1", "乗っ取り", null, null, "x", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(parent, ESSAY_ID))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.uploadImage(parent, ESSAY_ID, png("proxy.png"), "answer", 1))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.acceptGrading(parent, ESSAY_ID, null))
                .isInstanceOf(NotFoundException.class);

        verify(mapper, never()).update(any());
        verify(mapper, never()).logicalDelete(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertImage(any());
        verify(aiAdminClient, never()).acceptGrading(anyLong(), any());
    }

    /* ---------------------------------------------------------------- 作成 */

    @Test
    @DisplayName("作成: 本文から語数を数えて入れる（空なら 0）")
    void createCountsWords() {
        when(mapper.findById(eq(ESSAY_ID), eq(ACCOUNT_ID))).thenReturn(essay());
        when(mapper.listImages(anyLong())).thenReturn(List.of());

        service.create(student(), new EnglishEssayModels.CreateRequest("GRADE1", "題",
                "標題", "設問", "  I have a big dream today. "));

        ArgumentCaptor<EnglishEssayEntity> captor = ArgumentCaptor.forClass(EnglishEssayEntity.class);
        verify(mapper).insert(captor.capture());
        EnglishEssayEntity inserted = captor.getValue();
        assertThat(inserted.getWordCount()).isEqualTo(6);
        assertThat(inserted.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(inserted.getStateCode()).isEqualTo("A");
        assertThat(inserted.getVersion()).isEqualTo(1);
        assertThat(inserted.getEssayText()).isEqualTo("I have a big dream today.");
    }

    @Test
    @DisplayName("作成: 本文が空でも 0 語で通る")
    void createWithEmptyBodyIsZeroWords() {
        when(mapper.findById(eq(ESSAY_ID), eq(ACCOUNT_ID))).thenReturn(essay());
        when(mapper.listImages(anyLong())).thenReturn(List.of());

        service.create(student(), new EnglishEssayModels.CreateRequest("GRADE1", "題", null, null, "   "));

        ArgumentCaptor<EnglishEssayEntity> captor = ArgumentCaptor.forClass(EnglishEssayEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getWordCount()).isZero();
    }

    @Test
    @DisplayName("作成: 級と題は必須（日本語の理由で断る）")
    void createValidatesLevelAndTitle() {
        assertThatThrownBy(() -> service.create(student(),
                new EnglishEssayModels.CreateRequest("GRADE3", "題", null, null, "text")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("英検級");
        assertThatThrownBy(() -> service.create(student(),
                new EnglishEssayModels.CreateRequest("GRADE1", "   ", null, null, "text")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("題");
        verify(mapper, never()).insert(any());
    }

    /* ---------------------------------------------------------------- 更新 */

    @Test
    @DisplayName("更新: 語数を数え直し、要求に無い画像は消し、含まれる画像の区分と順を変える")
    void updateReordersAndDropsImages() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(
                image(IMAGE_ID_1, 1, "question"), image(IMAGE_ID_2, 2, "answer")));
        when(mapper.update(any())).thenReturn(1);

        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("PRE1", "新しい題",
                null, "設問2", "one two three", List.of(
                        new EnglishEssayModels.ImageRef(IMAGE_ID_2, "question", 1),
                        new EnglishEssayModels.ImageRef(IMAGE_ID_1, "answer", 2))));

        ArgumentCaptor<EnglishEssayEntity> captor = ArgumentCaptor.forClass(EnglishEssayEntity.class);
        verify(mapper).update(captor.capture());
        assertThat(captor.getValue().getWordCount()).isEqualTo(3);
        assertThat(captor.getValue().getLevel()).isEqualTo("PRE1");
        assertThat(captor.getValue().getTitle()).isEqualTo("新しい題");

        // UNIQUE(英作文ID, 表示順) に当たらないよう、先に退避してから消して、最後に確定する
        var order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).shiftImageOrders(ESSAY_ID, EnglishEssayServiceImpl.ORDER_SHIFT);
        order.verify(mapper).deleteImagesNotIn(ESSAY_ID, List.of(IMAGE_ID_2, IMAGE_ID_1));
        order.verify(mapper).updateImagePlacement(ESSAY_ID, IMAGE_ID_2, "question", 1);
        order.verify(mapper).updateImagePlacement(ESSAY_ID, IMAGE_ID_1, "answer", 2);
    }

    @Test
    @DisplayName("更新: 要求から外した画像の行は消える（残す画像だけを渡す）")
    void updateDeletesRemovedImages() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(
                image(IMAGE_ID_1, 1, "question"), image(IMAGE_ID_2, 2, "answer")));
        when(mapper.update(any())).thenReturn(1);

        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one two", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1))));

        verify(mapper).deleteImagesNotIn(ESSAY_ID, List.of(IMAGE_ID_1));
        verify(mapper).updateImagePlacement(ESSAY_ID, IMAGE_ID_1, "question", 1);
        verify(mapper, never()).updateImagePlacement(eq(ESSAY_ID), eq(IMAGE_ID_2), anyString(), anyInt());
    }

    @Test
    @DisplayName("更新: images を送らなければ画像は触らない（[] を送ると全部消える）")
    void updateWithoutImagesLeavesThemAlone() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(image(IMAGE_ID_1, 1, "question")));
        when(mapper.update(any())).thenReturn(1);

        service.update(student(), ESSAY_ID,
                new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null, "one", null));

        verify(mapper, never()).deleteImagesNotIn(anyLong(), anyList());
        verify(mapper, never()).shiftImageOrders(anyLong(), anyInt());

        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one", List.of()));

        // 空の配列は「全部消す」
        verify(mapper).deleteImagesNotIn(ESSAY_ID, List.of());
    }

    @Test
    @DisplayName("更新: 他人の作文は 404")
    void updateHidesOtherAccountsEssay() {
        when(mapper.findById(ESSAY_ID, OTHER_ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.update(new UserPrincipal(OTHER_ACCOUNT_ID, "x", "x",
                AccountType.STUDENT), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one", null)))
                .isInstanceOf(NotFoundException.class);
        verify(mapper, never()).update(any());
    }

    @Test
    @DisplayName("更新: 表示順の重複と、この作文に無い画像は断る")
    void updateValidatesImageRefs() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(
                image(IMAGE_ID_1, 1, "question"), image(IMAGE_ID_2, 2, "answer")));

        assertThatThrownBy(() -> service.update(student(), ESSAY_ID,
                new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null, "one", List.of(
                        new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1),
                        new EnglishEssayModels.ImageRef(IMAGE_ID_2, "answer", 1)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("表示順");

        assertThatThrownBy(() -> service.update(student(), ESSAY_ID,
                new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null, "one", List.of(
                        new EnglishEssayModels.ImageRef(7777L, "question", 1)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像");

        assertThatThrownBy(() -> service.update(student(), ESSAY_ID,
                new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null, "one", List.of(
                        new EnglishEssayModels.ImageRef(IMAGE_ID_1, "other", 1)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像区分");

        verify(mapper, never()).update(any());
    }

    /* ---------------------------------------------------------------- 削除 */

    @Test
    @DisplayName("削除は 論理削除（見えない・更新行が 0 なら 404 で、他人の行は触らない）")
    void deletesLogically() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.logicalDelete(ESSAY_ID, ACCOUNT_ID, ACCOUNT_ID)).thenReturn(1);
        service.delete(student(), ESSAY_ID);
        verify(mapper).logicalDelete(ESSAY_ID, ACCOUNT_ID, ACCOUNT_ID);

        // 見えない（他人・削除済み）ときは行を触らずに 404
        when(mapper.findById(ESSAY_ID, OTHER_ACCOUNT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(new UserPrincipal(OTHER_ACCOUNT_ID, "x", "x",
                AccountType.STUDENT), ESSAY_ID))
                .isInstanceOf(NotFoundException.class);
        verify(mapper, never()).logicalDelete(eq(ESSAY_ID), eq(OTHER_ACCOUNT_ID), eq(OTHER_ACCOUNT_ID));

        // 見えても自分の作文でない（保護者が子どもの作文を消そうとした）ときは 404 で、行は触らない
        long guardianId = 40L;
        EnglishEssayEntity childs = essay();
        when(mapper.findById(ESSAY_ID, guardianId)).thenReturn(childs);
        assertThatThrownBy(() -> service.delete(new UserPrincipal(guardianId, "p@example.com", "保護者",
                AccountType.GUARDIAN), ESSAY_ID))
                .isInstanceOf(NotFoundException.class);
        verify(mapper, never()).logicalDelete(eq(ESSAY_ID), eq(guardianId), eq(guardianId));
    }

    /* ---------------------------------------------------------------- 画像 */

    @Test
    @DisplayName("画像の登録: 実体を保存し、行を作る（原本名・MIME・大きさを返す）")
    void uploadsImage() throws IOException {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(0L);
        when(mapper.countImageOrder(ESSAY_ID, 1)).thenReturn(0L);

        EnglishEssayModels.ImageUploadResult result =
                service.uploadImage(student(), ESSAY_ID, png("写真.png"), "question", 1);

        assertThat(result.imageId()).isEqualTo(IMAGE_ID_1);
        assertThat(result.order()).isEqualTo(1);
        assertThat(result.category()).isEqualTo("question");
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.originalFileName()).isEqualTo("写真.png");
        assertThat(result.fileSize()).isPositive();

        ArgumentCaptor<EnglishEssayImageEntity> captor =
                ArgumentCaptor.forClass(EnglishEssayImageEntity.class);
        verify(mapper).insertImage(captor.capture());
        EnglishEssayImageEntity inserted = captor.getValue();
        assertThat(inserted.getRelativePath())
                .startsWith("english-essay/" + ACCOUNT_ID + "/")
                .matches("english-essay/" + ACCOUNT_ID + "/\\d{6}");
        assertThat(inserted.getStoredFileName()).endsWith(".png");
        assertThat(inserted.getCategory()).isEqualTo("question");
        // 実体が置かれている（相対パスの下に保存ファイル名がある）
        assertThat(Files.isReadable(storage.resolve(inserted.getRelativePath(), inserted.getStoredFileName())))
                .isTrue();
    }

    @Test
    @DisplayName("画像の登録: 枚数の上限を超えたら日本語で断る（1 枚も保存しない）")
    void uploadRejectsTooManyImages() throws IOException {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, png("photo.png"), "question", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2 枚まで");
        verify(mapper, never()).insertImage(any());
    }

    @Test
    @DisplayName("画像の登録: 大きさの上限を超えたら日本語で断る")
    void uploadRejectsTooLargeImage() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(0L);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png",
                new byte[1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, file, "question", 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1 MB");
    }

    @Test
    @DisplayName("画像の登録: PNG/JPEG/WebP 以外と、壊れた画像は断る（WebP は寸法を見ない）")
    void uploadValidatesImageFormat() throws IOException {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(0L);

        // gif は受け付けない（2.0 と同じ 3 形式だけ）
        MockMultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, gif, "question", 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PNG");

        // 中身が画像でないのに .png を名乗る（ImageIO が寸法を読めない）
        MockMultipartFile broken = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, broken, "question", 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("読み込");

        // WebP は JDK がデコードできないので、大きさだけで通す
        MockMultipartFile webp = new MockMultipartFile("file", "a.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        assertThat(service.uploadImage(student(), ESSAY_ID, webp, "question", 1).mimeType())
                .isEqualTo("image/webp");

        // 画像区分は question / answer だけ
        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, png("a.png"), "other", 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("画像区分");
    }

    /* ------------------------------------------- 画像の OCR の生の結果（認識テキスト・信頼度） */

    @Test
    @DisplayName("更新: 画像ごとの OCR の生の結果（認識テキスト・信頼度）を送ったら書く")
    void updateWritesImageRecognition() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(image(IMAGE_ID_1, 1, "question")));
        when(mapper.update(any())).thenReturn(1);

        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one two", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1,
                        "Do you agree with this opinion?", 93))));

        // 区分・表示順の更新のあと、認識の 2 列も書く
        verify(mapper).updateImagePlacement(ESSAY_ID, IMAGE_ID_1, "question", 1);
        verify(mapper).updateImageRecognition(ESSAY_ID, IMAGE_ID_1, "Do you agree with this opinion?", 93);
    }

    @Test
    @DisplayName("更新: OCR の結果を送らなければ既存値を消さない（認識の 2 列に触らない）")
    void updateKeepsRecognitionWhenNotSent() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(image(IMAGE_ID_1, 1, "question")));
        when(mapper.update(any())).thenReturn(1);

        // 区分と表示順だけ（＝OCR の結果を送らない回）
        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one two", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1))));
        // 信頼度だけ送る回（認識テキストは既存値のまま）
        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one two", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1, null, 100))));
        // 空白だけの認識テキストは「指定なし」として扱う（既存値を消さない）
        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one two", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1, "   ", null))));

        verify(mapper).updateImageRecognition(ESSAY_ID, IMAGE_ID_1, null, 100);
        // 何も指定しなかった回（と、空白だけの認識テキストの回）は**認識の 2 列に触らない**
        verify(mapper, never()).updateImageRecognition(anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        verify(mapper, org.mockito.Mockito.times(1))
                .updateImageRecognition(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("更新: 認識信頼度は 0〜100 の外なら 400（1 つも書かない）")
    void updateValidatesConfidence() {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.listImages(ESSAY_ID)).thenReturn(List.of(image(IMAGE_ID_1, 1, "question")));

        for (Integer bad : new Integer[] { -1, 101 }) {
            assertThatThrownBy(() -> service.update(student(), ESSAY_ID,
                    new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null, "one",
                            List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1, "text", bad)))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("認識信頼度");
        }
        // 境界（0 と 100）は通る
        when(mapper.update(any())).thenReturn(1);
        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1, "t", 0))));
        service.update(student(), ESSAY_ID, new EnglishEssayModels.UpdateRequest("GRADE1", "題", null, null,
                "one", List.of(new EnglishEssayModels.ImageRef(IMAGE_ID_1, "question", 1, "t", 100))));

        verify(mapper, never()).updateImageRecognition(eq(ESSAY_ID), eq(IMAGE_ID_1), any(), eq(-1));
        verify(mapper, never()).updateImageRecognition(eq(ESSAY_ID), eq(IMAGE_ID_1), any(), eq(101));
    }

    @Test
    @DisplayName("画像の登録: OCR の生の結果も一緒に書ける（送らなければ NULL のまま）")
    void uploadStoresRecognition() throws IOException {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(0L);
        when(mapper.countImageOrder(ESSAY_ID, 1)).thenReturn(0L);
        when(mapper.countImageOrder(ESSAY_ID, 2)).thenReturn(0L);

        service.uploadImage(student(), ESSAY_ID, png("q.png"), "question", 1, "Do you agree?", 88);
        service.uploadImage(student(), ESSAY_ID, png("a.png"), "answer", 2);

        ArgumentCaptor<EnglishEssayImageEntity> captor =
                ArgumentCaptor.forClass(EnglishEssayImageEntity.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertImage(captor.capture());
        assertThat(captor.getAllValues().get(0).getRecognizedText()).isEqualTo("Do you agree?");
        assertThat(captor.getAllValues().get(0).getConfidence()).isEqualTo(88);
        assertThat(captor.getAllValues().get(1).getRecognizedText()).isNull();
        assertThat(captor.getAllValues().get(1).getConfidence()).isNull();
    }

    @Test
    @DisplayName("画像の登録: 既に使われている表示順は断る（UNIQUE 制約で落とさない）")
    void uploadRejectsUsedOrder() throws IOException {
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.countImages(ESSAY_ID)).thenReturn(1L);
        when(mapper.countImageOrder(ESSAY_ID, 1)).thenReturn(1L);

        assertThatThrownBy(() -> service.uploadImage(student(), ESSAY_ID, png("a.png"), "question", 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("表示順");
    }

    @Test
    @DisplayName("画像の配信: 本人の作文の画像だけ（他人のは 404）")
    void servesOwnImage() throws IOException {
        EnglishEssayImageEntity image = image(IMAGE_ID_1, 1, "question");
        Path stored = storage.resolve(image.getRelativePath(), image.getStoredFileName());
        Files.createDirectories(stored.getParent());
        Files.write(stored, pngBytes());
        when(mapper.findById(ESSAY_ID, ACCOUNT_ID)).thenReturn(essay());
        when(mapper.findImage(ESSAY_ID, IMAGE_ID_1)).thenReturn(image);

        EnglishEssayModels.ImageFile file = service.image(ACCOUNT_ID, ESSAY_ID, IMAGE_ID_1);

        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(file.fileName()).isEqualTo("photo.png");
        assertThat(Files.isReadable(file.path())).isTrue();

        // 他人の作文は 404（画像の行を引く前に弾く）
        when(mapper.findById(ESSAY_ID, OTHER_ACCOUNT_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.image(OTHER_ACCOUNT_ID, ESSAY_ID, IMAGE_ID_1))
                .isInstanceOf(NotFoundException.class);
    }
}
