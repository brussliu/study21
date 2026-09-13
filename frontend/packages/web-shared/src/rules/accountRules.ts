/**
 * アカウント関連の入力規則（pc-web / mobile-web 共通）。
 *
 * バックエンドの検証（RegisterRequest / ProfileUpdateRequest / PasswordChangeRequest）と
 * 同じ規則を画面側でも使う。文言もバックエンドのメッセージに合わせる。
 */

/** メールアドレスの形式。 */
export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** パスワード: 8 文字以上で英字と数字をそれぞれ 1 文字以上。 */
export const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

/** 電話番号: 数字・ハイフン・括弧・空白・+ のみ、20 文字以内（空欄可）。 */
export const PHONE_RE = /^[0-9+()\-\s]{0,20}$/

export const PASSWORD_MIN_LENGTH = 8

export const PASSWORD_HINT = '8文字以上、英字と数字をそれぞれ1文字以上含めてください。'

export const PASSWORD_ERROR = 'パスワードは8文字以上で、英字と数字をそれぞれ1文字以上含めてください。'

export const PHONE_HINT = 'ハイフンあり・なしどちらでも構いません（例: 090-0000-0000）。'

export const PHONE_ERROR = '電話番号の形式が正しくありません。'

export const EMAIL_ERROR = 'メールアドレスの形式が正しくありません。'

/** 学年の選択肢（登録画面と同じ並び）。 */
export const GRADE_GROUPS: { label: string; items: string[] }[] = [
  {
    label: '小学校',
    items: ['小学1年生', '小学2年生', '小学3年生', '小学4年生', '小学5年生', '小学6年生']
  },
  { label: '中学校', items: ['中学1年生', '中学2年生', '中学3年生'] },
  { label: '高校', items: ['高校1年生', '高校2年生', '高校3年生'] }
]

/** 学年の全選択肢（既存データに無い学年でも選択肢として扱えるよう、現在値も足して使う）。 */
export const GRADE_OPTIONS: string[] = GRADE_GROUPS.flatMap((group) => group.items)
