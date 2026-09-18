package com.study21.user.reading;

import com.study21.user.account.AccountType;

/**
 * 読書管理を「だれの目で見ているか」。
 *
 * <p>2026-09-14 の決定（公開範囲）を 1 か所にまとめたもの。</p>
 *
 * <ul>
 *   <li>**全体書籍**（`公開範囲コード='GLOBAL'`）… 管理者が登録し、全家庭の【図書館】に出る</li>
 *   <li>**家庭の書籍**（`公開範囲コード='FAMILY'`）… 保護者が登録し、その家庭の中だけに出る。
 *       持ち主は {@code 所有家族学生ID}（＝生徒のアカウントID）</li>
 *   <li>**管理者は家庭を持たない**（{@code familyStudentId} が null）ので、全体書籍だけが見える</li>
 * </ul>
 *
 * <p>家庭の解決は既存の「保護者—生徒」の紐付けを使う（新しい家庭ID は作らない）:
 * 生徒は自分のアカウントID、保護者は {@code ACC_アカウント.保護者ID} から解決した
 * 生徒のアカウントID。`DOC_資料情報.家族学生ID` と同じ考え方。</p>
 *
 * @param accountId       操作しているアカウント（自分の本棚の持ち主）
 * @param accountType     アカウント種別（ADMIN / GUARDIAN / STUDENT）
 * @param familyStudentId 自分の家庭（生徒のアカウントID）。管理者は null
 */
public record ReadingScope(long accountId, AccountType accountType, Long familyStudentId) {

    /** 全体書籍だけを対象にする絞り込み。 */
    public static final String SCOPE_GLOBAL = "GLOBAL";
    /** 自分の家庭の書籍だけを対象にする絞り込み。 */
    public static final String SCOPE_FAMILY = "FAMILY";
    /** 全体書籍＋自分の家庭の書籍（既定）。 */
    public static final String SCOPE_ALL = "ALL";

    /** 自分の本棚だけを対象にする絞り込み（`GET /books?shelf=MINE`）。 */
    public static final String SHELF_MINE = "MINE";

    public boolean isAdmin() {
        return accountType == AccountType.ADMIN;
    }

    public boolean isGuardian() {
        return accountType == AccountType.GUARDIAN;
    }

    public boolean isStudent() {
        return accountType == AccountType.STUDENT;
    }

    /**
     * 書籍・分類を**登録・修正・削除**できるか。
     * 生徒は読むことと自分の本棚の出し入れだけ（決定 Q9）。
     */
    public boolean canManageBooks() {
        return !isStudent();
    }

    /**
     * その本を**修正・削除**してよいか（可視判定を通ったあとに呼ぶ）。
     *
     * <ul>
     *   <li>管理者 … 全体書籍だけ（家庭の書籍は見えないので、ここへ来る前に 404）</li>
     *   <li>保護者 … 自分の家庭の書籍だけ（全体書籍は読めるが直せない）</li>
     *   <li>生徒   … 不可</li>
     * </ul>
     */
    public boolean canEdit(ReadingBookEntity book) {
        if (book == null) {
            return false;
        }
        if (isAdmin()) {
            return isGlobal(book);
        }
        if (isGuardian()) {
            return isFamilyBook(book) && familyStudentId != null
                    && familyStudentId.equals(book.getOwnerFamilyId());
        }
        return false;
    }

    /** その本が全体書籍か。 */
    public boolean isGlobal(ReadingBookEntity book) {
        return book != null && SCOPE_GLOBAL.equals(book.getScope());
    }

    /** その本が家庭の書籍か。 */
    public boolean isFamilyBook(ReadingBookEntity book) {
        return book != null && SCOPE_FAMILY.equals(book.getScope());
    }

    /** 新しく登録する本の公開範囲（管理者＝GLOBAL / 保護者＝FAMILY）。 */
    public String createScope() {
        return isAdmin() ? SCOPE_GLOBAL : SCOPE_FAMILY;
    }

    /** 新しく作る分類の持ち主（管理者＝全体（null）/ 保護者＝自家庭）。 */
    public Long categoryOwnerFamilyId() {
        return isAdmin() ? null : familyStudentId;
    }
}
