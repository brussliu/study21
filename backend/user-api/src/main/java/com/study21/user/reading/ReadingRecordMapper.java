package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * RED_読書記録情報（読書履歴）の Mapper。
 *
 * <p>絞り込みは `count` と `list` で同じ WHERE 句（`RecordWhere`）を使う。
 * 一覧の `totalElements` / `totalPages` が絞り込み後の件数と一致するため。</p>
 *
 * <p>2026-09-14 の追加: **見えない本の記録は返さない**。本を指定しない一覧
 * （書籍管理画面の「読書履歴」）でも、他家庭の書籍の履歴が混ざらないように
 * `familyStudentId`（自分の家庭＝生徒のアカウントID。管理者は null）で絞る。</p>
 */
@Mapper
public interface ReadingRecordMapper {

    long count(@Param("bookId") Long bookId,
               @Param("dateFrom") LocalDate dateFrom,
               /** 排他（この日の 0 時より前）。サービスが `dateTo + 1日` を渡す */
               @Param("dateToExclusive") LocalDate dateToExclusive,
               @Param("pageNo") Integer pageNo,
               @Param("familyStudentId") Long familyStudentId);

    List<ReadingRecordEntity> list(@Param("bookId") Long bookId,
                                   @Param("dateFrom") LocalDate dateFrom,
                                   @Param("dateToExclusive") LocalDate dateToExclusive,
                                   @Param("pageNo") Integer pageNo,
                                   @Param("familyStudentId") Long familyStudentId,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    ReadingRecordEntity findById(@Param("recordId") long recordId);

    int insert(ReadingRecordEntity entity);

    int delete(@Param("recordId") long recordId);

    /**
     * その書籍の読書記録をすべて削除する（画面の【標記クリア】＝標記・読書記録・進捗のリセットで使う）。
     * 消した件数を返すので、画面のメッセージに件数を出せる。
     */
    int deleteByBook(@Param("bookId") long bookId);
}
