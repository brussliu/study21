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

  return router
}
