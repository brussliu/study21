import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * mobile-web は現在「空の骨組み」だけを置いている。
 * 業務画面は後で追加する（追加するときは src/views/ に画面を作り、ここへルートを足す）。
 *
 * 追加予定の構成（README.md 参照）:
 *   layouts/    画面共通のレイアウト
 *   components/ 部品
 *   stores/     Pinia ストア
 *   api/        バックエンド呼び出し
 *   config/     メニュー定義など
 */
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/PreparationView.vue'),
    meta: { title: '準備中' }
  }
]

export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior: () => ({ top: 0 })
  })
}
