-- ============================================================================
-- Study 2.1  設定カタログ／初期値の追加：LINE連携の「学習日報の通知」
--
-- 追加する設定（ページ区分 = LINE）:
--   LINE_DAILY_REPORT_ENABLED          true/false  提出時に LINE へ送るか（既定 false）
--   LINE_DAILY_REPORT_TO               text        送信先ID（空なら デフォルト送信先ID）
--   LINE_DAILY_REPORT_SEND_ON_RESUBMIT true/false  再提出でも送るか（既定 false）
--   LINE_DAILY_REPORT_TEMPLATE         text        メッセージ本文のテンプレート
--   LINE_DAILY_REPORT_LESSON_TEMPLATE  text        授業1件のテンプレート
--
-- 背景: 2.0 は日報の保存後に LINE（Messaging API の push）へ送っていた。
--       2.1 は【提出】時に送る予定で、設定は 2.0 と同じ「LINE連携」に置く（ユーザーの指定）。
--       送信処理そのものは未実装（この移行は設定の受け皿だけを用意する）。
-- 置換変数:
--   本文   {{日付}} {{曜日}} {{記入者}} {{時限数}} {{授業一覧}} {{振り返り}} {{今夜の勉強}} {{提出日時}}
--   授業   {{時限}} {{教科}} {{授業内容}} {{掌握度}} {{学習集中度}} {{学習量}} {{学習態度}} {{ノート}}
-- 冪等: カタログは ON CONFLICT DO NOTHING。値は既存行を上書きしない（管理者が設定済みの値を壊さない）。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

-- ---------------- カタログ（COM_設定項目） ----------------
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_DAILY_REPORT_ENABLED','ENUM','0','true,false','学習日報：提出時に LINE へ送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_DAILY_REPORT_TO','TEXT','0',NULL,'学習日報：送信先ID（userId / groupId / roomId。複数はカンマ区切り。空なら「デフォルト送信先ID」へ送る）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_DAILY_REPORT_SEND_ON_RESUBMIT','ENUM','0','true,false','学習日報：再提出（提出後に編集して再度提出）でも送るか（true/false）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_DAILY_REPORT_TEMPLATE','TEXT','0',NULL,'学習日報：メッセージ本文のテンプレート（{{日付}}・{{記入者}}・{{授業一覧}}・{{振り返り}}・{{今夜の勉強}}・{{提出日時}}・{{時限数}}・{{曜日}} を置換）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;
INSERT INTO public."COM_設定項目" ("ページ区分","設定キー","値タイプ","必須フラグ","有効値","説明")
VALUES ('LINE','LINE_DAILY_REPORT_LESSON_TEMPLATE','TEXT','0',NULL,'学習日報：授業1件のテンプレート（{{時限}}・{{教科}}・{{授業内容}}・{{掌握度}}・{{学習集中度}}・{{学習量}}・{{学習態度}}・{{ノート}} を置換。{{授業一覧}} の中で1件ずつ使う）')
ON CONFLICT ("ページ区分","設定キー") DO NOTHING;

-- ---------------- 初期値（COM_設定情報） ----------------
-- 送信は既定で「しない」（送信の実装後に管理者が true にする）。空の送信先は登録しない。
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('LINE','LINE_DAILY_REPORT_ENABLED','GLOBAL','false')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('LINE','LINE_DAILY_REPORT_SEND_ON_RESUBMIT','GLOBAL','false')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('LINE','LINE_DAILY_REPORT_TEMPLATE','GLOBAL','【学習日報】
日付：{{日付}}（{{曜日}}）
記入者：{{記入者}}

■ 授業内容（{{時限数}}限）
{{授業一覧}}
■ 全体の振り返り
{{振り返り}}

■ 今夜の勉強内容
{{今夜の勉強}}

提出日時：{{提出日時}}')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;
INSERT INTO public."COM_設定情報" ("ページ区分","設定キー","スコープ","設定値")
VALUES ('LINE','LINE_DAILY_REPORT_LESSON_TEMPLATE','GLOBAL','・{{時限}}限 / {{教科}}
{{授業内容}}
掌握度：{{掌握度}}
集中度：{{学習集中度}}　学習量：{{学習量}}　態度：{{学習態度}}
ノート：{{ノート}}
────────────')
ON CONFLICT ("ページ区分","設定キー") WHERE "スコープ"='GLOBAL' DO NOTHING;

-- 確認用
--   SELECT "設定キー", left("設定値", 20) FROM public."COM_設定情報"
--    WHERE "ページ区分"='LINE' ORDER BY "設定キー";
