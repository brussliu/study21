package com.study21.user.linkclip;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * リンククリップはアカウント個人所有。すべての操作で所有者アカウントIDを条件に含める
 * （親子間で共有しない）。
 */
@Mapper
public interface LinkClipMapper {
    List<LinkClipEntity> search(@Param("ownerAccountId") long ownerAccountId,
                                @Param("folderCode") String folderCode,
                                @Param("sourceCode") String sourceCode,
                                @Param("clipType") String clipType,
                                @Param("tag") String tag,
                                @Param("keyword") String keyword,
                                @Param("favoriteOnly") boolean favoriteOnly,
                                @Param("archiveState") String archiveState);
    LinkClipEntity findById(@Param("ownerAccountId") long ownerAccountId, @Param("linkClipId") long linkClipId);
    List<LinkClipEntity> findByNormalizedUrl(@Param("ownerAccountId") long ownerAccountId,
                                             @Param("normalizedUrl") String normalizedUrl);
    List<LinkClipEntity> findByIds(@Param("ownerAccountId") long ownerAccountId,
                                   @Param("ids") List<Long> ids);
    int insert(LinkClipEntity clip);
    int update(@Param("ownerAccountId") long ownerAccountId, @Param("linkClipId") long linkClipId,
               @Param("beforeVersion") int beforeVersion, @Param("clip") LinkClipEntity clip);
    int delete(@Param("ownerAccountId") long ownerAccountId, @Param("linkClipId") long linkClipId);
    int updateFlags(@Param("ownerAccountId") long ownerAccountId, @Param("linkClipId") long linkClipId,
                    @Param("favorite") Boolean favorite, @Param("read") Boolean read,
                    @Param("archived") Boolean archived);
    int touchView(@Param("ownerAccountId") long ownerAccountId, @Param("linkClipId") long linkClipId);

    List<LinkClipEntity.TagRow> findTagsByClipIds(@Param("ownerAccountId") long ownerAccountId,
                                                  @Param("ids") List<Long> ids);
    /** タグ候補（所有者の全クリップから重複を除いて件数つきで返す）。 */
    List<LinkClipModels.TagOption> findTagOptions(@Param("ownerAccountId") long ownerAccountId);
    /** 保存先ごとの件数（フォルダ以外の絞り込みを反映）。 */
    List<LinkClipModels.LaneCount> countByFolder(@Param("ownerAccountId") long ownerAccountId,
                                                 @Param("sourceCode") String sourceCode,
                                                 @Param("clipType") String clipType,
                                                 @Param("tag") String tag,
                                                 @Param("keyword") String keyword,
                                                 @Param("favoriteOnly") boolean favoriteOnly,
                                                 @Param("archiveState") String archiveState);
    int deleteTags(@Param("linkClipId") long linkClipId);
    int insertTag(@Param("linkClipId") long linkClipId, @Param("tagName") String tagName,
                  @Param("displayOrder") int displayOrder, @Param("ownerAccountId") long ownerAccountId);
}
