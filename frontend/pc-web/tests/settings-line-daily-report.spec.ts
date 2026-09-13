import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = path.resolve(webRoot, '../..')
const read = (relativePath: string): string => readFileSync(path.join(webRoot, relativePath), 'utf8')

/**
 * システム設定「学習日報 → LINE通知」（2.0 の日報 LINE 送信の設定）。
 *
 * 送信そのものは未実装だが、設定の受け皿は 2026-09-12 に用意した。
 * 置き場所は「LINE連携」ではなく**学習日報**のページ（ユーザーの指定）。
 *
 * 画面（study2SettingRuntime）のフィールドキーと、
 * サーバ側の対応表（SettingPageFields）・DB カタログ（COM_設定項目）が
 * ずれないようにソースで見張る（片方だけ足すと画面に出ない／保存できない）。
 */
const FIELD_KEYS = [
  'lineDailyReportEnabled',
  'lineDailyReportTo',
  'lineDailyReportSendOnResubmit',
  'lineDailyReportTemplate',
  'lineDailyReportLessonTemplate'
]

const SETTING_KEYS = [
  'LINE_DAILY_REPORT_ENABLED',
  'LINE_DAILY_REPORT_TO',
  'LINE_DAILY_REPORT_SEND_ON_RESUBMIT',
  'LINE_DAILY_REPORT_TEMPLATE',
  'LINE_DAILY_REPORT_LESSON_TEMPLATE'
]

describe('設定：学習日報の「LINE通知」', () => {
  it('画面の定義に 5 項目がある（学習日報の LINE通知グループ）', () => {
    const runtime = read('src/features/system-settings/study2SettingRuntime.ts')

    for (const key of FIELD_KEYS) {
      expect(runtime, key).toContain(`'${key}'`)
    }
    expect(runtime).toContain("'LINE通知'")
    // テンプレートは複数行なので textarea で出す
    expect(runtime).toContain("f('lineDailyReportTemplate','メッセージ本文のテンプレート','LINE通知','textarea'")
    expect(runtime).toContain("f('lineDailyReportLessonTemplate','授業1件のテンプレート','LINE通知','textarea'")
  })

  it('画面では「学習日報」のページに置き、「LINE連携」には置かない（ユーザーの指定）', () => {
    const runtime = read('src/features/system-settings/study2SettingRuntime.ts')
    const dailyReport = runtime.slice(runtime.indexOf("id: 'daily_report'"), runtime.indexOf("id: 'english_word_detail_ai'"))
    const line = runtime.slice(runtime.indexOf("id: 'line'"), runtime.indexOf("id: 'line'") + 1200)

    // 学習日報のカテゴリに 5 項目が入っている
    for (const key of FIELD_KEYS) {
      expect(dailyReport, key).toContain(`'${key}'`)
    }
    // LINE連携には入っていない（Messaging API と Webhook の設定だけ）
    for (const key of FIELD_KEYS) {
      expect(line, key).not.toContain(`'${key}'`)
    }
    expect(line).toContain("'lineMessagingChannelAccessToken'")
    expect(line).not.toContain('学習日報')
  })

  it('サーバ側の対応表（SettingPageFields）が同じ 5 項目を DAILY_REPORT に割り当てている', () => {
    const java = readFileSync(
      path.join(repoRoot, 'backend/admin-api/src/main/java/com/study21/admin/setting/SettingPageFields.java'),
      'utf8'
    )

    for (let index = 0; index < FIELD_KEYS.length; index += 1) {
      expect(java, FIELD_KEYS[index]).toContain(`put(m, "${FIELD_KEYS[index]}"`)
      expect(java, SETTING_KEYS[index]).toContain(`"DAILY_REPORT", "${SETTING_KEYS[index]}"`)
    }
    // LINE ページには割り当てていない
    expect(java).not.toContain('"LINE", "LINE_DAILY_REPORT_')
  })

  it('DB カタログ（COM_設定項目）にも同じ 5 項目がある（ページ区分は DAILY_REPORT）', () => {
    const sql = readFileSync(path.join(repoRoot, 'database/設定/TBL_COM_設定項目_init.sql'), 'utf8')
    const rename = readFileSync(
      path.join(repoRoot, 'database/移行/MIG_COM_設定項目_学習日報LINE通知_20260912.sql'),
      'utf8'
    )

    for (const key of SETTING_KEYS) {
      expect(sql, key).toContain(`'DAILY_REPORT','${key}'`)
      expect(rename, key).toContain(`'${key}'`)
    }
    // true/false の項目は ENUM で有効値を絞る
    expect(sql).toContain("VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_ENABLED','ENUM','0','true,false'")
    // LINE ページの行としては登録しない
    expect(sql).not.toContain("VALUES ('LINE','LINE_DAILY_REPORT_")
    // 付け替えの移行は「カタログ追加 → 値の付け替え → 旧カタログ削除」の順（外部キーのため）
    expect(rename.indexOf('INSERT INTO public."COM_設定項目"'))
      .toBeLessThan(rename.indexOf('UPDATE public."COM_設定情報"'))
    expect(rename.indexOf('UPDATE public."COM_設定情報"'))
      .toBeLessThan(rename.indexOf('DELETE FROM public."COM_設定項目"'))
  })

  it('テンプレートの置換変数が説明に書いてある（本文・授業）', () => {
    const runtime = read('src/features/system-settings/study2SettingRuntime.ts')

    for (const token of ['{{日付}}', '{{曜日}}', '{{記入者}}', '{{時限数}}', '{{授業一覧}}', '{{振り返り}}', '{{今夜の勉強}}', '{{提出日時}}']) {
      expect(runtime, token).toContain(token)
    }
    for (const token of ['{{時限}}', '{{教科}}', '{{授業内容}}', '{{掌握度}}', '{{学習集中度}}', '{{学習量}}', '{{学習態度}}', '{{ノート}}']) {
      expect(runtime, token).toContain(token)
    }
  })
})
