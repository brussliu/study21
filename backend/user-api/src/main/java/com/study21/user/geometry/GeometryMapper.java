package com.study21.user.geometry;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GEO_図形情報（図形管理）の Mapper。
 *
 * <p>図形は家族で共有する教材。GeoGebraXML とサムネイルは重いので、
 * 一覧では長さだけを返し、詳細（作図画面）で中身を取る。</p>
 */
@Mapper
public interface GeometryMapper {

    long count(@Param("keyword") String keyword,
               @Param("figureType") String figureType,
               @Param("tag") String tag,
               @Param("includeDeleted") boolean includeDeleted);

    List<GeometryEntity> search(@Param("keyword") String keyword,
                                @Param("figureType") String figureType,
                                @Param("tag") String tag,
                                @Param("includeDeleted") boolean includeDeleted,
                                @Param("sort") String sort,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    /** 一覧のサマリ（種類別・削除済みの件数）。 */
    GeometryTotalsEntity totals();

    /** タグの候補（| 区切りを分解して件数を数える）。 */
    List<GeometryTagEntity> tagSuggestions(@Param("includeDeleted") boolean includeDeleted);

    GeometryEntity findById(@Param("figureId") long figureId);

    GeometryEntity findByNo(@Param("figureNo") String figureNo);

    /** 表示順の次の値（新規作成・コピーで使う）。 */
    int nextDisplayOrder();

    int insert(GeometryEntity entity);

    /** 更新（楽観的ロック）。 */
    int update(GeometryEntity entity);

    int updateDisplayOrder(@Param("figureId") long figureId,
                           @Param("displayOrder") int displayOrder,
                           @Param("operator") Long operator,
                           @Param("version") int version);

    /** 論理削除（状態コードを DELETED にする）。 */
    int softDelete(@Param("figureId") long figureId, @Param("operator") Long operator);
}
