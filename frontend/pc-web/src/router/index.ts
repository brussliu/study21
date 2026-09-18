import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export type LayoutName = 'admin' | 'user' | 'blank'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    layout?: LayoutName
    requiresAuth?: boolean
  }
}

type AppArea = 'admin' | 'student' | 'parent'

/**
 * ゲーム機能のルート（3 エリアで同じ画面を使う）。
 * 6 種類のゲームは「資料管理」と同じく 1 ページのタブ（?game=<slug>）で切り替える。
 * すべてフロントエンドだけで動作し、DB には保存しない。
 */
function gameRoutes(area: AppArea): RouteRecordRaw[] {
  return [
    {
      path: 'game',
      name: `${area}-game`,
      component: () => import('@/views/game/GameView.vue'),
      meta: { title: 'ゲーム', layout: area === 'admin' ? 'admin' : 'user' }
    }
  ]
}

/**
 * 授業録音 / AI 授業記録（3 エリアで同じ画面を使う）。
 * 「1 機能 = 1 ルート」で、一覧 → 録音中 → 詳細 を別ルートにする。
 * **新しい授業は一覧の dialog**（ページ遷移しない。利用者の指示）なので、専用のルートは置かない。
 * すべてフロントエンドだけで動作し、バックエンド（録音・STT・AI）はまだ無い。
 */
function classroomRoutes(area: AppArea): RouteRecordRaw[] {
  const layout = area === 'admin' ? 'admin' : 'user'
  return [
    {
      path: 'classroom',
      name: `${area}-classroom`,
      component: () => import('@/views/classroom/ClassroomListView.vue'),
      meta: { title: '授業録音', layout }
    },
    {
      path: 'classroom/:id/live',
      name: `${area}-classroom-live`,
      component: () => import('@/views/classroom/ClassroomLiveView.vue'),
      meta: { title: '録音中', layout }
    },
    {
      path: 'classroom/:id',
      name: `${area}-classroom-detail`,
      component: () => import('@/views/classroom/ClassroomDetailView.vue'),
      meta: { title: '授業詳細', layout }
    }
  ]
}

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/UserLoginView.vue'),
    meta: { title: 'ログイン', layout: 'blank' }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/login/ForgotPasswordView.vue'),
    meta: { title: 'パスワード再設定', layout: 'blank' }
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/login/AdminLoginView.vue'),
    meta: { title: '管理者ログイン', layout: 'blank' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/register/RegisterView.vue'),
    meta: { title: '新規登録', layout: 'blank' }
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { layout: 'admin', requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/home' },
      {
        path: 'home',
        name: 'admin-home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: 'ホーム', layout: 'admin' }
      },
      {
        path: 'setting',
        name: 'admin-system-settings',
        component: () => import('@/views/admin/system-settings/SystemSettingsView.vue'),
        meta: { title: 'システム設定', layout: 'admin' }
      },
      {
        path: 'document',
        name: 'admin-document',
        component: () => import('@/views/document/DocumentManagementView.vue'),
        meta: { title: '資料管理', layout: 'admin' }
      },
      {
        path: 'document-folder',
        redirect: { path: '/admin/document', query: { view: 'folder' } }
      },
      {
        path: 'temp-file',
        name: 'admin-temp-file',
        component: () => import('@/views/tempfile/TempFileView.vue'),
        meta: { title: '臨時ファイル管理', layout: 'admin' }
      },
      {
        path: 'testinfo',
        name: 'admin-testinfo',
        component: () => import('@/views/testinfo/TestInfoView.vue'),
        meta: { title: 'テスト情報管理', layout: 'admin' }
      },
      {
        path: 'link-clip',
        name: 'admin-link-clip',
        component: () => import('@/views/linkclip/LinkClipView.vue'),
        meta: { title: 'リンククリップ', layout: 'admin' }
      },
      ...gameRoutes('admin'),
      ...classroomRoutes('admin'),
      {
        path: 'site',
        name: 'admin-site',
        component: () => import('@/views/net/SiteManagementView.vue'),
        meta: { title: 'サイト管理', layout: 'admin' }
      },
      {
        path: 'terminal-control',
        name: 'admin-terminal-control',
        component: () => import('@/views/net/TerminalControlView.vue'),
        meta: { title: '端末コントロール', layout: 'admin' }
      },
      {
        path: 'batch',
        name: 'admin-batch',
        component: () => import('@/views/batch/BatchListView.vue'),
        meta: { title: 'バッチ一覧', layout: 'admin' }
      },
      {
        path: 'batch-history',
        name: 'admin-batch-history',
        component: () => import('@/views/batch/BatchHistoryView.vue'),
        meta: { title: 'バッチ実行履歴', layout: 'admin' }
      },
      {
        path: 'batch-ai-history',
        name: 'admin-batch-ai-history',
        component: () => import('@/views/batch/AiCallHistoryView.vue'),
        meta: { title: 'AI呼出履歴', layout: 'admin' }
      },
      {
        path: 'internet-usage',
        name: 'admin-internet-usage',
        component: () => import('@/views/network/InternetUsageHistoryView.vue'),
        meta: { title: 'インターネット利用履歴', layout: 'admin' }
      },
      {
        path: 'daily-report',
        name: 'admin-daily-report',
        component: () => import('@/views/daily-report/DailyReportView.vue'),
        meta: { title: '学習日報', layout: 'admin' }
      },
      {
        path: 'todo',
        name: 'admin-todo',
        component: () => import('@/views/todo/TodoView.vue'),
        meta: { title: 'TODO', layout: 'admin' }
      },
      {
        path: 'study-monitor',
        name: 'admin-study-monitor',
        component: () => import('@/views/study-monitor/StudyMonitorView.vue'),
        meta: { title: '学習状況モニター', layout: 'admin' }
      },
      // 読書管理（親メニュー）。2.0 の英語読書を 書籍管理 / 書籍閲覧 に分けた
      {
        path: 'reading-books',
        name: 'admin-reading-books',
        component: () => import('@/views/reading/BookManagementView.vue'),
        meta: { title: '書籍管理', layout: 'admin' }
      },
      {
        path: 'reading-reader',
        name: 'admin-reading-reader',
        component: () => import('@/views/reading/BookReaderView.vue'),
        meta: { title: '書籍閲覧', layout: 'admin' }
      },
      // 日本語勉強（単語情報管理・単語テスト・単語勉強状況）
      {
        path: 'japanese-word',
        name: 'admin-japanese-word',
        component: () => import('@/views/japanese/JapaneseWordView.vue'),
        meta: { title: '単語情報管理', layout: 'admin' }
      },
      {
        path: 'japanese-test',
        name: 'admin-japanese-test',
        component: () => import('@/views/japanese/JapaneseTestView.vue'),
        meta: { title: '単語テスト', layout: 'admin' }
      },
      {
        path: 'japanese-word-status',
        name: 'admin-japanese-word-status',
        component: () => import('@/views/japanese/JapaneseWordStatusView.vue'),
        meta: { title: '単語勉強状況', layout: 'admin' }
      },
      // 数学勉強（図形管理・図形作成・AI 生図）。2.0 の geometry.jsp / geometry_draw.jsp
      {
        path: 'geometry',
        name: 'admin-geometry',
        component: () => import('@/views/geometry/GeometryView.vue'),
        meta: { title: '図形管理', layout: 'admin' }
      },
      {
        path: 'geometry-draw',
        name: 'admin-geometry-draw',
        component: () => import('@/views/geometry/GeometryDrawView.vue'),
        meta: { title: '図形作成', layout: 'admin' }
      },
      {
        // AI 生図（画像から作図）。画面（流れ）だけで処理は未実装
        path: 'geometry-ai',
        name: 'admin-geometry-ai',
        component: () => import('@/views/geometry/GeometryAiView.vue'),
        meta: { title: 'AI 生図', layout: 'admin' }
      },
      {
        path: ':screen',
        name: 'admin-screen',
        component: () => import('@/views/prototype/PrototypePageView.vue'),
        meta: { title: 'UI移行画面', layout: 'admin' }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { layout: 'user', requiresAuth: true },
    children: [
      { path: '', redirect: '/student/home' },
      {
        path: 'home',
        name: 'student-home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: 'ホーム', layout: 'user' }
      },
      {
        path: 'setting',
        name: 'student-system-settings',
        component: () => import('@/views/admin/system-settings/SystemSettingsView.vue'),
        meta: { title: 'システム設定', layout: 'user' }
      },
      {
        path: 'document',
        name: 'student-document',
        component: () => import('@/views/document/DocumentManagementView.vue'),
        meta: { title: '資料管理', layout: 'user' }
      },
      {
        path: 'document-folder',
        redirect: { path: '/student/document', query: { view: 'folder' } }
      },
      {
        path: 'temp-file',
        name: 'student-temp-file',
        component: () => import('@/views/tempfile/TempFileView.vue'),
        meta: { title: '臨時ファイル管理', layout: 'user' }
      },
      {
        path: 'testinfo',
        name: 'student-testinfo',
        component: () => import('@/views/testinfo/TestInfoView.vue'),
        meta: { title: 'テスト情報管理', layout: 'user' }
      },
      {
        path: 'link-clip',
        name: 'student-link-clip',
        component: () => import('@/views/linkclip/LinkClipView.vue'),
        meta: { title: 'リンククリップ', layout: 'user' }
      },
      ...gameRoutes('student'),
      ...classroomRoutes('student'),
      {
        path: 'site',
        name: 'student-site',
        component: () => import('@/views/net/SiteManagementView.vue'),
        meta: { title: 'サイト管理', layout: 'user' }
      },
      {
        path: 'terminal-control',
        name: 'student-terminal-control',
        component: () => import('@/views/net/TerminalControlView.vue'),
        meta: { title: '端末コントロール', layout: 'user' }
      },
      {
        path: 'batch',
        name: 'student-batch',
        component: () => import('@/views/batch/BatchListView.vue'),
        meta: { title: 'バッチ一覧', layout: 'user' }
      },
      {
        path: 'batch-history',
        name: 'student-batch-history',
        component: () => import('@/views/batch/BatchHistoryView.vue'),
        meta: { title: 'バッチ実行履歴', layout: 'user' }
      },
      {
        path: 'batch-ai-history',
        name: 'student-batch-ai-history',
        component: () => import('@/views/batch/AiCallHistoryView.vue'),
        meta: { title: 'AI呼出履歴', layout: 'user' }
      },
      {
        path: 'internet-usage',
        name: 'student-internet-usage',
        component: () => import('@/views/network/InternetUsageHistoryView.vue'),
        meta: { title: 'インターネット利用履歴', layout: 'user' }
      },
      {
        path: 'daily-report',
        name: 'student-daily-report',
        component: () => import('@/views/daily-report/DailyReportView.vue'),
        meta: { title: '学習日報', layout: 'user' }
      },
      {
        path: 'todo',
        name: 'student-todo',
        component: () => import('@/views/todo/TodoView.vue'),
        meta: { title: 'TODO', layout: 'user' }
      },
      {
        path: 'study-monitor',
        name: 'student-study-monitor',
        component: () => import('@/views/study-monitor/StudyMonitorView.vue'),
        meta: { title: '学習状況モニター', layout: 'user' }
      },
      // 読書管理（親メニュー）。2.0 の英語読書を 書籍管理 / 書籍閲覧 に分けた
      {
        path: 'reading-books',
        name: 'student-reading-books',
        component: () => import('@/views/reading/BookManagementView.vue'),
        meta: { title: '書籍管理', layout: 'user' }
      },
      {
        path: 'reading-reader',
        name: 'student-reading-reader',
        component: () => import('@/views/reading/BookReaderView.vue'),
        meta: { title: '書籍閲覧', layout: 'user' }
      },
      // 日本語勉強（単語情報管理・単語テスト・単語勉強状況）
      {
        path: 'japanese-word',
        name: 'student-japanese-word',
        component: () => import('@/views/japanese/JapaneseWordView.vue'),
        meta: { title: '単語情報管理', layout: 'user' }
      },
      {
        path: 'japanese-test',
        name: 'student-japanese-test',
        component: () => import('@/views/japanese/JapaneseTestView.vue'),
        meta: { title: '単語テスト', layout: 'user' }
      },
      {
        path: 'japanese-word-status',
        name: 'student-japanese-word-status',
        component: () => import('@/views/japanese/JapaneseWordStatusView.vue'),
        meta: { title: '単語勉強状況', layout: 'user' }
      },
      // 数学勉強（図形管理・図形作成・AI 生図）。2.0 の geometry.jsp / geometry_draw.jsp
      {
        path: 'geometry',
        name: 'student-geometry',
        component: () => import('@/views/geometry/GeometryView.vue'),
        meta: { title: '図形管理', layout: 'user' }
      },
      {
        path: 'geometry-draw',
        name: 'student-geometry-draw',
        component: () => import('@/views/geometry/GeometryDrawView.vue'),
        meta: { title: '図形作成', layout: 'user' }
      },
      {
        // AI 生図（画像から作図）。画面（流れ）だけで処理は未実装
        path: 'geometry-ai',
        name: 'student-geometry-ai',
        component: () => import('@/views/geometry/GeometryAiView.vue'),
        meta: { title: 'AI 生図', layout: 'user' }
      },
      {
        path: ':screen',
        name: 'student-screen',
        component: () => import('@/views/prototype/PrototypePageView.vue'),
        meta: { title: 'UI移行画面', layout: 'user' }
      }
    ]
  },
  {
    path: '/parent',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { layout: 'user', requiresAuth: true },
    children: [
      { path: '', redirect: '/parent/home' },
      {
        path: 'home',
        name: 'parent-home',
        component: () => import('@/views/parent/ParentHomeView.vue'),
        meta: { title: 'ホーム', layout: 'user' }
      },
      {
        path: 'setting',
        name: 'parent-system-settings',
        component: () => import('@/views/admin/system-settings/SystemSettingsView.vue'),
        meta: { title: 'システム設定', layout: 'user' }
      },
      {
        path: 'document',
        name: 'parent-document',
        component: () => import('@/views/document/DocumentManagementView.vue'),
        meta: { title: '資料管理', layout: 'user' }
      },
      {
        path: 'document-folder',
        redirect: { path: '/parent/document', query: { view: 'folder' } }
      },
      {
        path: 'temp-file',
        name: 'parent-temp-file',
        component: () => import('@/views/tempfile/TempFileView.vue'),
        meta: { title: '臨時ファイル管理', layout: 'user' }
      },
      {
        path: 'testinfo',
        name: 'parent-testinfo',
        component: () => import('@/views/testinfo/TestInfoView.vue'),
        meta: { title: 'テスト情報管理', layout: 'user' }
      },
      {
        path: 'link-clip',
        name: 'parent-link-clip',
        component: () => import('@/views/linkclip/LinkClipView.vue'),
        meta: { title: 'リンククリップ', layout: 'user' }
      },
      ...gameRoutes('parent'),
      ...classroomRoutes('parent'),
      {
        path: 'site',
        name: 'parent-site',
        component: () => import('@/views/net/SiteManagementView.vue'),
        meta: { title: 'サイト管理', layout: 'user' }
      },
      {
        path: 'terminal-control',
        name: 'parent-terminal-control',
        component: () => import('@/views/net/TerminalControlView.vue'),
        meta: { title: '端末コントロール', layout: 'user' }
      },
      {
        path: 'batch',
        name: 'parent-batch',
        component: () => import('@/views/batch/BatchListView.vue'),
        meta: { title: 'バッチ一覧', layout: 'user' }
      },
      {
        path: 'batch-history',
        name: 'parent-batch-history',
        component: () => import('@/views/batch/BatchHistoryView.vue'),
        meta: { title: 'バッチ実行履歴', layout: 'user' }
      },
      {
        path: 'batch-ai-history',
        name: 'parent-batch-ai-history',
        component: () => import('@/views/batch/AiCallHistoryView.vue'),
        meta: { title: 'AI呼出履歴', layout: 'user' }
      },
      {
        path: 'internet-usage',
        name: 'parent-internet-usage',
        component: () => import('@/views/network/InternetUsageHistoryView.vue'),
        meta: { title: 'インターネット利用履歴', layout: 'user' }
      },
      {
        path: 'daily-report',
        name: 'parent-daily-report',
        component: () => import('@/views/daily-report/DailyReportView.vue'),
        meta: { title: '学習日報', layout: 'user' }
      },
      {
        path: 'todo',
        name: 'parent-todo',
        component: () => import('@/views/todo/TodoView.vue'),
        meta: { title: 'TODO', layout: 'user' }
      },
      {
        path: 'study-monitor',
        name: 'parent-study-monitor',
        component: () => import('@/views/study-monitor/StudyMonitorView.vue'),
        meta: { title: '学習状況モニター', layout: 'user' }
      },
      // 読書管理（親メニュー）。2.0 の英語読書を 書籍管理 / 書籍閲覧 に分けた
      {
        path: 'reading-books',
        name: 'parent-reading-books',
        component: () => import('@/views/reading/BookManagementView.vue'),
        meta: { title: '書籍管理', layout: 'user' }
      },
      {
        path: 'reading-reader',
        name: 'parent-reading-reader',
        component: () => import('@/views/reading/BookReaderView.vue'),
        meta: { title: '書籍閲覧', layout: 'user' }
      },
      // 日本語勉強（単語情報管理・単語テスト・単語勉強状況）
      {
        path: 'japanese-word',
        name: 'parent-japanese-word',
        component: () => import('@/views/japanese/JapaneseWordView.vue'),
        meta: { title: '単語情報管理', layout: 'user' }
      },
      {
        path: 'japanese-test',
        name: 'parent-japanese-test',
        component: () => import('@/views/japanese/JapaneseTestView.vue'),
        meta: { title: '単語テスト', layout: 'user' }
      },
      {
        path: 'japanese-word-status',
        name: 'parent-japanese-word-status',
        component: () => import('@/views/japanese/JapaneseWordStatusView.vue'),
        meta: { title: '単語勉強状況', layout: 'user' }
      },
      // 数学勉強（図形管理・図形作成・AI 生図）。2.0 の geometry.jsp / geometry_draw.jsp
      {
        path: 'geometry',
        name: 'parent-geometry',
        component: () => import('@/views/geometry/GeometryView.vue'),
        meta: { title: '図形管理', layout: 'user' }
      },
      {
        path: 'geometry-draw',
        name: 'parent-geometry-draw',
        component: () => import('@/views/geometry/GeometryDrawView.vue'),
        meta: { title: '図形作成', layout: 'user' }
      },
      {
        // AI 生図（画像から作図）。画面（流れ）だけで処理は未実装
        path: 'geometry-ai',
        name: 'parent-geometry-ai',
        component: () => import('@/views/geometry/GeometryAiView.vue'),
        meta: { title: 'AI 生図', layout: 'user' }
      },
      {
        path: ':screen',
        name: 'parent-screen',
        component: () => import('@/views/prototype/PrototypePageView.vue'),
        meta: { title: 'UI移行画面', layout: 'user' }
      }
    ]
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '403', layout: 'blank' }
  },
  {
    path: '/404',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '404', layout: 'blank' }
  },
  {
    path: '/500',
    name: 'server-error',
    component: () => import('@/views/error/ServerErrorView.vue'),
    meta: { title: '500', layout: 'blank' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

/**
 * 分割して読み込む画面（遅延読み込み）の取得に失敗したか。
 *
 * <p>配備で画面のファイル名（ハッシュ）が変わると、**開いたままのタブ**は古い名前を取りに行き、
 * nginx が index.html を返す（＝MIME が違うので読み込めない）。実測: 授業の詳細へ進めず
 * 「Failed to fetch dynamically imported module」が出て、そのまま操作できなくなった。</p>
 */
export function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '')
  return message.includes('Failed to fetch dynamically imported module')
    || message.includes('Importing a module script failed')
    || message.includes('error loading dynamically imported module')
}

/** 読み込み直しを 1 回だけに抑える印（配備直後に繰り返し読み込み直さない）。 */
export const CHUNK_RELOAD_KEY = 'study21.chunkReload'

/**
 * 画面の読み込みに失敗したところから復帰する: **ページ全体を読み込み直して**新しいビルドを取りに行く。
 *
 * <p>アプリの中（クライアント側の遷移）では、消えたファイルを取りに行けない。
 * 2 回続けて失敗したときは何もしない（壊れた状態で無限に再読み込みしない）。</p>
 *
 * @param navigate 読み込み直す方法（テストから差し替えられる）
 * @returns 読み込み直したか
 */
export function recoverFromChunkLoadError(
  error: unknown,
  fullPath: string,
  navigate: (path: string) => void = (path) => { window.location.assign(path) }
): boolean {
  if (!isChunkLoadError(error)) return false
  try {
    if (window.sessionStorage.getItem(CHUNK_RELOAD_KEY) === '1') return false
    window.sessionStorage.setItem(CHUNK_RELOAD_KEY, '1')
  } catch {
    // sessionStorage が使えない環境では読み込み直さない（今までどおりエラー表示）
    return false
  }
  navigate(fullPath)
  return true
}

export function createAppRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior: () => ({ top: 0 })
  })

  router.beforeEach((to) => {
    const auth = useAuthStore()
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return to.path.startsWith('/admin') ? '/admin/login' : '/login'
    }
    if ((to.path === '/login' || to.path === '/admin/login' || to.path === '/register') && auth.isAuthenticated) {
      if (auth.role === 'ADMIN') return '/admin/home'
      if (auth.role === 'GUARDIAN') return '/parent/home'
      return '/student/home'
    }
    return true
  })

  /*
   * 画面の読み込みに失敗したら、**ページ全体を読み込み直す**（アプリの中だけでは新しい
   * ファイルを取りに行けない）。配備の直後に開いたままのタブがこれで自動的に復帰する。
   * 2 回続けて失敗したときは読み込み直さない（壊れた状態で無限に再読み込みしない）。
   */
  router.onError((error, to) => {
    if (to === undefined) return
    recoverFromChunkLoadError(error, to.fullPath)
  })

  // どこかへ進めたら印を消す（次の配備でもまた自動で復帰できるように）
  router.afterEach(() => {
    try {
      window.sessionStorage.removeItem(CHUNK_RELOAD_KEY)
    } catch {
      // 使えない環境では何もしない
    }
  })

  return router
}
