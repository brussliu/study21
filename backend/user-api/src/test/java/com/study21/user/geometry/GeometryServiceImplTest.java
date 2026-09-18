package com.study21.user.geometry;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 図形管理の業務ルール（登録・更新・コピー・削除・一覧・サムネイル）。
 *
 * 2.0 から引き継いだ仕様:
 * ・GeoGebra の作図データ（XML）とサムネイル（Base64 PNG）を 1 行に持つ
 * ・タグは '|' 区切り
 * ・削除は論理削除（状態コード='DELETED'）
 */
class GeometryServiceImplTest {

    private static final long FIGURE_ID = 42L;
    private static final long ACCOUNT_ID = 2L;

    private GeometryMapper geometryMapper;
    private GeometryServiceImpl service;

    @BeforeEach
    void setUp() {
        geometryMapper = mock(GeometryMapper.class);
        service = new GeometryServiceImpl(geometryMapper);
        doAnswer(invocation -> {
            GeometryEntity entity = invocation.getArgument(0);
            entity.setFigureId(FIGURE_ID);
            return 1;
        }).when(geometryMapper).insert(any());
    }

    private UserPrincipal student() {
        return new UserPrincipal(ACCOUNT_ID, "ricky.jingze@gmail.com", "試験 生徒", AccountType.STUDENT);
    }

    private GeometryEntity figure(String no, String title, String tags, int order, String status, int version) {
        GeometryEntity entity = new GeometryEntity();
        entity.setFigureId(FIGURE_ID);
        entity.setFigureNo(no);
        entity.setSubject("数学");
        entity.setFigureType("geometry");
        entity.setKind("saved");
        entity.setTitle(title);
        entity.setTags(tags);
        entity.setConstruction("<construction/>");
        entity.setThumbnail(null);
        entity.setDisplayOrder(order);
        entity.setStatusCode(status);
        entity.setVersion(version);
        return entity;
    }

    private GeometryModels.FigureSaveRequest saveRequest(String title, String figureType, List<String> tags,
                                                         Integer version) {
        return new GeometryModels.FigureSaveRequest(title, figureType, "メモ", tags, "<xml/>", "AAAA", version);
    }

    // ------------------------------------------------------------ 登録・更新

    @Test
    void createsFigureWithGeneratedNoAndOrder() {
        when(geometryMapper.findByNo(anyString())).thenReturn(null);
        when(geometryMapper.nextDisplayOrder()).thenReturn(7);
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", "三角形|部品", 7, "ACTIVE", 1));

        GeometryModels.FigureMutationResult result = service.create(student(),
                saveRequest("  三角形  ", "geometry", List.of("三角形", "部品"), null));

        ArgumentCaptor<GeometryEntity> captor = ArgumentCaptor.forClass(GeometryEntity.class);
        verify(geometryMapper).insert(captor.capture());
        GeometryEntity saved = captor.getValue();
        assertThat(saved.getFigureNo()).matches("GEO\\d{17}\\d{4}");
        assertThat(saved.getTitle()).isEqualTo("三角形");
        assertThat(saved.getTags()).isEqualTo("三角形|部品");
        assertThat(saved.getDisplayOrder()).isEqualTo(7);
        assertThat(saved.getKind()).isEqualTo("saved");
        assertThat(saved.getSubject()).isEqualTo("数学");
        assertThat(saved.getCreatedBy()).isEqualTo(ACCOUNT_ID);
        assertThat(result.message()).contains("保存しました");
    }

    @Test
    void rejectsUnknownFigureType() {
        assertThatThrownBy(() -> service.create(student(),
                saveRequest("三角形", "circle", List.of(), null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("図形の種類");
        verify(geometryMapper, never()).insert(any());
    }

    @Test
    void rejectsTagWithSeparator() {
        assertThatThrownBy(() -> service.create(student(),
                saveRequest("三角形", "geometry", List.of("三角|形"), null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("タグ");
    }

    @Test
    void rejectsTooLargeConstruction() {
        assertThatThrownBy(() -> service.create(student(), new GeometryModels.FigureSaveRequest(
                "三角形", "geometry", null, List.of(), "x".repeat(GeometryModels.CONSTRUCTION_MAX + 1), null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("作図データ");
    }

    @Test
    void updateUsesOptimisticLock() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", null, 1, "ACTIVE", 3));
        when(geometryMapper.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(student(), FIGURE_ID,
                saveRequest("三角形2", "geometry", List.of(), 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("先に更新されました");
    }

    @Test
    void updateKeepsTypeWhenOmitted() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", null, 1, "ACTIVE", 1));
        when(geometryMapper.update(any())).thenReturn(1);

        service.update(student(), FIGURE_ID, saveRequest("三角形2", null, List.of("図形"), 1));

        ArgumentCaptor<GeometryEntity> captor = ArgumentCaptor.forClass(GeometryEntity.class);
        verify(geometryMapper).update(captor.capture());
        assertThat(captor.getValue().getFigureType()).isEqualTo("geometry");
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void copiesFigureWithNewNoAndTitle() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", "三角形", 3, "ACTIVE", 2));
        when(geometryMapper.findByNo(anyString())).thenReturn(null);
        when(geometryMapper.nextDisplayOrder()).thenReturn(9);

        service.duplicate(student(), FIGURE_ID);

        ArgumentCaptor<GeometryEntity> captor = ArgumentCaptor.forClass(GeometryEntity.class);
        verify(geometryMapper).insert(captor.capture());
        GeometryEntity copy = captor.getValue();
        assertThat(copy.getTitle()).isEqualTo("三角形 のコピー");
        assertThat(copy.getFigureNo()).isNotEqualTo("GEO1").matches("GEO\\d+");
        assertThat(copy.getKind()).isEqualTo("saved");
        assertThat(copy.getDisplayOrder()).isEqualTo(9);
        assertThat(copy.getConstruction()).isEqualTo("<construction/>");
    }

    @Test
    void updatesDisplayOrder() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", null, 1, "ACTIVE", 4));
        when(geometryMapper.updateDisplayOrder(eq(FIGURE_ID), eq(5), eq(ACCOUNT_ID), eq(4))).thenReturn(1);

        GeometryModels.FigureMutationResult result = service.updateOrder(student(), FIGURE_ID, 5, null);

        assertThat(result.message()).contains("表示順");
    }

    @Test
    void deleteSoftDeletesFigure() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", null, 1, "ACTIVE", 1));

        GeometryModels.SimpleResult result = service.delete(student(), FIGURE_ID);

        verify(geometryMapper).softDelete(FIGURE_ID, ACCOUNT_ID);
        assertThat(result.message()).contains("削除しました");
    }

    @Test
    void deleteRejectsAlreadyDeletedFigure() {
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(figure("GEO1", "三角形", null, 1, "DELETED", 2));

        assertThatThrownBy(() -> service.delete(student(), FIGURE_ID))
                .isInstanceOf(ValidationException.class);
        verify(geometryMapper, never()).softDelete(anyLong(), any());
    }

    @Test
    void rejectsUnknownFigure() {
        when(geometryMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(999L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(student(), 999L)).isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------- 一覧

    @Test
    void searchValidatesSortAndClampsSize() {
        when(geometryMapper.count(any(), any(), any(), anyBoolean())).thenReturn(1L);
        when(geometryMapper.search(any(), any(), any(), anyBoolean(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(figure("GEO1", "三角形", "三角形|部品", 1, "ACTIVE", 1)));
        GeometryTotalsEntity totals = new GeometryTotalsEntity();
        totals.setFigureCount(96L);
        totals.setGeometryCount(90L);
        totals.setFunctionCount(6L);
        totals.setDeletedCount(6L);
        when(geometryMapper.totals()).thenReturn(totals);

        GeometryModels.FigureListResult result = service.search(null, null, null, "titleAsc", false, 0, 9999);

        assertThat(result.size()).isEqualTo(GeometryModels.MAX_SIZE);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).tags()).containsExactly("三角形", "部品");
        assertThat(result.items().get(0).constructionLength()).isEqualTo("<construction/>".length());
        assertThat(result.items().get(0).hasThumbnail()).isFalse();
        assertThat(result.totals().figureCount()).isEqualTo(96);

        assertThatThrownBy(() -> service.search(null, null, null, "unknown", false, 1, 24))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.search(null, "circle", null, null, false, 1, 24))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void thumbnailDecodesBase64AndAcceptsDataUrl() {
        GeometryEntity entity = figure("GEO1", "三角形", null, 1, "ACTIVE", 1);
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47};
        entity.setThumbnail(Base64.getEncoder().encodeToString(png));
        when(geometryMapper.findById(FIGURE_ID)).thenReturn(entity);

        assertThat(service.thumbnail(FIGURE_ID)).isEqualTo(png);

        entity.setThumbnail("data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        assertThat(service.thumbnail(FIGURE_ID)).isEqualTo(png);

        entity.setThumbnail("");
        assertThat(service.thumbnail(FIGURE_ID)).isNull();
    }

    @Test
    void tagsReturnCounts() {
        GeometryTagEntity tag = new GeometryTagEntity();
        tag.setTag("三角形");
        tag.setTagCount(30L);
        when(geometryMapper.tagSuggestions(false)).thenReturn(List.of(tag));

        assertThat(service.tags(false)).hasSize(1);
        assertThat(service.tags(false).get(0).count()).isEqualTo(30);
    }
}
