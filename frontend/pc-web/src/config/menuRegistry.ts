import type { MenuItem, Role } from '@study21/web-shared'

export type AppArea = 'admin' | 'student' | 'parent'

function screen(
  area: AppArea,
  id: string,
  label: string,
  slug: string,
  icon: string,
  children?: MenuItem[]
): MenuItem {
  return { id, label, icon, path: `/${area}/${slug}`, enabled: true, children }
}

/**
 * UI 移行期間用メニュー。
 * 役割別の権限が未確定のため、現在は三つのエリアで同じ画面を表示する。
 * アイコンは原 ui-demo のサイドバー（design-system.html）と同一の `#i-*` を参照。
 */
export function frameworkMenu(area: AppArea): MenuItem[] {
  return [screen(area, `${area}-home`, 'ホーム', 'home', 'home')]
}

export function prototypeMenu(area: AppArea, role?: Role): MenuItem[] {
  return [
    screen(area, 'mindmap', '思維導図', 'mindmap', 'grid'),
    screen(area, 'daily-report', '学習日報', 'daily-report', 'book'),
    screen(area, 'todo', 'TODO', 'todo', 'check-square'),
    // 読書管理（親メニュー）。2.0 の英語読書を【書籍管理】と【書籍閲覧】に分けた。
    // 【書籍管理】は管理者（全体書籍）と保護者（自分の家庭の本）だけの画面なので、
    // 生徒には出さない（2026-09-14 の決定 Q9。ルートは残るが API は 403）。
    screen(area, 'reading', '読書管理', 'reading-books', 'book', [
      ...(canManageBooks(role)
        ? [screen(area, 'reading-books', '書籍管理', 'reading-books', 'book')]
        : []),
      screen(area, 'reading-reader', '書籍閲覧', 'reading-reader', 'book-open'),
      // 書籍閲覧2 は既存の書籍閲覧（バックアップとして残す）とは別の画面。
      // 本棚を探す操作感（背表紙・ホバー・抜き出し）で読む。
    ]),
    screen(area, 'english', '英語勉強', 'english', 'type', [
      screen(area, 'english-reading-intensive', '英語読解・精読', 'english-reading-intensive', 'book'),
      screen(area, 'english-essay', '英作文AI添削', 'english-essay', 'edit'),
      screen(area, 'english-cloze', '英語穴埋め問題', 'english-cloze', 'filter'),
      screen(area, 'ai', 'AI英語学習', 'ai', 'play'),
      screen(area, 'english-grammar', '英語文法管理', 'english-grammar', 'type'),
      screen(area, 'english-word-textbook', '英単語教材取込', 'english-word-textbook', 'book-open'),
      screen(area, 'testword', '単語テスト', 'testword', 'play'),
      screen(area, 'phrase-test', '熟語テスト', 'phrase-test', 'clipboard'),
      screen(area, 'word', '単語情報管理', 'word', 'folder'),
      screen(area, 'word-status', '単語勉強状況', 'word-status', 'clock')
    ]),
    screen(area, 'japanese', '日本語勉強', 'japanese', 'book-open', [
      screen(area, 'japanese-test', '単語テスト', 'japanese-test', 'play'),
      screen(area, 'japanese-word', '単語情報管理', 'japanese-word', 'folder'),
      screen(area, 'japanese-word-status', '単語勉強状況', 'japanese-word-status', 'clock'),
      /*
       * 画面確認用の単語情報管理（仮データだけで動き、本番 API は呼ばない）。
       * 本番の「単語情報管理」と MENU 上で見分けられるよう「（新画面）」を付けている。
       * 確認が済んだら、この 1 行を外す。
       */
      screen(area, 'japanese-demo', '単語情報管理（新画面）', 'japanese-demo', 'eye')
    ]),
    screen(area, 'math', '数学勉強', 'math', 'sigma', [
      screen(area, 'math-knowledge', '知識点管理', 'math-knowledge', 'grid'),
      screen(area, 'math-wrong', '誤問題集', 'math-wrong', 'alert'),
      screen(area, 'geometry', '図形管理', 'geometry', 'edit')
    ]),
    // 授業録音 / AI 授業記録（授業の録音と書き起こし・AI ノート）。ページ（流れ）のみ。
    // ロールの出し分けは未決定のため、まずは 3 エリア共通で出す（権限はバックエンドで守る）。
    screen(area, 'classroom', '授業録音', 'classroom', 'mic'),
    screen(area, 'game', 'ゲーム', 'game', 'gamepad'),
    screen(area, 'testinfo', 'テスト情報管理', 'testinfo', 'clipboard'),
    screen(area, 'document', '資料管理', 'document', 'folder'),
    screen(area, 'temp-file', '臨時ファイル管理', 'temp-file', 'image'),
    screen(area, 'network', 'ネットワーク制御', 'site', 'globe', [
      screen(area, 'site', 'サイト管理', 'site', 'globe'),
      screen(area, 'terminal-control', '端末コントロール', 'terminal-control', 'monitor'),
      screen(area, 'internet-usage', 'インターネット利用履歴', 'internet-usage', 'clock')
    ]),
    screen(area, 'link-clip', 'リンククリップ', 'link-clip', 'bookmark'),
    screen(area, 'app-control', 'アプリ制御', 'agent-control', 'shield', [
      screen(area, 'app-manage', 'アプリ管理', 'agent-control', 'shield'),
      screen(area, 'app-history', 'アプリ利用履歴', 'agent-history', 'clock')
    ]),
    screen(area, 'batch', 'バッチ管理', 'batch', 'sliders', [
      screen(area, 'batch-list', 'バッチ一覧', 'batch', 'sliders'),
      screen(area, 'batch-history', 'バッチ実行履歴', 'batch-history', 'clock'),
      screen(area, 'batch-ai-history', 'AI呼出履歴', 'batch-ai-history', 'play')
    ]),
    screen(area, 'study-monitor', '学習状況モニター', 'study-monitor', 'video'),
    // 「履歴管理」（2.0 の history.jsp を再現したプロトタイプ画面）は左メニューから外した。
    // 中身は インターネット利用履歴 / バッチ実行履歴 / AI呼出履歴 として個別の画面に移している。
    screen(area, 'setting', '設定', 'setting', 'sliders')
  ]
}

export const businessMenu: MenuItem[] = []

/**
 * 【書籍管理】（読書管理の子）を開けるロールか。
 *
 * 2026-09-14 の決定 Q9: 管理者＝全体書籍・保護者＝自分の家庭の本を管理する。
 * 生徒は読むことと【自分の本棚】の出し入れだけなので、メニューに出さない。
 * ロールが分からないとき（未ログイン・UI 確認）は出しておく（API 側が 403 で守る）。
 */
function canManageBooks(role?: Role): boolean {
  return role === undefined || role === 'ADMIN' || role === 'GUARDIAN'
}

export function resolveMenu(area: AppArea, role?: Role): MenuItem[] {
  return [...frameworkMenu(area), ...prototypeMenu(area, role)]
}
