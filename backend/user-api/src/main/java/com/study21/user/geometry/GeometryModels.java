package com.study21.user.geometry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 図形管理（数学勉強＞図形管理）のモデル。
 *
 * <p>2.0 の図形管理（`geometry.jsp`＝一覧・検索、`geometry_draw.jsp`＝GeoGebra で作図）を
 * 2.1 の画面として作り直したもの。データは `GEO_図形情報`（2.0 の `TRN_図形作成情報` から移行済み）。
 * 図形は**家族で共有する教材**（2.0 に持ち主の概念が無かったため）。</p>
 */
public final class GeometryModels {

    private GeometryModels() {
    }

    /** 図形の種類。 */
    public static final String TYPE_GEOMETRY = "geometry";
    public static final String TYPE_FUNCTION = "function";
    public static final List<String> FIGURE_TYPES = List.of(TYPE_GEOMETRY, TYPE_FUNCTION);

    /** 登録区分（demo=初期データ / saved=利用者が作ったもの）。 */
    public static final List<String> KINDS = List.of("demo", "saved");

    /** 状態（論理削除）。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    /** 並び替え。 */
    public static final List<String> SORTS = List.of("updatedDesc", "createdDesc", "titleAsc");

    /** タグの区切り（2.0 と同じ）。 */
    public static final String TAG_SEPARATOR = "|";

    /** 図形名の最大長（2.0 と同じ）。 */
    public static final int TITLE_MAX = 120;
    /** GeoGebra の XML とサムネイル（Base64）の上限。 */
    public static final int CONSTRUCTION_MAX = 2_000_000;
    public static final int THUMBNAIL_MAX = 4_000_000;

    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 100;

    // ---------------------------------------------------------------- 一覧

    /** 一覧に出す 1 件（GeoGebraXML とサムネイルは重いので含めない）。 */
    public record FigureRow(
            long figureId,
            /** 利用者に見せる番号（2.0 の 図形ID。GEO2026… / geometry-demo-1） */
            String figureNo,
            String subject,
            String figureType,
            /** demo / saved */
            String kind,
            String title,
            String memo,
            List<String> tags,
            int displayOrder,
            String status,
            boolean hasThumbnail,
            /** GeoGebraXML の文字数（中身は詳細で返す） */
            int constructionLength,
            int version,
            String createdAt,
            String updatedAt) {
    }

    public record GeometryTotals(long figureCount, long geometryCount, long functionCount, long deletedCount) {
    }

    public record FigureListResult(
            List<FigureRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages,
            GeometryTotals totals) {
    }

    /** 作図画面が使う 1 件（XML とサムネイルつき）。 */
    public record FigureDetail(FigureRow figure, String construction, String thumbnail) {
    }

    /** タグの候補。 */
    public record TagRow(String tag, long count) {
    }

    // ------------------------------------------------------------ 登録・更新

    /** 図形の保存（新規・更新で同じ形。2.0 の `/api/geometry/save` と同じ項目）。 */
    public record FigureSaveRequest(
            @NotBlank(message = "図形名を入力してください。")
            @Size(max = TITLE_MAX, message = "図形名は120文字以内で入力してください。") String title,
            @Size(max = 20, message = "図形の種類の指定が正しくありません。") String figureType,
            String memo,
            List<String> tags,
            /** GeoGebra の作図データ（XML） */
            String construction,
            /** サムネイル（Base64 PNG。無ければ null） */
            String thumbnail,
            /** 楽観的ロック（更新のとき） */
            Integer version) {
    }

    /** 表示順の変更。 */
    public record OrderRequest(int displayOrder, Integer version) {
    }

    // ------------------------------------------------------------------ 返信

    public record FigureMutationResult(FigureRow figure, String message) {
    }

    public record SimpleResult(int count, String message) {
    }
}
