package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** RED_標記情報（ハイライト・下線・メモ・語彙）の Mapper。 */
@Mapper
public interface ReadingMarkMapper {

    long count(@Param("bookId") long bookId,
               @Param("pageNo") Integer pageNo,
               @Param("markType") String markType);

    List<ReadingMarkEntity> list(@Param("bookId") long bookId,
                                 @Param("pageNo") Integer pageNo,
                                 @Param("markType") String markType,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    ReadingMarkEntity findById(@Param("markId") long markId);

    /** 標記が付いているページ番号（閲覧画面のページレールの印）。 */
    List<Integer> markedPages(@Param("bookId") long bookId);

    /** 同じページの次の表示順。 */
    int nextOrderNo(@Param("bookId") long bookId, @Param("pageNo") int pageNo);

    int insert(ReadingMarkEntity entity);

    int delete(@Param("markId") long markId);

    int deleteByBook(@Param("bookId") long bookId);
}
