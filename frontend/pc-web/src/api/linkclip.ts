import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * リンククリップ API（/api/user）。型とエンドポイントは user-api の LinkClipController と対応する。
 * 所有権はアカウント個人（家族共有ではない）ため、家族IDは送らない。
 */

/** 仕分けレーンのコード。 */
export type FolderCode = 'INBOX' | 'READ_LATER' | 'LEARNING' | 'REFERENCE' | 'DONE'
/** リンク元サイトの種別。 */
export type SourceCode = 'WEB' | 'YOUTUBE' | 'GITHUB' | 'WIKIPEDIA' | 'NEWS' | 'LOCAL_FILE' | 'OTHER'
/** クリップの種別。 */
export type ClipType = 'LINK' | 'VIDEO' | 'ARTICLE' | 'REPOSITORY' | 'NEWS' | 'FILE'
/** アーカイブの絞り込み。 */
export type ArchiveFilter = 'active' | 'archived' | 'all'

/** 一覧・詳細の 1 件。 */
export interface ClipRow {
  linkClipId: number
  folderCode: FolderCode
  sourceCode: SourceCode
  clipType: ClipType
  siteName: string | null
  pageTitle: string
  url: string
  normalizedUrl: string
  summary: string | null
  aiSummary: string | null
  memo: string | null
  publisherName: string | null
  publishedAt: string | null
  videoSeconds: number | null
  thumbnailUrl: string | null
  favorite: boolean
  read: boolean
  archived: boolean
  viewCount: number
  lastViewedAt: string | null
  metaFetchedAt: string | null
  aiSummarizedAt: string | null
  version: number
  tags: string[]
  createdAt: string | null
  updatedAt: string | null
}

export interface TagOption { name: string; count: number }
export interface LaneCount { folderCode: string; count: number }

/** 一覧の応答。タグ候補とレーン件数も同時に返る。 */
export interface LinkClipWorkspace {
  rows: ClipRow[]
  tags: TagOption[]
  lanes: LaneCount[]
}

export interface ClipQuery {
  folder?: string
  source?: string
  clipType?: string
  tag?: string
  keyword?: string
  favorite?: boolean
  archive?: ArchiveFilter
}

/** 保存前プレビュー（DB へは書かない）。 */
export interface LinkPreview {
  url: string
  normalizedUrl: string
  sourceCode: SourceCode
  clipType: ClipType
  siteName: string | null
  pageTitle: string | null
  summary: string | null
  publisherName: string | null
  publishedAt: string | null
  videoSeconds: number | null
  thumbnailUrl: string | null
  localFile: boolean
}

/** 登録・更新で共通の入力。未入力は null を送る（空文字は送らない）。 */
export interface SaveClipRequest {
  url: string
  pageTitle: string
  siteName?: string | null
  folderCode?: string
  sourceCode?: string
  clipType?: string
  summary?: string | null
  aiSummary?: string | null
  memo?: string | null
  publisherName?: string | null
  publishedAt?: string | null
  videoSeconds?: number | null
  thumbnailUrl?: string | null
  favorite?: boolean
  archived?: boolean
  tags: string[]
  /** 保護者のときだけ使う。true なら、ひもづくお子さまのリンククリップにも同じ内容を登録する。 */
  alsoForStudent?: boolean
}

/** 更新は楽観的ロック用の version が必須（不一致は 409）。 */
export interface UpdateClipRequest extends SaveClipRequest {
  version: number
}

export interface FlagsRequest {
  favorite?: boolean
  read?: boolean
  archived?: boolean
}

export interface DuplicateCheck {
  duplicated: boolean
  rows: ClipRow[]
}

const http = new HttpClient({ baseUrl: '/api/user' })

export const getLinkClips = (query: ClipQuery = {}): Promise<ApiResponse<LinkClipWorkspace>> =>
  http.get('/link-clips', {
    params: {
      folder: query.folder?.trim() || undefined,
      source: query.source?.trim() || undefined,
      clipType: query.clipType?.trim() || undefined,
      tag: query.tag?.trim() || undefined,
      keyword: query.keyword?.trim() || undefined,
      favorite: query.favorite ? true : undefined,
      archive: query.archive ?? 'active'
    }
  })

/** URL から Open Graph などの情報を取得する（保存前の確認用）。 */
export const previewLinkClip = (url: string): Promise<ApiResponse<LinkPreview>> =>
  http.post('/link-clips/preview', { body: { url } })

export const createLinkClip = (body: SaveClipRequest): Promise<ApiResponse<{ row: ClipRow }>> =>
  http.post('/link-clips', { body })

export const updateLinkClip = (linkClipId: number, body: UpdateClipRequest): Promise<ApiResponse<{ row: ClipRow }>> =>
  http.put(`/link-clips/${linkClipId}`, { body })

export const deleteLinkClip = (linkClipId: number): Promise<ApiResponse<{ deleted: boolean }>> =>
  http.delete(`/link-clips/${linkClipId}`)

export const updateLinkClipFlags = (linkClipId: number, body: FlagsRequest): Promise<ApiResponse<{ row: ClipRow }>> =>
  http.post(`/link-clips/${linkClipId}/flags`, { body })

/** リンクを開いたことを記録する（閲覧回数 +1・既読化）。 */
export const recordLinkClipView = (linkClipId: number): Promise<ApiResponse<{ row: ClipRow }>> =>
  http.post(`/link-clips/${linkClipId}/view`, {})

/** 同じ URL の既存クリップを確認する（重複登録自体は禁止しない）。 */
export const checkLinkClipDuplicates = (url: string): Promise<ApiResponse<DuplicateCheck>> =>
  http.get('/link-clips/duplicates', { params: { url } })
