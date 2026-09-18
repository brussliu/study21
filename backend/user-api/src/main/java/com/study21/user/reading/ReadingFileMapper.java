package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RED_書籍ファイル情報（本文 PDF・表紙画像）の Mapper。
 *
 * <p>`UNIQUE (書籍ID, ファイル区分)` なので 1 冊 1 PDF・1 表紙。差し替えは同じ行の更新
 * （登録元コード・登録日時は残す）。実体ファイルの出入りはサービス側が
 * {@link ReadingFileStorage} で行い、この Mapper は行だけを扱う。</p>
 */
@Mapper
public interface ReadingFileMapper {

    /** その本の 1 件（PDF / COVER）。無ければ null。 */
    ReadingFileEntity find(@Param("bookId") long bookId, @Param("fileKind") String fileKind);

    /** その本の全件（PDF・表紙。書籍削除で実体を消すときに使う）。 */
    List<ReadingFileEntity> findByBook(@Param("bookId") long bookId);

    /**
     * 区分ごとの**見える**本の全件（本棚のサマリ「PDF 登録済み」の集計に使う）。
     *
     * <p>2026-09-14 の追加: 一覧と同じ可視条件（全体書籍 ＋ 自分の家庭の書籍）を当てる。
     * 当てないと、他家庭の本の PDF まで「PDF 登録済み N 冊」に数えてしまう。</p>
     */
    List<ReadingFileEntity> findByKind(@Param("fileKind") String fileKind,
                                       @Param("accountId") Long accountId,
                                       @Param("familyStudentId") Long familyStudentId,
                                       @Param("scope") String scope,
                                       @Param("myShelfOnly") boolean myShelfOnly);

    int insert(ReadingFileEntity entity);

    /** 差し替え（同じ行の更新。登録元コード・登録日時は残す）。 */
    int update(ReadingFileEntity entity);

    int delete(@Param("fileId") long fileId);

    int deleteByBook(@Param("bookId") long bookId);
}
