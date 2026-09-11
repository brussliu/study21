import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export type MobileArea = 'admin' | 'student' | 'parent'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/UserLoginView.vue'),
    meta: { title: 'ログイン' }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/login/ForgotPasswordView.vue'),
    meta: { title: 'パスワード再設定' }
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/login/AdminLoginView.vue'),
    meta: { title: '管理者ログイン' }
  },
  {
    path: '/admin',
    component: () => import('@/layouts/MobileLayout.vue'),
    props: { area: 'admin' as MobileArea },
    children: [
      { path: '', redirect: '/admin/home' },
      {
        path: 'home',
        name: 'admin-home',
        component: () => import('@/views/admin/AdminHomeView.vue'),
        meta: { title: 'ホーム' }
      }
    ]
  },
  {
    path: '/student',
    component: () => import('@/layouts/MobileLayout.vue'),
    props: { area: 'student' as MobileArea },
    children: [
      { path: '', redirect: '/student/home' },
      {
        path: 'home',
        name: 'student-home',
        component: () => import('@/views/student/StudentHomeView.vue'),
        meta: { title: 'ホーム' }
      }
    ]
  },
  {
    path: '/parent',
    component: () => import('@/layouts/MobileLayout.vue'),
    props: { area: 'parent' as MobileArea },
    children: [
      { path: '', redirect: '/parent/home' },
      {
        path: 'home',
        name: 'parent-home',
        component: () => import('@/views/parent/ParentHomeView.vue'),
        meta: { title: 'ホーム' }
      }
    ]
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '403' }
  },
  {
    path: '/404',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '404' }
  },
  {
    path: '/500',
    name: 'server-error',
    component: () => import('@/views/error/ServerErrorView.vue'),
    meta: { title: '500' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior: () => ({ top: 0 })
  })
}
