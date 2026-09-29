<script setup lang="ts">
import { computed, onMounted } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { detailVersionCountLabels, detailVersionOrigin } from '@/features/japanese-word/wordMapper'
import type { JpnDetailVersion } from '@/api/japanese'
import type { useJpnEditorStore } from '@/features/japanese-word/editorStore'
import '@/features/japanese/japanese.css'

/**
 * 詳細の「版の履歴」。
 *
 * AI の取得も画面の保存も**版を積み上げる**（新しい版が生效中、前の版は ARCHIVED）。
 * ここでは版ごとに「誰が作ったか」「いつ取得したか」「段落が何件あるか」を出し、
 * 【この版を使う】で**古い版に戻せる**（版は消えない）。
 *
 * 楽観的ロックは `バージョン`。**切り替えたい版の**値をそのまま送る（版ごとに別の値なので、
 * 表示中の詳細の値ではない）。ほかの操作が先に更新していれば 409 になり、一覧を読み直して
 * 「読み直してください」と伝える。
 */

const props = defineProps<{ store: ReturnType<typeof useJpnEditorStore> }>()
const emit = defineEmits<{ activated: [message: string] }>()

/** 版の一覧（ストアの状態。prop 越しでも必ず配列にする）。 */
const versions = computed<JpnDetailVersion[]>(() => props.store.versions ?? [])

/** 生效中の版（表示中の詳細と一致するかは detailId で見る）。 */
const activeDetailId = computed(() => props.store.loadedDetail?.detail?.detailId ?? null)

function isCurrent(version: { detailId: number; active: boolean }): boolean {
  return version.active || version.detailId === activeDetailId.value
}

onMounted(() => {
  void props.store.loadDetailVersions()
})

/** 日時は「2026/10/01 09:00」の形にする（秒は出さない）。 */
function formatAt(value: string | null): string {
  if (value === null || value === '') {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (part: number): string => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function use(version: JpnDetailVersion): Promise<void> {
  // 楽観的ロックは**その版の** バージョン（一覧が返す値）。表示中の版の値ではない
  const ok = await props.store.activateDetailVersion(version.detailId, version.version)
  if (ok) {
    emit('activated', '有効な版を切り替えました。')
  }
}
</script>

<template>
  <section class="jp-version" data-jp-versions>
    <div class="jp-version__head">
      <h3 class="jp-version__title">
        <AppIcon name="clock" size="sm" /> 版の履歴
      </h3>
      <button
        type="button" class="btn btn--secondary btn--sm" :disabled="store.versionsLoading"
        data-jp-versions-reload @click="store.loadDetailVersions()"
      >
        再読み込み
      </button>
    </div>
    <p class="jp-hint">
      AI の取得も保存も版を積み上げます（新しい版が生效中）。古い版を選ぶと、その内容に戻せます。
    </p>

    <p v-if="store.versionsLoading && versions.length === 0" class="jp-hint" data-jp-versions-loading>
      版の履歴を読み込んでいます…
    </p>
    <p v-else-if="store.versionsError !== ''" class="alert alert--danger" data-jp-versions-error>
      {{ store.versionsError }}
    </p>
    <p v-else-if="versions.length === 0" class="jp-hint" data-jp-versions-empty>
      まだ版がありません（AI の取得か保存で作られます）。
    </p>

    <ol v-else class="jp-version__list">
      <li
        v-for="version in versions" :key="version.detailId"
        class="jp-version__item" :class="{ 'is-current': isCurrent(version) }" data-jp-version-item
      >
        <div class="jp-version__row">
          <span class="jp-version__no" data-jp-version-no>版 {{ version.contentVersion }}</span>
          <span v-if="isCurrent(version)" class="badge badge--success" data-jp-version-active>生效中</span>
          <span class="badge badge--neutral">{{ version.manual ? '人が作成' : 'AI が作成' }}</span>
          <span class="jp-version__origin">{{ detailVersionOrigin(version) }}</span>
          <span class="jp-version__at">取得 {{ formatAt(version.fetchedAt) }}</span>
          <span v-if="version.note" class="jp-version__note">{{ version.note }}</span>
          <button
            v-if="!isCurrent(version)" type="button" class="btn btn--secondary btn--sm jp-version__use"
            :disabled="store.activatingVersionId !== null" data-jp-version-use @click="use(version)"
          >
            この版を使う
          </button>
        </div>
        <ul class="jp-version__counts">
          <li v-for="entry in detailVersionCountLabels(version)" :key="entry.label">
            <span class="jp-version__count-label">{{ entry.label }}</span>
            <span class="jp-version__count-value">{{ entry.count }}</span>
          </li>
        </ul>
      </li>
    </ol>

    <p v-if="store.activateError !== ''" class="alert alert--warning" data-jp-version-error>
      {{ store.activateError }}
    </p>
  </section>
</template>
