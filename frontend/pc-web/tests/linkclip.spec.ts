import { describe, expect, it } from 'vitest'
import {
  FOLDER_CHOICES, FOLDER_LANES, clipTypeLabel, folderLabel, formatDuration, formatTags,
  isLocalFile, parseTags, sourceIcon, sourceLabel
} from '@/features/linkclip/linkclip'

describe('parseTags（カンマ区切りのタグ入力）', () => {
  it('カンマ・全角カンマで分割し、前後の空白を落とす', () => {
    expect(parseTags('英語, listening , youtube')).toEqual(['英語', 'listening', 'youtube'])
    expect(parseTags('英語、数学')).toEqual(['英語', '数学'])
  })

  it('空要素と空白だけの要素を捨てる', () => {
    expect(parseTags(' a , , b ,')).toEqual(['a', 'b'])
    expect(parseTags('   ')).toEqual([])
    expect(parseTags(null)).toEqual([])
  })

  it('大文字小文字を無視して重複を排除する', () => {
    expect(parseTags('YouTube, youtube, YOUTUBE')).toEqual(['YouTube'])
  })

  it('空白を1つにまとめ、100文字を超えるタグは捨てる', () => {
    expect(parseTags('a   b')).toEqual(['a b'])
    expect(parseTags('x'.repeat(101))).toEqual([])
    expect(parseTags('x'.repeat(100))).toEqual(['x'.repeat(100)])
  })
})

describe('formatDuration', () => {
  it('m:ss / h:mm:ss で表示する', () => {
    expect(formatDuration(0)).toBe('0:00')
    expect(formatDuration(9)).toBe('0:09')
    expect(formatDuration(59)).toBe('0:59')
    expect(formatDuration(60)).toBe('1:00')
    expect(formatDuration(3599)).toBe('59:59')
    expect(formatDuration(3600)).toBe('1:00:00')
    expect(formatDuration(3725)).toBe('1:02:05')
  })

  it('未入力・不正値は「—」', () => {
    expect(formatDuration(null)).toBe('—')
    expect(formatDuration(undefined)).toBe('—')
    expect(formatDuration(-1)).toBe('—')
  })
})

describe('ラベルとアイコン', () => {
  it('フォルダ・ソース・種別のラベルを返す', () => {
    expect(folderLabel('INBOX')).toBe('未整理')
    expect(folderLabel('DONE')).toBe('完了')
    expect(folderLabel(null)).toBe('未整理')
    expect(sourceLabel('YOUTUBE')).toBe('YouTube')
    expect(sourceLabel('LOCAL_FILE')).toBe('Local File')
    expect(sourceLabel('UNKNOWN')).toBe('Other')
    expect(clipTypeLabel('VIDEO')).toBe('動画')
    expect(clipTypeLabel(null)).toBe('リンク')
  })

  it('ソース別アイコンはスプライトに存在する名前を返す', () => {
    expect(sourceIcon('WEB')).toBe('globe')
    expect(sourceIcon('YOUTUBE')).toBe('video')
    expect(sourceIcon('LOCAL_FILE')).toBe('file')
    expect(sourceIcon('UNKNOWN')).toBe('bookmark')
  })

  it('仕分けレーンは 8 件で、コンポーザの選択肢は 5 件（未整理〜完了）', () => {
    expect(FOLDER_LANES.map((lane) => lane.code)).toEqual([
      'ALL', 'INBOX', 'READ_LATER', 'LEARNING', 'REFERENCE', 'DONE', 'FAVORITE', 'ARCHIVED'
    ])
    expect(FOLDER_CHOICES.map((choice) => choice.code)).toEqual([
      'INBOX', 'READ_LATER', 'LEARNING', 'REFERENCE', 'DONE'
    ])
  })
})

describe('isLocalFile', () => {
  it('ソースが LOCAL_FILE、または file:// / ドライブパスなら true', () => {
    expect(isLocalFile({ sourceCode: 'LOCAL_FILE', url: 'https://example.com' })).toBe(true)
    expect(isLocalFile({ sourceCode: 'WEB', url: 'file:///D:/docs/a.pdf' })).toBe(true)
    expect(isLocalFile({ sourceCode: 'WEB', url: 'D:\\docs\\a.pdf' })).toBe(true)
    expect(isLocalFile({ sourceCode: 'WEB', url: 'https://example.com' })).toBe(false)
  })
})

describe('formatTags', () => {
  it('カンマ+空白区切りの編集用文字列にする', () => {
    expect(formatTags(['英語', 'listening'])).toBe('英語, listening')
    expect(formatTags(null)).toBe('')
  })
})
