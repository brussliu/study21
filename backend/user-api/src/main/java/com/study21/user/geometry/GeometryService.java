package com.study21.user.geometry;

import com.study21.user.security.UserPrincipal;

import java.util.List;

/**
 * 図形管理（数学勉強＞図形管理）の業務処理。
 *
 * <p>2.0 の図形管理（GeoGebra で作図した図形の一覧・保存）を 2.1 に移したもの。
 * 図形は**家族で共有する教材**。</p>
 */
public interface GeometryService {

    /** 図形の一覧（検索・並び替え・ページング・サマリ）。 */
    GeometryModels.FigureListResult search(String keyword, String figureType, String tag, String sort,
                                           boolean includeDeleted, int page, int size);

    /** 作図画面が使う 1 件（GeoGebraXML とサムネイルつき）。 */
    GeometryModels.FigureDetail detail(long figureId);

    /** サムネイルの PNG（無ければ null）。 */
    byte[] thumbnail(long figureId);

    /** タグの候補（件数つき）。 */
    List<GeometryModels.TagRow> tags(boolean includeDeleted);

    /** 図形の保存（新規）。 */
    GeometryModels.FigureMutationResult create(UserPrincipal user, GeometryModels.FigureSaveRequest request);

    /**
     * 図形の保存（新規。登録元コードを指定する）。
     *
     * <p>AI 生図（`GEO_AI生図リクエスト情報`）から作った図形は `'AI'` を付ける。
     * `GEO_図形情報` に列は足さない（來源はこの列で表す。設計 §3.3）。</p>
     */
    GeometryModels.FigureMutationResult create(UserPrincipal user, GeometryModels.FigureSaveRequest request,
                                               String sourceCode);

    /** 図形の保存（更新・楽観的ロック）。 */
    GeometryModels.FigureMutationResult update(UserPrincipal user, long figureId,
                                               GeometryModels.FigureSaveRequest request);

    /** 図形のコピー。 */
    GeometryModels.FigureMutationResult duplicate(UserPrincipal user, long figureId);

    /** 表示順の変更。 */
    GeometryModels.FigureMutationResult updateOrder(UserPrincipal user, long figureId, int displayOrder,
                                                    Integer version);

    /** 図形の削除（論理削除）。 */
    GeometryModels.SimpleResult delete(UserPrincipal user, long figureId);
}
