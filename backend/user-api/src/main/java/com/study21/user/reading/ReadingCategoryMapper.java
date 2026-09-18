package com.study21.user.reading;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RED_書籍分類情報（本棚の分類＝マスタ）の Mapper。
 *
 * <p>2026-09-14 の決定で分類は**家庭ごと**になった:
 * `所有家族学生ID` が NULL の分類は「全体の分類」（管理者が作り、全家庭に見える）、
 * 値が入っている分類はその家庭だけの分類。分類名は**家庭の中で一意**。</p>
 *
 * <p>見えるのは「全体の分類 ＋ 自分の家庭の分類」なので、一覧・1 件・重複チェック・
 * 表示順の採番はすべて `familyStudentId`（自分の家庭＝生徒のアカウントID。管理者は null）で
 * 絞る。冊数（`bookCount`）は**その人が見える本だけ**を数える（一覧の冊数と一致させる）。</p>
 *
 * <p>削除しても本は消えず、`RED_書籍情報.分類ID` が NULL に戻る
 * （DDL の ON DELETE SET NULL）ので、削除前に {@link #countBooks} で冊数を数えて
 * 画面に案内する。</p>
 */
@Mapper
public interface ReadingCategoryMapper {

    /** 見える分類の一覧（表示順 → 分類ID 順）。見える本だけを数えた冊数つき。 */
    List<ReadingCategoryEntity> list(@Param("familyStudentId") Long familyStudentId);

    /** 1 件。見えない分類（他家庭の分類）は null を返す（サービスが 404 にする）。 */
    ReadingCategoryEntity findById(@Param("categoryId") long categoryId,
                                   @Param("familyStudentId") Long familyStudentId);

    /**
     * 同じ持ち主の中で同名の分類があるか（重複チェック。自分自身は除く）。
     *
     * @param ownerFamilyId 作ろうとしている（直そうとしている）分類の持ち主。null＝全体の分類
     */
    ReadingCategoryEntity findByName(@Param("name") String name,
                                     @Param("excludeId") Long excludeId,
                                     @Param("ownerFamilyId") Long ownerFamilyId);

    /** 同じ持ち主の中での末尾に足すときの表示順（最大 + 1）。0 件なら 1。 */
    int nextDisplayOrder(@Param("ownerFamilyId") Long ownerFamilyId);

    /** その分類に入っている**見える**冊数（削除の案内に使う）。 */
    long countBooks(@Param("categoryId") long categoryId,
                    @Param("familyStudentId") Long familyStudentId);

    int insert(ReadingCategoryEntity entity);

    int update(ReadingCategoryEntity entity);

    int delete(@Param("categoryId") long categoryId);
}
