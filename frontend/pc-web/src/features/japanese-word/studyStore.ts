/**
 * 単語情報管理（本番）の「詳細」＝新画面の学習画面で使う状態。
 *
 * 一覧で選んだ 1 語を `GET /words/{id}` で取り直し、新画面が使う形（`DemoWord`）へ移し替えて持つ。
 * デモのストア（`features/japanese-demo`）は仮データ専用なので、本番はこちらを使う。
 */

import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '@study21/web-shared'
import { fetchJpnWord } from '@/api/japanese'
import type { DemoWord } from '@/features/japanese-demo/types'
import { toStudyWord } from './wordMapper'

function messageOf(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.message : fallback
}

export const useJpnStudyStore = defineStore('jpn-study', () => {
  /** 表示中の単語（新画面の形）。 */
  const word = ref<DemoWord | null>(null)
  const loading = ref(false)
  const error = ref('')

  /** 1 語を読み込んで学習画面に出す。 */
  async function load(wordId: number): Promise<void> {
    loading.value = true
    error.value = ''
    word.value = null
    try {
      const response = await fetchJpnWord(wordId)
      const result = response.data
      word.value = toStudyWord({
        wordId: result.word.wordId,
        word: result.word.word,
        reading: result.word.reading,
        jlptLevel: result.word.jlptLevel,
        partOfSpeech: result.word.partOfSpeech,
        collections: result.collections,
        detail: result.detail,
        updatedAt: result.word.lastStudiedAt ?? ''
      })
    } catch (caught) {
      error.value = messageOf(caught, '単語の詳細を取得できませんでした。')
    } finally {
      loading.value = false
    }
  }

  return { word, loading, error, load }
})
