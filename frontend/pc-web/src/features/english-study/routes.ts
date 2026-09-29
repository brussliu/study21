import type { RouteRecordRaw } from 'vue-router'
import type { AppArea } from '@/config/menuRegistry'

/** 旧 URL を保ち、三つのエリアで同じ六画面を使用する。 */
export function englishStudyRoutes(area: AppArea): RouteRecordRaw[] {
  return (['word', 'phrase'] as const).flatMap(kind => (['test', 'manage', 'status'] as const).map(mode => {
    const label = kind === 'word' ? '単語' : '熟語'
    const path = mode === 'test' ? (kind === 'word' ? 'testword' : 'phrase-test') : mode === 'manage' ? kind : `${kind}-status`
    return { path, name: `${area}-english-${kind}-${mode}`, component: () => import('@/views/english/study/EnglishStudyView.vue'), props: { kind, mode }, meta: { title: `${label}${mode === 'test' ? 'テスト' : mode === 'manage' ? '情報管理' : '勉強状況'}`, layout: area === 'admin' ? 'admin' as const : 'user' as const } }
  }))
}
