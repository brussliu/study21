import type { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'

/** 詳細編集の共通契約。保存先だけをデモ／本番で切り替える。 */
export type WordEditorContract = Pick<ReturnType<typeof useJapaneseDemoStore>,
  | 'draft' | 'draftDirty' | 'saveState' | 'saveMessage' | 'conflictNote' | 'words'
  | 'closeEditor' | 'touchDraft' | 'updateDraftBasic' | 'updateDraftDescription'
  | 'addListItem' | 'updateListItem' | 'removeListItem' | 'moveListItem' | 'setDetailPart'
  | 'saveDraft'
> & { openEditor: (id: string) => boolean | Promise<boolean> }

/**
 * 競合したときに「自分の入力を残したまま」続ける操作は**デモだけ**が持つ。
 *
 * 本番（{@code editorStore}）は版を切り替えて戻す（`activateDetailVersion`）ので、
 * 上書き保存する道は用意しない。契約に入れると本番にも空の実装を置くことになるため、
 * ここには入れない（画面はデモのときだけデモストアの実装を直接呼ぶ）。
 */
