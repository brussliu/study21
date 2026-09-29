import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '@study21/web-shared'
import {
  activateJpnDetailVersion,
  fetchJpnDetailVersions,
  fetchJpnWord,
  saveJpnWordEditor,
  type JpnDetailVersion,
  type JpnWordDetailResult
} from '@/api/japanese'
import type { DemoWord, DemoDetailContent } from '@/features/japanese-demo/types'
import { emptyDetail, toStudyWord } from './wordMapper'
import { editorRequest } from './editorMapper'

/** 本番の下書き。保存完了まで一覧・学習画面には反映しない。 */
export const useJpnEditorStore = defineStore('jpn-editor', () => {
  const draft = ref<DemoWord | null>(null)
  const loadedDetail = ref<JpnWordDetailResult | null>(null)
  const draftDirty = ref(false)
  const saveState = ref<'IDLE' | 'SAVING' | 'SAVED' | 'FAILED' | 'CONFLICT'>('IDLE')
  const saveMessage = ref('')
  const conflictNote = ref('')
  const loading = ref(false)
  const error = ref('')
  const stateCode = ref('ACTIVE')
  const note = ref('')
  const words = computed(() => draft.value ? [draft.value] : [])
  let requestId = 0

  /* ---------- 詳細の版の履歴 ---------- */

  /** 版の一覧（**新しい順**）。 */
  const versions = ref<JpnDetailVersion[]>([])
  /** 版の一覧を読み込み中か。 */
  const versionsLoading = ref(false)
  /** 版の一覧の読み込みに失敗した理由。 */
  const versionsError = ref('')
  /** 切り替え中の版（二重に押せないようにする）。 */
  const activatingVersionId = ref<number | null>(null)
  /** 切り替えに失敗した理由（409 のときは読み直しを促す文）。 */
  const activateError = ref('')

  function accept(result: JpnWordDetailResult): void {
    loadedDetail.value = result
    draft.value = toStudyWord({ ...result.word, collections: result.collections, detail: result.detail })
    // 教材由来の複合品詞も、編集していない限り元の値のまま保存する。
    draft.value.partOfSpeech = (result.word.partOfSpeech ?? '') as DemoWord['partOfSpeech']
    stateCode.value = result.word.stateCode
    note.value = result.word.note ?? ''
    draftDirty.value = false
  }

  /** 詳細の版の一覧を読み直す。 */
  async function loadDetailVersions(): Promise<boolean> {
    if (loadedDetail.value === null) {
      return false
    }
    versionsLoading.value = true
    versionsError.value = ''
    try {
      const response = await fetchJpnDetailVersions(loadedDetail.value.word.wordId)
      versions.value = response.data.items
      return true
    } catch (caught) {
      versionsError.value = caught instanceof ApiError ? caught.message : '版の履歴を読み込めませんでした。'
      versions.value = []
      return false
    } finally {
      versionsLoading.value = false
    }
  }

  /**
   * 指定した版を有効にする。
   *
   * `version` は**切り替えたい版を一覧で読んだときの `バージョン`**（`JpnDetailVersion.version`）。
   * 版ごとに違う値なので、表示中の詳細の値ではない。
   * ほかの操作が先に更新していれば 409 になり、ここで版の一覧を読み直して知らせる。
   */
  async function activateDetailVersion(detailId: number, version: number): Promise<boolean> {
    if (loadedDetail.value === null || activatingVersionId.value !== null) {
      return false
    }
    activatingVersionId.value = detailId
    activateError.value = ''
    try {
      const response = await activateJpnDetailVersion(loadedDetail.value.word.wordId, detailId, version)
      // サーバーが切り替えたあとの詳細を返すので、画面は取り直さずに差し替える
      accept(response.data)
      await loadDetailVersions()
      return true
    } catch (caught) {
      if (isConflict(caught)) {
        activateError.value = '他の操作で先に更新されました。読み直してください。'
        // 版の一覧が古いので取り直す（見えている版番号も変わる）
        await loadDetailVersions()
      } else {
        activateError.value = caught instanceof ApiError ? caught.message : '版を切り替えられませんでした。'
      }
      return false
    } finally {
      activatingVersionId.value = null
    }
  }

  async function openEditor(id: string): Promise<boolean> {
    if (saveState.value === 'SAVING') return false
    const token = ++requestId
    loading.value = true
    error.value = ''
    try {
      const response = await fetchJpnWord(Number(id))
      if (token !== requestId) return false
      accept(response.data)
      saveState.value = 'IDLE'
      saveMessage.value = ''
      conflictNote.value = ''
      return true
    } catch (caught) {
      if (token !== requestId) return false
      error.value = caught instanceof ApiError ? caught.message : '詳細情報を読み込めませんでした。'
      return false
    } finally {
      if (token === requestId) loading.value = false
    }
  }

  function closeEditor(): void {
    if (saveState.value === 'SAVING') return
    requestId += 1
    draft.value = null
    loadedDetail.value = null
    draftDirty.value = false
    loading.value = false
    error.value = ''
    saveState.value = 'IDLE'
    versions.value = []
    versionsLoading.value = false
    versionsError.value = ''
    activatingVersionId.value = null
    activateError.value = ''
  }

  /** 下書きの詳細の中身（無ければ空の器を作る）。 */
  function draftDetail(): DemoDetailContent | null {
    if (draft.value === null || saveState.value === 'SAVING') {
      return null
    }
    if (draft.value.detail === null) {
      draft.value.detail = emptyDetail()
    }
    return draft.value.detail
  }

  function touchDraft(): void {
    draftDirty.value = true

    if (saveState.value === 'SAVED' || saveState.value === 'FAILED' || saveState.value === 'CONFLICT') {
      saveState.value = 'IDLE'
      saveMessage.value = ''
    }
  }

  /** 下書きの基本情報を更新する。 */
  function updateDraftBasic(patch: Partial<Pick<DemoWord,
    'heading' | 'reading' | 'partOfSpeech' | 'jlpt' | 'chineseMeaning' | 'alternateReading'>>): void {
    if (draft.value === null || saveState.value === 'SAVING') {
      return
    }
    Object.assign(draft.value, patch)
    touchDraft()
  }

  /** 下書きの詳細（説明文）を更新する。 */
  function updateDraftDescription(patch: Partial<Pick<DemoDetailContent,
    'coreMeaning' | 'descriptionJa' | 'descriptionZh'>>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    Object.assign(detail, patch)
    touchDraft()
  }

  /** 配列の項目を操作するためのキー。 */
  type ListKey =
    | 'senses' | 'examples' | 'patterns' | 'dialogs' | 'synonyms' | 'cautions'
    | 'conjugations' | 'collocations' | 'relatedWords' | 'usageNotes' | 'practices'

  /** 配列に 1 件足す。 */
  function addListItem(key: ListKey, item: unknown): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    ;(detail[key] as unknown[]).push(item)
    touchDraft()
  }

  /** 配列の 1 件を差し替える。 */
  function updateListItem(key: ListKey, index: number, patch: Record<string, unknown>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown as Record<string, unknown>[]
    const current = list[index]
    if (current === undefined) {
      return
    }
    list[index] = { ...current, ...patch }
    touchDraft()
  }

  /** 配列の 1 件を消す。 */
  function removeListItem(key: ListKey, index: number): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown[]
    if (index < 0 || index >= list.length) {
      return
    }
    list.splice(index, 1)
    touchDraft()
  }

  /** 配列の順番を入れ替える（-1 で前へ、+1 で後ろへ）。 */
  function moveListItem(key: ListKey, index: number, direction: -1 | 1): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    const list = detail[key] as unknown[]
    const target = index + direction
    if (index < 0 || index >= list.length || target < 0 || target >= list.length) {
      return
    }
    const [moved] = list.splice(index, 1)
    list.splice(target, 0, moved)
    touchDraft()
  }

  /** 詳細の中身そのもの（発音・記憶のヒント・自他動詞）を差し替える。 */
  function setDetailPart(patch: Partial<Pick<DemoDetailContent,
    'pronunciation' | 'memoryHint' | 'transitivityPair'>>): void {
    const detail = draftDetail()
    if (detail === null) {
      return
    }
    Object.assign(detail, patch)
    touchDraft()
  }


  async function saveDraft(): Promise<boolean> {
    if (!draft.value || !loadedDetail.value || saveState.value === 'SAVING') return false
    if (!draft.value.heading.trim()) {
      saveState.value = 'FAILED'
      saveMessage.value = '見出し語を入力してください。'
      return false
    }
    saveState.value = 'SAVING'
    try {
      const response = await saveJpnWordEditor(loadedDetail.value.word.wordId,
        editorRequest(loadedDetail.value, draft.value, stateCode.value, note.value))
      accept(response.data)
      saveState.value = 'SAVED'
      saveMessage.value = '保存しました。'
      return true
    } catch (caught) {
      // 競合の判定は 1 か所にまとめる（HTTP 409 と code CONFLICT の**どちらでも**競合。
      // 版の切り替えは 409 を見ているので、保存だけ別の見方をしない）
      saveState.value = isConflict(caught) ? 'CONFLICT' : 'FAILED'
      saveMessage.value = caught instanceof ApiError ? caught.message : '保存できませんでした。入力は残しています。'
      conflictNote.value = '入力を控えてから保存済みの内容を読み直してください。'
      return false
    }
  }

  return { draft, draftDirty, saveState, saveMessage, conflictNote, words, loading, error, stateCode, note,
    openEditor, closeEditor, touchDraft, updateDraftBasic, updateDraftDescription, addListItem, updateListItem,
    removeListItem, moveListItem, setDetailPart, saveDraft, loadedDetail, versions, versionsLoading, versionsError, activatingVersionId, activateError,
    loadDetailVersions, activateDetailVersion }
})

/**
 * 競合（ほかの操作が先に更新した）か。
 *
 * HTTP 409 と `code === 'CONFLICT'` の**どちらでも**競合として扱う。サーバーが 409 に別の
 * code を載せても、code だけ CONFLICT で status が違っても、画面の扱いを割らない。
 */
function isConflict(caught: unknown): boolean {
  return caught instanceof ApiError && (caught.status === 409 || caught.code === 'CONFLICT')
}
