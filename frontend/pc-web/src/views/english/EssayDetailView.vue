<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import EssayImagePreview from '@/features/english-essay/components/EssayImagePreview.vue'
import EssayTextPreview from '@/features/english-essay/components/EssayTextPreview.vue'
import EssayRoundSwitcher from '@/features/english-essay/components/EssayRoundSwitcher.vue'
import EssayReportPanel from '@/features/english-essay/components/EssayReportPanel.vue'
import { ESSAY_LEVEL_LABELS, type EssayGrading } from '@/features/english-essay/types'
import {
  addGradingRound,
  hasPendingRounds,
  loadEssay,
  type Essay,
  type EssayGradingRound,
  type EssayImage
} from '@/features/english-essay/store'
import '@/features/english-essay/essay-report.css'
import '@/features/english-essay/essay-page.css'

/**
 * 英作文AI添削【詳細】（`/:area/english-essay/:essayId`）。
 *
 * <p>2.0 の `english_essay.jsp?essayId=N`（`body.essay-detail-page`）の並びに合わせる。
 * 2.0 の詳細は `css/english_essay.css` の</p>
 *
 * <pre>
 * body.essay-detail-page .essay-editor-content {
 *   grid-template-columns: minmax(0, 3fr) minmax(250px, .65fr);
 *   grid-template-areas: "hero hero" "recognition side" "result side";
 * }
 * </pre>
 *
 * <p>で、**英雄 →（左）設問と作文＝`recognition-card` →（左）AI添削レポート＝`result-card`、
 * 右の柱（`side`）に登録画像＝`upload-card`（`position: sticky; top: 14px`）**という 4 領域。
 * 2.1 も同じ 4 領域を `[data-ee-region]`（hero / recognition / side / result）で名乗る。</p>
 *
 * <p>中身も 2.0 に合わせる: 設問と作文は `.ocr-grid`（`.85fr : 1.15fr`）の 2 面
 * （題は「作文の設問」＝ Question / Instructions と「手書き作文」＝ Student's Essay。
 * 足元に語数）、登録画像はサムネイル（78px）＋ファイル名・区分の一覧、レポートは
 * 見出しの右に**結果言語**。</p>
 *
 * <p>2.0 は 1 回だけの添削だったが、2.1 は「**何度も添削して、历次を切り替えて見る**」が
 * 追加要件なので、上部の【添削の回】で選んだ回の内容（レポート・修正ポイント・改善後の作文例・
 * 得点）がまるごと入れ替わる（2.0 の見た目に**足す**形で置く）。既定は最新の回。</p>
 *
 * <p>回の番号は **API の `round` をそのまま**使う（失敗した回も 1 行として残るので、成功した回を
 * 数え直すと番号がずれる）。失敗した回も選択肢に出し、選ぶと**失敗の理由**を出して
 * レポート（4 観点など）は出さない（データが無いので見出しも残さない）。</p>
 *
 * <p>データは user-api（`GET /api/user/english-essays/{essayId}`）。添削は**非同期**なので、
 * 【この内容でAI添削】＝ user-api の**受付**で、結果は**様子見**で拾う:
 * `QUEUED`／`RUNNING` の回があるうちは **3 秒ごとに静かに読み直す**（`loading` を立てない・
 * 要素を作り直さない ＝ ちらつかない。`docs/DECISIONS.md`）。</p>
 *
 * <p>親エリア（`/parent/...`）は**閲覧だけ**（`docs/PERMISSION_MATRIX.md`）。レポートと
 * 添削の回の切り替えは見られるが、添削を積むボタンは出さない。</p>
 */
const route = useRoute()
const toast = useToast()

/** URL の `:essayId`。 */
const essayId = computed<string>(() => {
  const value = route.params.essayId
  return Array.isArray(value) ? (value[0] ?? '') : String(value ?? '')
})

/** 見ている作文（見つからないときは null）。 */
const essay = ref<Essay | null>(null)

/** 添削の回の状態（古い順。成功・実行中・失敗のすべて）。 */
const rounds = ref<EssayGradingRound[]>([])

/** 最初の読み込みが終わったか（「見つかりません」を読み込み中に出さない）。 */
const loaded = ref(false)

/** 見ている回（**API の `round`**。1 から。既定は最新）。 */
const selectedRound = ref<number>(1)

/** 結果の言語（2.0 の `essayResultLanguageSelect` と同じく、レポートの見出しに置く）。 */
const lang = ref<'japanese' | 'chinese'>('japanese')

/** 拡大している登録画像。 */
const zoomImage = ref<EssayImage | null>(null)

/**
 * 実体を読めなかった画像（この画面で `img` の読み込みに失敗したもの）。
 *
 * <p>DB に行があっても実体のファイルが無いことがある（2.0 からの移行分など）。
 * 割れた絵を出さず、枠に「表示できません」を出して拡大もさせない。</p>
 */
const brokenImages = ref<string[]>([])

/** その画像は読めなかったか。 */
function isBroken(imageId: string): boolean {
  return brokenImages.value.includes(imageId)
}

/** 画像の読み込みに失敗した印を付ける（同じ画像で何度呼ばれても 1 つだけ）。 */
function markBroken(imageId: string): void {
  if (!isBroken(imageId)) {
    brokenImages.value = [...brokenImages.value, imageId]
  }
}

/** 拡大している本文（設問／作文。詳細は閲覧のみなので**読むだけ**で出す）。 */
const zoomField = ref<'question' | 'essay' | null>(null)

/** 拡大の見出し（2.0 の `essayTextModalTitle` と同じ 2 つ）。 */
const zoomTitle = computed<string>(() => (zoomField.value === 'essay' ? '手書き作文' : '作文の設問'))

/** 拡大に出す本文（保存されている全文。設問と作文で分ける）。 */
const zoomText = computed<string>(() => (zoomField.value === 'essay'
  ? essay.value?.essayText ?? ''
  : essay.value?.questionText ?? ''))

/** 添削の受付を送信中か（二重送信を防ぐ）。 */
const accepting = ref(false)

/** 戻り先（エリアは URL から取る）。 */
const backTo = computed<string>(() => `/${areaOf(route.path)}/english-essay`)

/**
 * 親エリアか（`docs/PERMISSION_MATRIX.md` の英作文AI添削は「親＝子 結果の閲覧」）。
 *
 * <p>親は**見るだけ**なので、添削を積む操作（【この内容でAI添削】【もう一度AI添削】）を出さない。
 * レポートと添削の回の切り替えはそのまま見られる。</p>
 */
const isParent = computed<boolean>(() => areaOf(route.path) === 'parent')

/**
 * 選ばれている回の行（まだ 1 回も無ければ null）。
 *
 * <p>見ている回は**番号（API の `round`）**で探す。成功した回だけを数えた位置では、途中に
 * 失敗した回があると API の `round` とずれる（第 1 回が失敗したとき、成功した回が「第 1 回」
 * になってしまう）。</p>
 */
const selectedRoundRow = computed<EssayGradingRound | null>(() => {
  const list = rounds.value
  return list.find((row) => row.round === selectedRound.value) ?? list[list.length - 1] ?? null
})

/** 選ばれている回が失敗したか（結果が無い回。実行中は別に扱う）。 */
const selectedRoundFailed = computed<boolean>(() => {
  const row = selectedRoundRow.value
  if (row === null) {
    return false
  }
  return row.statusCode !== 'SUCCEEDED' && row.statusCode !== 'QUEUED' && row.statusCode !== 'RUNNING'
})

/** 失敗した回の理由（記録が無ければその旨。空欄を黙って出さない）。 */
const failureReason = computed<string>(() => {
  const reason = (selectedRoundRow.value?.failureReason ?? '').trim()
  return reason === '' ? '理由は記録されていません。' : reason
})

/** 選ばれている回の添削結果（成功した回だけ。まだ・実行中・失敗は null）。 */
const selected = computed<EssayGrading | null>(() => selectedRoundRow.value?.report ?? null)

/** レポートが無いときの案内（まだ 1 回も無いのか、いま実行中なのか）。 */
const emptyText = computed<string>(() => (rounds.value.length === 0
  ? 'この作文はまだAI添削されていません。'
  : 'この回のAI添削は実行中です。しばらくお待ちください。'))

const levelLabel = computed<string>(() =>
  essay.value === null ? '' : ESSAY_LEVEL_LABELS[essay.value.level]
)

/** URL の先頭（`/admin` `/student` `/parent`）。 */
function areaOf(path: string): string {
  return path.split('/').filter((segment) => segment.length > 0)[0] ?? 'student'
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 登録日時（見ている人の時計で `YYYY/MM/DD HH:mm`）。 */
function formatAt(iso: string): string {
  const at = new Date(iso)
  if (Number.isNaN(at.getTime())) {
    return iso
  }
  return `${at.getFullYear()}/${pad(at.getMonth() + 1)}/${pad(at.getDate())}`
    + ` ${pad(at.getHours())}:${pad(at.getMinutes())}`
}

/** 画像の区分のラベル（設問画像／答案画像）。 */
function imageLabel(image: EssayImage): string {
  return image.category === 'question' ? '設問画像' : '答案画像'
}

/**
 * 設問の語数（**数えるだけで保存しない**）。
 *
 * <p>誤りの混ざった数値を出さないよう、その場で数える（`essay.wordCount` は作文の語数）。
 * 0 のときは出さない（`0 words` を空の設問に出さない）。</p>
 */
const questionWordCount = computed<string>(() => {
  const text = essay.value === null ? '' : essay.value.questionText
  const count = text.split(/\s+/).filter((word) => word.length > 0).length
  return count === 0 ? '語数なし' : `${count} words`
})

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/* ---------- 読み込みと様子見（ちらつかせない） ---------- */

/** 様子を見る間隔（ミリ秒）。日本語の単語画面と同じ 3 秒。 */
const POLL_INTERVAL_MS = 3_000

/** 様子見をやめるまでの回数（3 秒 × 600 ＝ 30 分。これ以上は実行中でも知らせない）。 */
const POLL_MAX_TICKS = 600

/** 様子見のタイマー（1 つだけ回す）。 */
let pollTimer: number | null = null

/** 様子見の回数（上限で打ち切るため）。 */
let pollTicks = 0

/** まだ終わっていない回の ID。 */
function pendingIds(): string[] {
  return rounds.value
    .filter((round) => round.statusCode === 'QUEUED' || round.statusCode === 'RUNNING')
    .map((round) => round.gradingId)
}

/**
 * 様子見を止める。
 *
 * <p>タイマーを消すだけで `essay` は触らない（要素を作り直さない）。</p>
 */
function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
  pollTicks = 0
}

/** 様子見を始める（すでに回っていれば何もしない）。 */
function startPolling(): void {
  if (pollTimer !== null) {
    return
  }
  pollTicks = 0
  pollTimer = window.setInterval(() => {
    void poll()
  }, POLL_INTERVAL_MS)
}

/**
 * 作文を読み直す。
 *
 * <p><b>{@code quiet}</b> は「画面を待たせない読み直し」（様子見と受付の直後が使う）。
 * {@code loading} を立てず、失敗しても黙って見送る（次の周期でまた試す）。中身が変わって
 * いなければ store が**同じオブジェクト**を返すので、画面は描き直されない＝ちらつかない。</p>
 */
async function load(options: { quiet?: boolean } = {}): Promise<void> {
  const quiet = options.quiet === true
  const id = essayId.value
  if (id === '') {
    loaded.value = true
    return
  }
  try {
    const detail = await loadEssay(id)
    // URL が変わっていたら、遅れて返った応答は捨てる
    if (id !== essayId.value) {
      return
    }
    applyDetail(detail, quiet)
  } catch (caught) {
    if (!quiet) {
      essay.value = null
      rounds.value = []
      toast.danger(messageOf(caught, '英作文を取得できませんでした。'))
    }
  } finally {
    if (!quiet) {
      loaded.value = true
    }
  }
}

/** 読み直した結果を画面へ写す（新しい回が増えていれば、その回を選ぶ）。 */
function applyDetail(detail: { essay: Essay; rounds: EssayGradingRound[] } | null, quiet: boolean): void {
  if (detail === null) {
    essay.value = null
    rounds.value = []
    stopPolling()
    return
  }
  const previous = latestRoundOf(rounds.value)
  const latest = latestRoundOf(detail.rounds)
  essay.value = detail.essay
  rounds.value = detail.rounds
  // 既定は**最新**の回（番号は API の round）。新しい回が増えたときだけ選び直す
  // （見ている回を勝手に動かさない）
  if (latest !== null && (previous === null || latest > previous || selectedRound.value > latest)) {
    selectedRound.value = latest
  }
  if (!quiet) {
    zoomImage.value = null
  }
  if (hasPendingRounds(detail.rounds)) {
    startPolling()
  } else {
    stopPolling()
  }
}

/** いちばん新しい回の番号（まだ 1 回も無ければ null）。 */
function latestRoundOf(list: readonly EssayGradingRound[]): number | null {
  let latest: number | null = null
  for (const row of list) {
    if (latest === null || row.round > latest) {
      latest = row.round
    }
  }
  return latest
}

/**
 * 1 回ぶんの様子見。
 *
 * <p>落ち着いたら（未完了が 0 になったら）知らせて止める。**失敗しても黙って見送る**
 * （利用者の操作ではないので邪魔しない）。</p>
 */
async function poll(): Promise<void> {
  const watching = pendingIds()
  pollTicks += 1
  await load({ quiet: true })
  if (pendingIds().length > 0) {
    if (pollTicks >= POLL_MAX_TICKS) {
      stopPolling()
      toast.warning('AI添削がまだ実行中です。【一覧へ戻る】で状態を確かめてください。')
    }
    return
  }
  stopPolling()
  notifySettled(watching)
}

/** 終わった回の結果を知らせる（成功・失敗の件数）。 */
function notifySettled(watching: readonly string[]): void {
  const finished = rounds.value.filter((round) => watching.includes(round.gradingId))
  if (finished.length === 0) {
    return
  }
  const succeeded = finished.filter((round) => round.statusCode === 'SUCCEEDED').length
  const failed = finished.length - succeeded
  const message = `AI添削が終わりました（成功 ${succeeded} / 失敗 ${failed}）。`
  if (failed > 0) {
    toast.warning(message)
  } else {
    toast.success(message)
  }
}

/**
 * 【この内容でAI添削】（未添削のとき）／【もう一度AI添削】（積み重ねるとき）。
 *
 * <p>送るのは**受付**だけ（実行はバックエンドの働き手）。すぐ「受付ました」と出して、
 * 結果は上の様子見で反映する。前の回は消さない。</p>
 */
async function gradeAgain(): Promise<void> {
  const current = essay.value
  if (current === null || accepting.value) {
    return
  }
  accepting.value = true
  try {
    const accepted = await addGradingRound(current.id)
    toast.success(`AI添削を受付けました（第 ${accepted.round} 回）。`)
    // 受付で状態が変わる（待ちになる）ので、すぐ 1 回**静かに**読み直して様子見に入る
    await load({ quiet: true })
  } catch (caught) {
    toast.danger(messageOf(caught, 'AI添削を受付けられませんでした。'))
  } finally {
    accepting.value = false
  }
}

/** URL の作文を読み直す（開いたとき・`:essayId` が変わったとき）。 */
function open(): void {
  essay.value = null
  rounds.value = []
  selectedRound.value = 1
  zoomImage.value = null
  zoomField.value = null
  brokenImages.value = []
  stopPolling()
  loaded.value = false
  void load()
}

// 画像の拡大は共通部品（`EssayImagePreview`）が持ち、Esc もそこで受ける
onMounted(open)

onUnmounted(stopPolling)

watch(essayId, () => open())
</script>

<template>
  <div class="ee-detail" data-ee-detail>
    <template v-if="essay !== null">
      <!-- 2.0 の `body.essay-detail-page` と同じグリッド（hero ／ recognition ／ side ／ result）。
           左に設問・作文とレポート、**右の柱に登録画像**を固定する。並べ方は essay-page.css。 -->
      <header class="ee-hero" data-ee-region="hero">
        <div class="ee-hero__main">
          <h1>
            <AppIcon name="pen" size="sm" /> 英作文 詳細
          </h1>
          <p data-ee-title>{{ essay.title }}</p>
          <div class="ee-hero__meta">
            <span class="ee-pill">保存データ</span>
            <span><AppIcon name="list" size="sm" /> 登録番号 {{ essay.id }}</span>
            <span data-ee-level><AppIcon name="check" size="sm" /> {{ levelLabel }}</span>
            <span data-ee-created><AppIcon name="clock" size="sm" /> 登録日時 {{ formatAt(essay.createdAt) }}</span>
          </div>
        </div>
        <div class="ee-hero__actions">
          <RouterLink class="btn btn--secondary" data-ee-back :to="backTo">
            <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
          </RouterLink>
        </div>
      </header>

      <!-- 設問と作文（2.0 の recognition-card。`.ocr-grid` の 0.85fr : 1.15fr で 2 面に並べる） -->
      <section class="card ee-meta" data-ee-region="recognition">
        <div class="ee-meta__grid">
          <article class="ee-ocr-panel">
            <header>
              <span class="ee-ocr-panel__type"><AppIcon name="list" size="sm" /></span>
              <span class="ee-ocr-panel__label">
                <strong class="ee-block__title">作文の設問</strong>
                <small>Question / Instructions</small>
              </span>
              <span class="ee-ocr-panel__actions">
                <button
                  type="button" class="btn btn--secondary btn--sm" data-ee-zoom-text
                  title="作文の設問を拡大表示" aria-label="作文の設問を拡大表示"
                  @click="zoomField = 'question'"
                >
                  <AppIcon name="zoom-in" size="sm" /> 拡大
                </button>
                <span class="badge badge--outline ee-ocr-panel__badge">保存データ</span>
              </span>
            </header>
            <p class="ee-ocr-text ee-ocr-text--read" data-ee-question>{{ essay.questionText }}</p>
            <footer>
              <span>登録済みの設問</span>
              <span>{{ questionWordCount }}</span>
            </footer>
          </article>

          <article class="ee-ocr-panel">
            <header>
              <span class="ee-ocr-panel__type"><AppIcon name="pen" size="sm" /></span>
              <span class="ee-ocr-panel__label">
                <strong class="ee-block__title">手書き作文</strong>
                <small>Student's Essay</small>
              </span>
              <span class="ee-ocr-panel__actions">
                <button
                  type="button" class="btn btn--secondary btn--sm" data-ee-zoom-text
                  title="手書き作文を拡大表示" aria-label="手書き作文を拡大表示"
                  @click="zoomField = 'essay'"
                >
                  <AppIcon name="zoom-in" size="sm" /> 拡大
                </button>
                <span class="badge badge--outline ee-ocr-panel__badge">保存データ</span>
              </span>
            </header>
            <p class="ee-ocr-text ee-ocr-text--read" data-ee-essay>{{ essay.essayText }}</p>
            <footer>
              <span>登録済みの作文</span>
              <span data-ee-words>{{ essay.wordCount }} words</span>
            </footer>
          </article>
        </div>
      </section>

      <!-- 登録画像（2.0 の upload-card。詳細では**右の柱**＝グリッドの `side` に固定。
           中身は 2.0 の `.essay-image-grid` ＝ 1 列の一覧で、1 枚ごとに
           サムネイル（78px）＋ファイル名・区分・順番） -->
      <section v-if="essay.images.length > 0" class="card ee-images" data-ee-region="side" data-ee-upload-side>
        <h3 class="ee-block__title">登録画像</h3>
        <p class="ee-block__lead">OCRに使用した保存済み画像です。</p>
        <div class="ee-images__list">
          <article
            v-for="image in essay.images" :key="image.id"
            class="ee-images__item"
          >
            <button
              type="button" class="ee-images__thumb" data-ee-image
              :title="`${imageLabel(image)}を拡大`"
              :aria-label="`${imageLabel(image)} ${image.fileName} を拡大表示`"
              :disabled="isBroken(image.id)" @click="zoomImage = image"
            >
              <img
                v-if="!isBroken(image.id)" :src="image.dataUrl"
                :alt="`${imageLabel(image)} ${image.fileName}`" @error="markBroken(image.id)"
              >
              <span v-else class="ee-images__missing" data-ee-image-missing>表示できません</span>
            </button>
            <span class="ee-images__order">{{ image.order }}</span>
            <div class="ee-images__meta">
              <strong :title="image.fileName">{{ image.fileName }}</strong>
              <small>{{ imageLabel(image) }}</small>
              <button
                type="button" class="btn btn--secondary btn--sm ee-images__zoom"
                :title="`${imageLabel(image)}を拡大`" :disabled="isBroken(image.id)"
                @click="zoomImage = image"
              >
                <AppIcon name="zoom-in" size="sm" /> 拡大
              </button>
            </div>
          </article>
        </div>
      </section>

      <!-- AI添削レポート（2.0 の result-card。見出し・説明・**結果言語**） -->
      <section class="card ee-report-card" data-ee-region="result">
        <header class="ee-report-card__head">
          <div>
            <h3 class="ee-block__title" data-ee-report-heading>AI添削レポート</h3>
            <p class="ee-report-card__lead" data-ee-report-lead>設問への適合度、内容、構成、語彙・文法を総合評価</p>
          </div>
          <!-- 結果言語（2.0 の `word2-language-toggle` と同じセグメント。利用者の指示で
               英作文の select をやめ、testworld2_test_a.jsp の右上の切替と同じ形にした） -->
          <div class="ee-report-card__lang">
            <span id="eeReportLangLabel">結果言語</span>
            <div class="ee-lang" role="group" aria-labelledby="eeReportLangLabel" data-ee-lang>
              <button
                type="button" class="ee-lang__item" :class="{ 'is-active': lang === 'japanese' }"
                :aria-pressed="lang === 'japanese'" data-ee-lang-ja @click="lang = 'japanese'"
              >
                日本語
              </button>
              <button
                type="button" class="ee-lang__item" :class="{ 'is-active': lang === 'chinese' }"
                :aria-pressed="lang === 'chinese'" data-ee-lang-zh @click="lang = 'chinese'"
              >
                中文
              </button>
            </div>
          </div>
        </header>

        <!-- 添削の回（2.1 の追加要件）。利用者の指示で**タブ**にした（前はセレクト＋
             「第 N 回の結果」の見出し。番号は API の `round` をそのまま使う＝見出しはタブが兼ねる） -->
        <EssayRoundSwitcher
          v-if="rounds.length > 0"
          :rounds="rounds"
          :model-value="selectedRound"
          :can-regrade="!isParent"
          @update:model-value="selectedRound = $event"
          @regrade="gradeAgain"
        />

        <!-- タブが指す中身（選んだ回のレポート・失敗の理由・未添削の案内）。
             タブが無い（まだ 1 回も添削していない）ときは tabpanel にしない -->
        <div
          :id="rounds.length > 0 ? 'eeRoundPanel' : undefined"
          class="ee-report-card__panel"
          :role="rounds.length > 0 ? 'tabpanel' : undefined"
          :aria-labelledby="rounds.length > 0 ? `eeRoundTab-${selectedRound}` : undefined"
        >
          <EssayReportPanel v-if="selected !== null" :grading="selected" :lang="lang" />

          <!-- 失敗した回: 理由を出して、レポート（4 観点など）は出さない（データが無い） -->
          <div v-else-if="selectedRoundFailed" class="ee-failed" data-ee-grade-failed>
            <p class="ee-failed__title">この回の添削は失敗しました。</p>
            <p class="ee-failed__reason" data-ee-grade-failed-reason>{{ failureReason }}</p>
            <p v-if="!isParent" class="ee-failed__hint">もう一度AI添削でやり直せます。</p>
          </div>

          <div v-else class="ee-no-report" data-ee-empty>
            <p class="ee-no-report__text">{{ emptyText }}</p>
            <button
              v-if="!isParent && rounds.length === 0" type="button" class="btn btn--primary" data-ee-grade
              :disabled="accepting" @click="gradeAgain"
            >
              <AppIcon name="wand" size="sm" /> この内容でAI添削
            </button>
          </div>
        </div>
      </section>
    </template>

    <div v-else-if="loaded" class="card ee-missing">
      <p class="ee-missing__text">作文が見つかりません。</p>
      <RouterLink class="btn btn--secondary" data-ee-back :to="backTo">
        <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
      </RouterLink>
    </div>

    <!-- 登録画像の拡大表示（フォームと同じ共通部品。2.0 の `#essayImagePreviewModal` 相当） -->
    <EssayImagePreview
      :open="zoomImage !== null" :src="zoomImage?.dataUrl ?? ''"
      :title="zoomImage === null ? undefined : `${imageLabel(zoomImage)}（${zoomImage.fileName}）`"
      @close="zoomImage = null"
    />

    <!-- 本文（設問・作文）の拡大表示（2.0 の `#essayTextModal` 相当。詳細は**読むだけ**） -->
    <EssayTextPreview
      :open="zoomField !== null" :title="zoomTitle" :text="zoomText"
      @close="zoomField = null"
    />
  </div>
</template>

<style scoped>
/* 見出しは 2.0 の result-card に寄せる（AI添削レポート＋一行の説明＋結果言語）。 */
.ee-report-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);
}

.ee-report-card__head .ee-block__title {
  margin-bottom: var(--sp-1);
}

.ee-report-card__lead {
  margin: 0;
  color: var(--color-text-subtle);
  font-size: var(--fs-sm);
}

/* 結果言語（2.0 の `essayResultLanguageSelect` と同じ位置＝見出しの右）。
   切替の見た目は 2.0 の `word2-language-toggle`（外枠 2px パディング＋選択中は主色の塗り） */
.ee-report-card__lang {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-subtle);
  font-size: var(--fs-2xs);
}

/* 言語切替の中身（`.ee-lang*`）は機能の CSS（`essay-report.css`）に置く。
   同じ見た目の回のタブ（`.ee-round__tab`）と同じ置き場にして、置き場が分裂しないようにする
   （`AGENTS.md`「新增样式写进 app.css 或对应的 src/features/<feature>/*.css」） */

/* ---------- 設問 : 作文の 2 面（2.0 の `.ocr-grid`） ----------
   `EssayForm.vue` の `.ee-ocr-grid` は 1fr : 1fr だが、2.0 の詳細は
   `.85fr : 1.15fr`（設問がやや狭く、作文が広い）。詳細だけの並べ方なので、
   共通のクラスは触らず、ここで `.ee-meta` の中だけ組み替える。 */
.ee-meta__grid {
  display: grid;
  grid-template-columns: 0.85fr 1.15fr;
  gap: var(--sp-3);
}

/* 読み取り専用の本文（2.0 の `.ocr-panel textarea`。`.ee-ocr-text` の
   `min-height: 180px` と `padding` を受け継ぎ、枠だけ外す）。 */
.ee-ocr-text--read {
  width: 100%;
  margin: 0;
  border: 0;
  outline: 0;
  color: var(--color-text);
  font-size: var(--fs-md);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 見出しの中の「保存データ」の札（2.0 の `.confidence.ready`） */
.ee-ocr-panel__badge {
  flex: 0 0 auto;
  color: var(--color-primary);
}

/* 面の見出しの題（`.ee-ocr-panel header strong` は 13px。`.ee-block__title` の
   14px を詳細でも保つ）。題と副題（Question / Instructions）は 2.0 と同じく 2 行に積む
   （`EssayForm.vue` と同じ見た目） */
.ee-ocr-panel__label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.ee-ocr-panel__label .ee-block__title {
  font-size: var(--fs-md);
  line-height: 1.35;
}

/* 回の切り替えの直後の中身の余白は `essay-report.css` の
   `.ee-report-card__panel > .ee-report` が持つ（ここは view の見た目だけ） */

/* 狭い画面: 設問 : 作文を 1 列へ（2.0 の 1000px の規則に合わせる） */
@media (max-width: 1000px) {
  .ee-meta__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
