<script setup lang="ts">
import { computed, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import EssayForm from '@/features/english-essay/components/EssayForm.vue'

/**
 * 英作文AI添削【新規登録／編集】の**ページ**（2.0 の `english_essay.jsp?mode=new` 相当）。
 *
 * <p>2.0 は同じ JSP を `?mode=new` で開き、**モーダルの骨格をそのまま全画面**にしていた
 * （`body.essay-new-page`）。2.1 は最初からページなので、`EssayForm`（= 2.0 の
 * `essayEditorContent` の中身）をそのまま並べ、**【一覧へ戻る】と【保存】を英雄の右**
 * （2.0 の `essay-hero-actions` と同じ位置）に置く。足元の【キャンセル】は**置かない**
 * （利用者の指示。離れる導線は英雄の【一覧へ戻る】とブラウザの戻るだけ）。</p>
 *
 * <p>一覧の【英作文新規】と行の【編集】は、ここへ**ページ遷移**する（モーダルは開かない）。
 * 編集も**同じページ**を使う（2.0 と同じ振る舞い。利用者の要求）。親エリアは閲覧のみなので、
 * このルートは置かない（`router/index.ts`）。</p>
 *
 * <p>**未保存のまま離れようとしたら確認する**（2.0 の離脱確認に倣う）。画面の中の移動は
 * `onBeforeRouteLeave` が受け持つので、【一覧へ戻る】・ブラウザの戻る・そのほかのルート遷移の
 * どれでも同じように聞く。</p>
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

/** 2.0 の離脱確認（無ければ簡潔な日本語を 1 つ入れる。利用者の指示）。 */
const LEAVE_MESSAGE = '保存していない変更があります。このまま移動しますか？'

/** 中身（下書き・画像・OCR・保存）は `EssayForm` が受け持つ。 */
const form = ref<InstanceType<typeof EssayForm> | null>(null)

/** このページで**提出できる**か（親エリアは閲覧のみ）。 */
const canSubmit = computed<boolean>(() => areaOf(route.path) !== 'parent')

/** URL の `:essayId`（新規は空）。 */
const essayId = computed<string>(() => {
  const value = route.params.essayId
  return Array.isArray(value) ? (value[0] ?? '') : String(value ?? '')
})

/** 一覧へ戻る道（エリアは URL から取る）。 */
const listPath = computed<string>(() => `/${areaOf(route.path)}/english-essay`)

/** URL の先頭（`/admin` `/student` `/parent`）。 */
function areaOf(path: string): string {
  return path.split('/').filter((segment) => segment.length > 0)[0] ?? 'student'
}

/** 未保存の変更があるか（`EssayForm` が下書きから判定する）。 */
function dirty(): boolean {
  return form.value?.dirty === true
}

/** 保存する（【保存】は英雄の右）。保存できたら詳細へ移る。 */
async function save(): Promise<void> {
  await form.value?.save()
}

/** 保存できた（詳細へ移る）。 */
function onSaved(id: string): void {
  void router.push(`/${areaOf(route.path)}/english-essay/${id}`)
}

/** 添削を受付けた（詳細が結果を様子見するので、そのまま詳細へ）。 */
function onGraded(id: string): void {
  void router.push(`/${areaOf(route.path)}/english-essay/${id}`)
}

/** 知らせ（成功／失敗）。 */
function notify(tone: 'success' | 'danger', message: string): void {
  if (tone === 'success') {
    toast.success(message)
  } else {
    toast.danger(message)
  }
}

/**
 * 離れようとしたら確認する（了承したときだけ通す）。
 *
 * <p>画面の中の移動（【一覧へ戻る】・ブラウザの戻る・そのほかのルート遷移）はここを通る。</p>
 */
onBeforeRouteLeave((): boolean => {
  if (!dirty()) {
    return true
  }
  return window.confirm(LEAVE_MESSAGE)
})
</script>

<template>
  <div class="ee-page-form" data-ee-new-page>
    <EssayForm
      v-if="canSubmit" ref="form" :essay-id="essayId || null"
      @saved="onSaved" @graded="onGraded" @notify="notify"
    >
      <template #actions>
        <RouterLink class="btn btn--secondary" data-ee-list-back :to="listPath">
          <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
        </RouterLink>
        <button type="button" class="btn btn--primary" data-ee-save @click="save">
          <AppIcon name="check" size="sm" /> 保存
        </button>
      </template>
    </EssayForm>

    <!-- 親エリアは提出しない（このルートは置いていないが、直接開かれても案内する） -->
    <p v-else class="ee-hint" data-ee-readonly>この画面は閲覧のみです。</p>
  </div>
</template>
