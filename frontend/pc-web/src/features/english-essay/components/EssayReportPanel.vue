<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { ESSAY_LEVEL_LABELS, type EssayGrading, type EssayReportSide } from '../types'

/**
 * 添削レポート（2.0 の `renderReport` と同じ 5 ブロックの並び）。
 *
 * <p>2.0 の `js/english_essay.js` の `renderReport` は `#essayReport` に次の順で書く:</p>
 *
 * <ol>
 *   <li>作文タイトル（`.report-essay-title`。結果の言語に追随）</li>
 *   <li>得点と総合コメント（`.report-overview` ＝ 190px : 1fr の 2 列。輪・
 *       `EIKEN WRITING FEEDBACK`・題・要約・タグ 3 件）</li>
 *   <li>4 観点（`.rubric-grid` ＝ 4 列。観点名・得点・`<meter>`・講評）</li>
 *   <li>修正ポイント（`.correction-list`）と改善後の作文例（`.model-answer`）を
 *       `.feedback-grid`（1.18fr : .82fr）で 2 列に</li>
 *   <li>英検対策のワンポイント＋注意書き（`.next-advice` の橙の枠 1 つ）</li>
 * </ol>
 *
 * <p>2.1 の足しものは**「設問が求めていること」**（`taskRequirements`）の節だけで、
 * 総合評価と 4 観点の間に置く。2.0 の各面に付いていた拡大表示はそのまま残す。</p>
 *
 * <p>**結果の言語の切り替えは 2.0 と同じくレポートの見出し（result-card の head）に置く**
 * ので、この部品は `lang` を受け取るだけ（切り替えの面は持たない）。
 * 历次の切り替えも呼ぶ側（詳細ページ）が持つ。</p>
 */
const props = defineProps<{
  /** 表示する回の添削結果。 */
  grading: EssayGrading
  /** 結果の言語（2.0 の `essayResultLanguageSelect`。既定は日本語）。 */
  lang?: 'japanese' | 'chinese'
}>()

type ReportLang = 'japanese' | 'chinese'
type ZoomKind = 'overview' | 'rubric' | 'corrections' | 'model' | 'advice'

const toast = useToast()

/** いま選ばれている言語（親が持つ。渡されなければ日本語）。 */
const lang = computed<ReportLang>(() => (props.lang === 'chinese' ? 'chinese' : 'japanese'))

/** 拡大表示中のブロック（null なら閉じている）。 */
const zoom = ref<ZoomKind | null>(null)

/** いま選ばれている言語の本文。 */
const side = computed<EssayReportSide>(
  () => (lang.value === 'chinese' ? props.grading.chinese : props.grading.japanese)
)

const levelLabel = computed<string>(() => ESSAY_LEVEL_LABELS[props.grading.level])

/**
 * 作文タイトル（2.0 の `report-essay-title`）。
 *
 * <p>結果の言語に追随し、選んだ言語の題が無ければもう一方を出す（2.0 と同じ）。</p>
 */
const reportTitle = computed<string>(() => (lang.value === 'chinese'
  ? props.grading.titleZh || props.grading.titleJa
  : props.grading.titleJa || props.grading.titleZh))

/**
 * リングに出す達成率（%）。得点か満点が分からない回は 0（リングを空にする）。
 */
const percent = computed<number>(() => {
  const { score, maxScore } = props.grading
  if (score === undefined || maxScore === undefined || maxScore <= 0) {
    return 0
  }
  return Math.round((score / maxScore) * 100)
})

/**
 * 得点の表示。
 *
 * <p>満点が分かるときだけ `26 / 32`。満点が分からない回（2.0 の古い形式の移行データ）は
 * **点数だけ**を出す。**得点そのものが分からない回は `—`**
 * （`0 / 16` のような嘘の数字を出さない）。</p>
 */
const scoreText = computed<string>(() => {
  const { score, maxScore } = props.grading
  if (score === undefined) {
    return '—'
  }
  return maxScore === undefined ? String(score) : `${score} / ${maxScore}`
})

/**
 * リングの下に出す満点（2.0 の `.score-ring span` ＝ `/ 32`。分からない回は出さない）。
 */
const ringMax = computed<string>(() => {
  const { maxScore } = props.grading
  return maxScore === undefined ? '' : `/ ${maxScore}`
})

/**
 * 設問が求めていること（**配列の形**。箇条書きで出す）。
 *
 * <p>オブジェクトの形（`commentJa` / `commentZh`）のときは空。</p>
 */
const requirementItems = computed<string[]>(() => {
  const requirements = props.grading.taskRequirements
  return requirements !== null && requirements.kind === 'list' ? requirements.items : []
})

/**
 * 設問が求めていること（**オブジェクトの形**の 1 段落）。
 *
 * <p>結果の言語に追随し、その言語が無ければもう一方を出す（2.0 と同じ）。</p>
 */
const requirementNote = computed<string>(() => {
  const requirements = props.grading.taskRequirements
  if (requirements === null || requirements.kind !== 'note') {
    return ''
  }
  return lang.value === 'chinese'
    ? (requirements.zh || requirements.ja)
    : (requirements.ja || requirements.zh)
})

/** 設問が求めていることを出すか（どちらの形でも、中身が有れば出す）。 */
const hasRequirements = computed<boolean>(
  () => requirementItems.value.length > 0 || requirementNote.value !== ''
)

/** 修正ポイントを出すか（古い形式の移行データには無い）。 */
const hasCorrections = computed<boolean>(() => side.value.corrections.length > 0)

/** 改善後の作文例を出すか（古い形式の移行データには無い）。 */
const hasModelAnswer = computed<boolean>(() => props.grading.modelAnswer !== '')

/** 英検対策のワンポイントを出すか。 */
const hasAdvice = computed<boolean>(() => side.value.advice !== '' || side.value.notice !== '')

const ZOOM_TITLES: Record<ZoomKind, string> = {
  overview: '総合評価',
  rubric: '4 観点の評価',
  corrections: '修正ポイント',
  model: '改善後の作文例',
  advice: '英検対策のワンポイント'
}

const zoomTitle = computed<string>(() => (zoom.value === null ? '' : ZOOM_TITLES[zoom.value]))

function openZoom(kind: ZoomKind): void {
  zoom.value = kind
}

function closeZoom(): void {
  zoom.value = null
}

/** 拡大表示は Esc でも閉じられる（背景クリックでは閉じない）。 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && zoom.value !== null) {
    closeZoom()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

/** 改善後の作文例を写す（写せない環境では知らせるだけ）。 */
async function copyModelAnswer(): Promise<void> {
  const text = props.grading.modelAnswer
  const clipboard = navigator.clipboard
  if (!clipboard || typeof clipboard.writeText !== 'function') {
    toast.danger('コピーできませんでした。お使いの環境ではクリップボードを使えません。')
    return
  }
  try {
    await clipboard.writeText(text)
    toast.success('改善後の作文例をコピーしました。')
  } catch {
    toast.danger('コピーできませんでした。')
  }
}
</script>

<template>
  <div class="ee-report" data-ee-report>
    <!-- 作文タイトル（2.0 の「作文タイトル」。結果の言語に追随する） -->
    <div v-if="reportTitle !== ''" class="ee-report-title" data-ee-report-title>
      <span class="ee-report-title__label">{{ lang === 'chinese' ? '作文标题' : '作文タイトル' }}</span>
      <h3 class="ee-report-title__value">{{ reportTitle }}</h3>
    </div>

    <!-- 2.0 の `renderReport` と同じ 5 ブロックの順序（2.0 には無い「設問が求めていること」
         だけを 2.1 の足しものとして、総合評価と 4 観点の間に置く）。
         1. 作文タイトル（上）→ 2. 得点と総合コメント → 3. 4 観点 → 4. 修正ポイント／改善後の作文例
         → 5. 英検対策のワンポイント＋注意書き -->

    <!-- 2. 得点と総合コメント（2.0 の `.report-overview` ＝ 190px : 1fr の 2 列）。
         2.0 の総合評価には見出しが無いので、拡大表示だけ右上の小さなボタンにする
         （節の題を足さずに、2.1 の拡大表示を残す） -->
    <div class="ee-overview">
      <div class="ee-report-score" :style="{ '--ee-score': percent }">
        <div class="ee-report-score__inner">
          <strong class="ee-report-score__value" data-ee-score>{{ scoreText }}</strong>
          <span class="ee-report-score__max">{{ ringMax }}</span>
          <span class="ee-report-score__level" data-ee-level>{{ levelLabel }}</span>
        </div>
      </div>

      <div class="ee-overview__body">
        <span class="ee-report__label">EIKEN WRITING FEEDBACK</span>
        <h3 class="ee-overview__title">{{ side.title }}</h3>
        <p class="ee-overview__summary" data-ee-summary>{{ side.summary }}</p>
        <div class="ee-tags">
          <span
            v-for="(tag, index) in side.tags" :key="tag"
            class="badge ee-tag" :class="{ 'is-warn': index >= 2 }"
          >{{ tag }}</span>
        </div>
      </div>

      <button
        type="button" class="btn btn--secondary btn--sm ee-overview__zoom" data-ee-zoom
        title="総合評価を拡大表示" aria-label="総合評価を拡大表示" @click="openZoom('overview')"
      >
        <AppIcon name="zoom-in" size="sm" />
      </button>
    </div>

    <!-- 2.1 の足しもの。設問が求めていること（採点の観点）。配列なら箇条書き、オブジェクトなら 1 段落 -->
    <section v-if="hasRequirements" class="ee-sec ee-requirements-sec">
      <header class="ee-sec__head">
        <h4 class="ee-sec__title">設問が求めていること</h4>
      </header>

      <ul v-if="requirementItems.length > 0" class="ee-requirements">
        <li
          v-for="(requirement, index) in requirementItems" :key="index"
          class="ee-requirement" data-ee-task-requirement
        >
          {{ requirement }}
        </li>
      </ul>
      <p v-else class="ee-requirement" data-ee-task-requirement>{{ requirementNote }}</p>
    </section>

    <!-- 3. 4 観点（2.0 の `.rubric-grid` ＝ 4 列。古い形式の移行データには無いので、
         そのときは見出しごと出さない） -->
    <section v-if="side.rubric.length > 0" class="ee-panel ee-rubric-panel">
      <header class="ee-panel__head">
        <span class="ee-panel__title">4 観点の評価</span>
        <button
          type="button" class="btn btn--secondary btn--sm" data-ee-zoom
          @click="openZoom('rubric')"
        >
          <AppIcon name="zoom-in" size="sm" /> 拡大表示
        </button>
      </header>

      <div class="ee-rubric">
        <div v-for="item in side.rubric" :key="item.key" class="ee-rubric__item" data-ee-rubric>
          <div class="ee-rubric__row">
            <span class="ee-rubric__key">{{ item.key }}</span>
            <strong class="ee-rubric__score">{{ item.score }} / {{ item.maxScore }}</strong>
          </div>
          <meter class="ee-rubric__meter" min="0" :max="item.maxScore" :value="item.score"></meter>
          <p class="ee-rubric__note">{{ item.note }}</p>
        </div>
      </div>
    </section>

    <!-- 4. 修正ポイント（左の広い面）と改善後の作文例（右）。2.0 の `.feedback-grid` ＝ 1.18fr : .82fr。
         どちらも無ければブロックごと出さない -->
    <div v-if="hasCorrections || hasModelAnswer" class="ee-columns">
      <article v-if="hasCorrections" class="ee-panel">
        <header class="ee-panel__head">
          <span class="ee-panel__title">
            修正ポイント<span class="ee-panel__sub">{{ side.corrections.length }} 件の改善候補</span>
          </span>
          <button
            type="button" class="btn btn--secondary btn--sm" data-ee-zoom
            @click="openZoom('corrections')"
          >
            <AppIcon name="zoom-in" size="sm" /> 拡大表示
          </button>
        </header>

        <ol class="ee-corrections">
          <li
            v-for="(item, index) in side.corrections" :key="index"
            class="ee-correction" data-ee-correction
          >
            <span class="ee-correction__no">{{ index + 1 }}</span>
            <div>
              <p class="ee-correction__pair">
                <del>{{ item.original }}</del>
                <span class="ee-correction__arrow">→</span>
                <ins>{{ item.corrected }}</ins>
              </p>
              <p class="ee-correction__reason">
                <span class="badge ee-correction__cat">{{ item.category }}</span>{{ item.reason }}
              </p>
            </div>
          </li>
        </ol>
      </article>

      <article v-if="hasModelAnswer" class="ee-panel">
        <header class="ee-panel__head">
          <span class="ee-panel__title">
            改善後の作文例<span
              v-if="grading.wordRequirement !== ''" class="ee-panel__sub"
            >語数の目安 {{ grading.wordRequirement }}</span>
          </span>
          <div class="ee-panel__actions">
            <button
              type="button" class="btn btn--secondary btn--sm" data-ee-zoom
              @click="openZoom('model')"
            >
              <AppIcon name="zoom-in" size="sm" /> 拡大表示
            </button>
            <button
              type="button" class="btn btn--secondary btn--sm" data-ee-copy
              @click="copyModelAnswer"
            >
              <AppIcon name="copy" size="sm" /> コピー
            </button>
          </div>
        </header>

        <div class="ee-model" data-ee-model>{{ grading.modelAnswer }}</div>
      </article>
    </div>

    <!-- 5. 英検対策のワンポイント＋注意書き（2.0 の `.next-advice` ＝ アイコン＋本文の 1 行）。
         どちらも無ければ出さない -->
    <div v-if="hasAdvice" class="ee-advice-sec">
      <span class="ee-advice-sec__icon" aria-hidden="true"><AppIcon name="chevron-down" size="sm" /></span>
      <div class="ee-advice-sec__body">
        <strong class="ee-advice-sec__title">英検対策のワンポイント</strong>
        <p class="ee-advice" data-ee-advice>{{ side.advice }}</p>
        <p v-if="side.notice !== ''" class="ee-notice" data-ee-notice>{{ side.notice }}</p>
      </div>
      <button
        type="button" class="btn btn--secondary btn--sm" data-ee-zoom
        @click="openZoom('advice')"
      >
        <AppIcon name="zoom-in" size="sm" /> 拡大表示
      </button>
    </div>
  </div>

  <!-- 拡大表示（背景クリックでは閉じない。× か Esc で閉じる） -->
  <div v-if="zoom !== null" class="overlay" data-ee-zoom-dialog>
    <section class="dialog dialog--lg ee-zoom" role="dialog" aria-modal="true" :aria-label="zoomTitle">
      <div class="dialog__head">
        <h2 class="dialog__title">{{ zoomTitle }}</h2>
        <button type="button" class="dialog__close" aria-label="閉じる" @click="closeZoom">
          <AppIcon name="x" size="sm" />
        </button>
      </div>

      <div class="dialog__body ee-zoom__body">
        <template v-if="zoom === 'overview'">
          <p class="ee-zoom__score">{{ scoreText }}（{{ levelLabel }}）</p>
          <span class="ee-report__label">EIKEN WRITING FEEDBACK</span>
          <h3 class="ee-zoom__title">{{ side.title }}</h3>
          <p class="ee-overview__summary">{{ side.summary }}</p>
          <div class="ee-tags">
            <span v-for="tag in side.tags" :key="tag" class="badge ee-tag">{{ tag }}</span>
          </div>
        </template>

        <template v-else-if="zoom === 'rubric'">
          <div v-for="item in side.rubric" :key="item.key" class="ee-zoom__rubric">
            <div class="ee-rubric__row">
              <span>{{ item.key }}</span>
              <strong class="ee-rubric__score">{{ item.score }} / {{ item.maxScore }}</strong>
            </div>
            <meter min="0" :max="item.maxScore" :value="item.score"></meter>
            <p>{{ item.note }}</p>
          </div>
        </template>

        <template v-else-if="zoom === 'corrections'">
          <div v-for="(item, index) in side.corrections" :key="index" class="ee-zoom__correction">
            <p>
              <del>{{ item.original }}</del>
              <span class="ee-correction__arrow">→</span>
              <ins>{{ item.corrected }}</ins>
            </p>
            <p><span class="badge ee-correction__cat">{{ item.category }}</span>{{ item.reason }}</p>
          </div>
        </template>

        <template v-else-if="zoom === 'model'">
          <p class="ee-zoom__req">語数の目安：{{ grading.wordRequirement }}</p>
          <div class="ee-model ee-model--zoom">{{ grading.modelAnswer }}</div>
        </template>

        <template v-else>
          <p class="ee-advice">{{ side.advice }}</p>
          <p class="ee-notice">{{ side.notice }}</p>
        </template>
      </div>

      <div class="dialog__foot">
        <button type="button" class="btn btn--secondary" @click="closeZoom">閉じる</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 作文タイトル（2.0 の `report-essay-title`）。 */
.ee-report-title {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  margin-bottom: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: var(--radius-lg);
  background: var(--color-surface-alt);
}

.ee-report-title__label {
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
  font-weight: 600;
  letter-spacing: 0.06em;
}

.ee-report-title__value {
  margin: 0;
  color: var(--color-text-strong);
  font-size: var(--fs-lg);
  line-height: 1.5;
}

/* 設問が求めていること（採点の観点）。 */
.ee-requirements {
  display: grid;
  gap: var(--sp-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ee-requirement {
  position: relative;
  padding-left: var(--sp-5);
  color: var(--color-text);
  font-size: var(--fs-md);
  line-height: 1.7;
}

.ee-requirement::before {
  content: '';
  position: absolute;
  top: 0.6em;
  left: var(--sp-2);
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
}
</style>
