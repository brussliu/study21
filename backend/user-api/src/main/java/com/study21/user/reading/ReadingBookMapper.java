package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 * RED_書籍情報（読書管理の本）の Mapper。
 *
 * <p>2026-09-14 の決定で、本は**公開範囲**（`GLOBAL`=管理者が登録した全体書籍 /
 * `FAMILY`=家庭の中だけの書籍）を持ち、見える範囲は「全体書籍 ＋ 自分の家庭の書籍」に
 * なった（管理者は家庭を持たないので全体書籍だけ）。</p>
 *
 * <p>そのため一覧・件数・サマリ・1 冊のすべてに同じ可視条件（`BookScope`）を当てる。
 * 渡すのは {@code accountId}（自分の本棚の持ち主）・{@code familyStudentId}
 * （自分の家庭＝生徒のアカウントID。管理者は null）・{@code scope}（`ALL`/`GLOBAL`/`FAMILY`。
 * null は `ALL`）・{@code myShelfOnly}（`shelf=MINE`）。</p>
 *
 * <p>{@code 書籍番号} の重複チェック（{@link #findByNo(String)}）は採番専用なので
 * 可視条件を当てない（番号はシステム全体で一意にする）。</p>
 */
@Mapper
public interface ReadingBookMapper {

    /**
     * 件数（一覧と同じ絞り込み条件）。categoryId は 0＝未分類のみ、正の値＝その分類。
     * language は 中国語 / 英語 / 日本語。
     */
    long count(@Param("keyword") String keyword,
               @Param("difficulty") String difficulty,
               @Param("status") String status,
               @Param("tag") String tag,
               @Param("pinned") Boolean pinned,
               @Param("categoryId") Long categoryId,
               @Param("language") String language,
               @Param("accountId") Long accountId,
               @Param("familyStudentId") Long familyStudentId,
               @Param("scope") String scope,
               @Param("myShelfOnly") boolean myShelfOnly);

    /** 本の 1 ページ（置頂 → 最後に読んだ順）。 */
    List<ReadingBookEntity> search(@Param("keyword") String keyword,
                                   @Param("difficulty") String difficulty,
                                   @Param("status") String status,
                                   @Param("tag") String tag,
                                   @Param("pinned") Boolean pinned,
                                   @Param("categoryId") Long categoryId,
                                   @Param("language") String language,
                                   @Param("accountId") Long accountId,
                                   @Param("familyStudentId") Long familyStudentId,
                                   @Param("scope") String scope,
                                   @Param("myShelfOnly") boolean myShelfOnly,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    /**
     * サマリ（冊数・読書中・読了・累計時間・標記・未分類・自分の本棚の冊数）。
     * 一覧と同じ可視条件を当てる（画面の「全 N 冊」を一覧と一致させるため）。
     */
    ReadingShelfTotals totals(@Param("accountId") Long accountId,
                              @Param("familyStudentId") Long familyStudentId,
                              @Param("scope") String scope,
                              @Param("myShelfOnly") boolean myShelfOnly);

    /**
     * 1 冊。**可視判定はサービス層が行う**（全体書籍か自分の家庭の書籍かを見て、
     * 見えない本は 404 にする）。SQL 側では絞らない（1 冊の判定は
     * `ReadingScope` の規則をそのまま使いたいため）。
     */
    ReadingBookEntity findById(@Param("bookId") long bookId);

    /** 書籍番号 の重複チェック（採番専用。可視条件は当てない）。 */
    ReadingBookEntity findByNo(@Param("bookNo") String bookNo);

    int insert(ReadingBookEntity entity);

    /** 修正（楽観的ロック: 画面が持っていた バージョン と一致するときだけ更新する）。 */
    int update(ReadingBookEntity entity);

    int updatePinned(@Param("bookId") long bookId,
                     @Param("pinned") boolean pinned,
                     @Param("operator") Long operator,
                     @Param("version") int version);

    int updateStatus(@Param("bookId") long bookId,
                     @Param("status") String status,
                     @Param("operator") Long operator,
                     @Param("version") int version);

    /**
     * PDF を上げたときに画面（pdf.js）が数えた総ページ数を反映する。
     * 現在ページが新しい総ページ数を超えていたら丸める（LEAST）。
     */
    int updateTotalPages(@Param("bookId") long bookId,
                         @Param("totalPages") int totalPages,
                         @Param("operator") Long operator);

    /**
     * 読書記録を付けたときの更新（2.0 と同じ規則）。
     * 現在ページ・読書ステータス（総ページに達したら 読了）・最近の読書時間・
     * 累計読書時間（加算）・最終読書日時。
     */
    int updateAfterRecord(@Param("bookId") long bookId,
                          @Param("currentPage") int currentPage,
                          @Param("status") String status,
                          @Param("minutes") int minutes,
                          @Param("readAt") Timestamp readAt,
                          @Param("operator") Long operator);

    /** 累計標記件数を数え直す（標記の追加・削除のあと）。 */
    int refreshMarkCount(@Param("bookId") long bookId, @Param("operator") Long operator);

    /**
     * 読書の進捗を最初の状態に戻す（現在ページ 1・未着手・最近/累計の読書時間 0・最終読書日時 なし）。
     * 標記の全削除と一緒に使う（【標記クリア】）。読書記録（履歴）は消さない。
     */
    int resetProgress(@Param("bookId") long bookId, @Param("operator") Long operator);

    /** 読書履歴の削除後に、最近/累計の読書時間と最終読書日時を残った記録から計算し直す。 */
    int refreshRecordSummary(@Param("bookId") long bookId, @Param("operator") Long operator);

    int delete(@Param("bookId") long bookId);

    // ------------------------------------------ 【自分の本棚】（アカウントごと）

    /**
     * 【本棚に入れる】。すでに入っていれば何もしない（冪等。UNIQUE への ON CONFLICT）。
     * 入れた行の数（0＝すでに入っていた）を返す。
     */
    int insertShelf(@Param("bookId") long bookId,
                    @Param("accountId") long accountId,
                    @Param("operator") Long operator);

    /** 【本棚から外す】。入っていなければ何もしない（冪等）。外した行数を返す。 */
    int deleteShelf(@Param("bookId") long bookId, @Param("accountId") long accountId);

    /** その本が自分の本棚に入っているか。 */
    boolean existsShelf(@Param("bookId") long bookId, @Param("accountId") long accountId);

    /** 自分の本棚の冊数。 */
    long countShelf(@Param("accountId") long accountId);
}
