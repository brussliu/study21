<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import '@/features/geometry/geometry.css'
import '@/features/geometry/geometry-ai.css'

/**
 * 図形一覧の【新規】で開く**作成方法の選択ダイアログ**。
 *
 * 2 つの方法だけを出す（利用者の指定）。
 *
 * ・手動で作図する（現在の方法）… 作図画面（`/{area}/geometry-draw`）を今までどおり開く
 * ・AI で作図する（画像から）… AI 生図の画面（`/{area}/geometry-ai`）を開く
 *
 * **AI 生図が無効（設定 `GEOMETRY_AI_ENABLED=false`）のときは AI の選択肢を出さない**
 * （一覧画面は、選択肢が 1 つだけならダイアログを出さずに作図画面を開く）。
 *
 * 画面遷移はこの小窓では行わず、`manual` / `ai` を親（一覧画面）へ伝えるだけにする
 * （遷移先を知っているのは一覧画面。テストもしやすい）。
 *
 * 閉じ方は右上の × と【キャンセル】だけにする（背景クリックでは閉じない。全画面共通のルール）。
 */
const props = withDefaults(defineProps<{
  /** AI 生図が使えるか（null = まだ設定を読めていない。読めるまでは出す） */
  aiEnabled?: boolean | null
}>(), { aiEnabled: null })

const emit = defineEmits<{ close: []; manual: []; ai: [] }>()

/** AI の選択肢を出すか（設定が読めていて無効なら出さない）。 */
const showAi = computed(() => props.aiEnabled !== false)
</script>

<template>
  <Teleport to="body">
    <div class="overlay">
      <section
        class="dialog dialog--sm gm-create-dialog" role="dialog" aria-modal="true"
        aria-label="新規の作成方法" data-gm-create-dialog
      >
        <header class="dialog__head">
          <h2 class="dialog__title"><AppIcon name="plus" size="sm" /> 新規作成</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="emit('close')">
            <AppIcon name="x" size="sm" />
          </button>
        </header>

        <div class="dialog__body">
          <p class="gm-hint">作成の方法を選んでください。</p>
          <div class="gm-create-dialog__choices">
            <!-- 現在の方法。今までどおり作図画面を開く（E2E の入口） -->
            <button type="button" class="gm-choice" data-gm-create-manual @click="emit('manual')">
              <AppIcon name="edit" size="lg" />
              <span class="gm-choice__label">手動で作図する（現在の方法）</span>
              <span class="gm-choice__note">
                GeoGebra のツールで自分で作図します。作図した結果は図形一覧に保存されます。
              </span>
            </button>

            <!-- AI 生図。設定で無効なら出さない（使えない導線を見せない） -->
            <button v-if="showAi" type="button" class="gm-choice" data-gm-create-ai @click="emit('ai')">
              <AppIcon name="wand" size="lg" />
              <span class="gm-choice__label">
                AI で作図する（画像から）
                <span class="badge badge--success" data-gm-create-ai-badge>使えます</span>
              </span>
              <span class="gm-choice__note">
                問題集や教科書の図を画像で読み取り、AI が GeoGebra の作図を作ります。
                できた作図は<strong>作図画面で確認・調整してから</strong>保存します。
              </span>
            </button>
          </div>
        </div>

        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-gm-create-cancel @click="emit('close')">
            <AppIcon name="x" size="sm" /> キャンセル
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
