import type { RouteRecordRaw } from 'vue-router'
import type { AppArea } from '@/config/menuRegistry'

export function practiceDemoRoutes(area: AppArea): RouteRecordRaw[] {
  return (['cloze', 'intensive'] as const).flatMap(kind => {
    const base = kind === 'cloze' ? 'english-cloze' : 'english-reading-intensive'
    const title = kind === 'cloze' ? '英語穴埋め問題' : '英語読解・精読'
    const meta = { title, layout: area === 'admin' ? 'admin' as const : 'user' as const }
    return [
      { path: base, name: `${area}-${kind}-list`, component: () => import('@/views/english/practice/LessonListView.vue'), props: { kind }, meta },
      { path: `${base}/new`, name: `${area}-${kind}-new`, component: () => import('@/views/english/practice/LessonEditorView.vue'), props: { kind }, meta },
      { path: `${base}/:recordId/edit`, name: `${area}-${kind}-edit`, component: () => import('@/views/english/practice/LessonEditorView.vue'), props: { kind }, meta },
      { path: kind === 'cloze' ? `${base}/:recordId/:questionId` : `${base}/:recordId`, name: `${area}-${kind}-study`, component: () => import('@/views/english/practice/LessonStudyView.vue'), props: { kind }, meta },
    ]
  })
}
