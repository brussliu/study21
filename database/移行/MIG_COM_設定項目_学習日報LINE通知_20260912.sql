-- ============================================================================
-- Study 2.1  学習日報の LINE 通知設定を「LINE連携」から「学習日報」へ移す
--
-- 背景: 2026-09-12 に LINE連携（ページ区分 LINE）へ追加したが、
--       ユーザーの指定で設定画面の「学習日報」ページへ移した。
--       設定キー（LINE_DAILY_REPORT_*）は変えず、ページ区分だけを付け替える。
--
-- 対象キー:
--   LINE_DAILY_REPORT_ENABLED / _TO / _SEND_ON_RESUBMIT / _TEMPLATE / _LESSON_TEMPLATE
--
-- 手順（COM_設定情報 → COM_設定項目 の外部キーがあるため、この順番が必須）:
--   1. 移動先（DAILY_REPORT）のカタログ行を用意する
--   2. 設定値のページ区分を付け替える（外部キーが満たされる）
--   3. 移動元（LINE）のカタログ行を消す（参照されていないので消せる）
--
-- 冪等: 再実行しても 1〜3 はいずれも何も起きない。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

-- 1. 移動先のカタログ行
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_ENABLED','ENUM','0','true,false','学習日報：提出時に LINE へ送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_TO','TEXT','0',NULL,'学習日報：送信先ID（userId / groupId / roomId。複数はカンマ区切り。空なら「デフォルト送信先ID」へ送る）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_SEND_ON_RESUBMIT','ENUM','0','true,false','学習日報：再提出（提出後に編集して再度提出）でも送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_TEMPLATE','TEXT','0',NULL,'学習日報：メッセージ本文のテンプレート（{{日付}}・{{記入者}}・{{授業一覧}}・{{振り返り}}・{{今夜の勉強}}・{{提出日時}}・{{時限数}}・{{曜日}} を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('DAILY_REPORT','LINE_DAILY_REPORT_LESSON_TEMPLATE','TEXT','0',NULL,'学習日報：授業1件のテンプレート（{{時限}}・{{教科}}・{{授業内容}}・{{掌握度}}・{{学習集中度}}・{{学習量}}・{{学習態度}}・{{ノート}} を置換。{{授業一覧}} の中で1件ずつ使う）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- 2. 設定値のページ区分を付け替える
UPDATE public."COM_設定情報"
   SET "ページ区分" = 'DAILY_REPORT', "更新日時" = CURRENT_TIMESTAMP
 WHERE "ページ区分" = 'LINE'
   AND "設定キー" IN ('LINE_DAILY_REPORT_ENABLED', 'LINE_DAILY_REPORT_TO', 'LINE_DAILY_REPORT_SEND_ON_RESUBMIT',
                     'LINE_DAILY_REPORT_TEMPLATE', 'LINE_DAILY_REPORT_LESSON_TEMPLATE');

-- 3. 移動元のカタログ行を消す
DELETE FROM public."COM_設定項目"
 WHERE "ページ区分" = 'LINE'
   AND "設定キー" IN ('LINE_DAILY_REPORT_ENABLED', 'LINE_DAILY_REPORT_TO', 'LINE_DAILY_REPORT_SEND_ON_RESUBMIT',
                     'LINE_DAILY_REPORT_TEMPLATE', 'LINE_DAILY_REPORT_LESSON_TEMPLATE');

-- 確認用
--   SELECT "ページ区分", "設定キー" FROM public."COM_設定項目"
--    WHERE "設定キー" LIKE 'LINE_DAILY_REPORT%' ORDER BY "設定キー";   -- すべて DAILY_REPORT
--   SELECT "ページ区分", count(*) FROM public."COM_設定項目" GROUP BY 1 ORDER BY 1;
