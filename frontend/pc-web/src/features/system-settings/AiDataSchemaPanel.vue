<script setup lang="ts">
/**
 * **Data TAB**（AI 出力データ構造の表示。batC51 / batC52 で共用）。
 *
 * DTO が AI 出力データ構造の唯一の定義なので、画面はサーバーが DTO から生成した
 * JSON Schema をそのまま受け取って表示するだけにする（画面に固定の JSON を持たない）。
 * DTO を直せば、この表示も、プロンプトへ注入する出力形式も同時に変わる。
 *
 * 見るだけの TAB（スキーマは編集できない）。表示は 2 つ:
 * 1. 項目構造（フィールド名・型・説明・必須。入れ子は開いて見る）
 * 2. JSON Schema（整形した本文。そのまま確認・コピーできる。長いので枠の中を縦に送る）
 *
 * 見た目は `aiDataSchema.css`（1 か所）が持つ。素の DOM で描く側
 * （`aiDataSchemaPanel.ts`。日本語単語AI の Data TAB）と共有するため、この部品は
 * 自前の `<style>` を持たない（片方だけ規則が無いと、そこだけ見た目が崩れる）。
 */
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { loadAiResponseSchema } from '@/api/system-settings'
import { fieldsOfSchema, flattenSchemaFields } from '@/features/system-settings/aiSchemaView'
import '@/features/system-settings/aiDataSchema.css'

const props = defineProps<{
  /** バッチコード（batC51 / batC52）。 */
  task: string
}>()

const dto = ref('')
const schema = ref<Record<string, unknown> | null>(null)
const loading = ref(true)
const error = ref('')
/** 開いている行（`入れ子.名前` のような path）。 */
const expandedPaths = ref<string[]>([])

const fields = computed(() => (schema.value ? fieldsOfSchema(schema.value) : []))
const rows = computed(() =>
  flattenSchemaFields(fields.value, (path) => expandedPaths.value.includes(path))
)
const schemaText = computed(() => (schema.value ? JSON.stringify(schema.value, null, 2) : ''))

function toggle(path: string): void {
  expandedPaths.value = expandedPaths.value.includes(path)
    ? expandedPaths.value.filter((item) => item !== path)
    : [...expandedPaths.value, path]
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await loadAiResponseSchema(props.task)
    const data = response.data
    if (!response.success || !data) {
      error.value = response.message || 'AI 出力データ構造を読み込めませんでした。'
      schema.value = null
      return
    }
    dto.value = data.dto
    schema.value = data.schema
    // 最初は入れ子を閉じておく（項目の並びが見やすい）
    expandedPaths.value = []
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'AI 出力データ構造を読み込めませんでした。'
    schema.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="ai-data-schema" :data-ai-data-schema="task">
    <!-- 1 行のヒントは短く（設定ページの hint は 1 行に収める決まり）。詳しい説明は下の行に置く -->
    <p class="setting-help ai-data-schema__note" data-ai-data-note>
      AI が返すデータの形は DTO（<code>{{ dto || task }}</code>）が唯一の定義です（見るだけ）。
    </p>
    <p class="ai-data-schema__lead">
      ここには DTO から自動生成した JSON Schema を表示します。<strong>ここでは編集できません</strong>。
      DTO を直すと、この内容と AI へ渡す出力形式（プロンプトへ注入する文）が同時に変わります。
    </p>

    <p v-if="loading" class="ai-data-schema__state" data-ai-data-loading>
      <AppIcon name="rotate" /> 読み込んでいます...
    </p>
    <p v-else-if="error" class="ai-data-schema__state is-error" data-ai-data-error>
      {{ error }}
    </p>

    <template v-else>
      <section class="ai-data-schema__block" data-ai-data-structure>
        <h5 class="ai-data-schema__title"><AppIcon name="list" /> 項目構造</h5>
        <table class="ai-data-schema__table">
          <thead>
            <tr>
              <th scope="col">フィールド名</th>
              <th scope="col">型</th>
              <th scope="col">説明</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.path"
              class="ai-data-schema__row"
              :data-ai-data-field="row.path"
              :data-ai-data-depth="row.depth"
            >
              <td class="ai-data-schema__name">
                <span class="ai-data-schema__indent" :style="{ width: `${row.depth * 16}px` }"></span>
                <button
                  v-if="row.hasChildren"
                  type="button"
                  class="ai-data-schema__toggle"
                  :aria-expanded="row.expanded"
                  :data-ai-data-toggle="row.path"
                  @click="toggle(row.path)"
                >
                  {{ row.expanded ? '▼' : '▶' }}
                </button>
                <span v-else class="ai-data-schema__toggle-space"></span>
                <code>{{ row.name }}</code>
                <span v-if="row.required" class="ai-data-schema__required" :data-ai-data-required="row.path">
                  必須
                </span>
              </td>
              <td class="ai-data-schema__type"><code>{{ row.type }}</code></td>
              <td class="ai-data-schema__description">{{ row.description }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="ai-data-schema__block" data-ai-data-json>
        <h5 class="ai-data-schema__title"><AppIcon name="code" /> JSON Schema</h5>
        <!-- 縦に送れる枠はキーボードでも送れること（tabindex）。見た目は aiDataSchema.css -->
        <pre class="ai-data-schema__json" tabindex="0" role="region" aria-label="JSON Schema">{{ schemaText }}</pre>
      </section>
    </template>
  </div>
</template>
