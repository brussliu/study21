-- ============================================================================
-- Study 2.0 -> 2.1  日本語勉強（単語情報管理・単語テスト・単語勉強状況）移行
-- 移行元: study3.public.STY_日本語単語* / STY_日本語学習日次情報
-- 移行先: study21.public.JPN_*
-- ----------------------------------------------------------------------------
-- ！！ 移行元は study2 ではなく **study3** ！！（英語の単語テスト・熟語は study2 にある）
--   同一 PostgreSQL インスタンス（192.168.0.100:54320）に study2 / study3 / study21 が
--   同居しているので dblink('dbname=study3', ...) で接続する。
--
-- 事前条件:
--   1. database/日本語勉強/TBL_JPN_*.sql（10 ファイル）を適用済みであること
--   2. 移行元 study3 が同一インスタンスに存在すること（dblink で接続）
--
-- 実行例:
--   psql -h 192.168.0.100 -p 54320 -U postgres -d study21 -v ON_ERROR_STOP=1 \
--        -f MIG_JPN_日本語勉強_20260913.sql
--
-- 移行元テーブルと件数（2026-09-13 実測）:
--   STY_日本語単語母表                    9,847 -> JPN_単語情報
--   STY_日本語単語収録情報                9,886 -> JPN_単語収録情報
--   STY_日本語単語詳細情報                  383 -> JPN_単語詳細情報
--     〃 _語義情報 653 / _例文情報 1,011 / _発音情報 384 /
--        _コロケーション情報 926 / _関連語情報 676 / _使用注意情報 485
--                                          -> JPN_単語詳細情報.詳細JSON に集約
--   STY_日本語単語問題情報                1,700 -> JPN_単語問題情報
--   STY_日本語単語問題選択肢情報          6,800 -> JPN_単語問題選択肢情報
--   STY_日本語単語テスト情報                 15 -> JPN_テスト情報
--   STY_日本語単語テスト出題情報          1,105 -> JPN_テスト出題情報
--   STY_日本語単語学習状況情報              213 -> JPN_学習状況情報
--   STY_日本語単語技能習得情報              906 -> JPN_技能習得情報
--   STY_日本語学習日次情報                   13 -> JPN_学習日次情報
--   （STY_日本語単語テスト課題情報 1,326 / 〃回答履歴情報 1,113 は 2.1 が持たないため移行しない）
--
-- 持ち主の対応（日報・TODO・Web閲覧履歴と同じ判断）:
--   'liu'（劉季＝保護者。保護者 bruss.ji.liu@gmail.com） -> アカウント 1
--   'ljz'（劉競澤＝生徒。ricky.jingze@gmail.com）        -> アカウント 2
--   学習データの ユーザーID は **全件 'liu'**。対応表に無い ユーザーID は
--   利用者アカウントID を NULL にできない（NOT NULL）ので、その行は移行せず
--   SKIP 件数を NOTICE に出す（行を捨てたことが分かるようにする）。
--
-- 値の対応で決めたこと:
--   * 空文字は NULL に寄せる（2.1 は「未設定は NULL」）。テスト.レベル は全件 空文字。
--   * 問題選択肢.誤答区分 の空文字（正解の行）は NULL。
--   * 出題状態 WAITING -> PENDING（2.1 のコード）。
--   * テスト.正解数/不正解数 は 2.0 の 正解課題数/不正解課題数。
--     2.0 の 課題数/完了課題数 は 2.1 が課題テーブルを持たないため移行しない。
--   * 監査列は人が実行した記録ではないので 登録者/更新者アカウントID = NULL、
--     登録元コード/更新元コード = 'MIGRATION'。
--   * 登録日時/更新日時 は 2.0 の値をそのまま引き継ぐ（NULL は移行時刻）。
--
-- 冪等性:
--   すべての 2.0 の ID を 旧*ID 列（一意索引）に保持し、
--   ON CONFLICT ("旧*ID") DO NOTHING で再実行しても増えない。
--   子テーブルは親の 旧ID -> 新ID を引いて入れ、親が入っていない行は入れない。
--
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS dblink;

-- ---------------------------------------------------------------------------
-- 1. 2.0 のデータを読み込む（study3 へ dblink 接続）
-- ---------------------------------------------------------------------------

CREATE TEMP TABLE migr_jpn_owner (
    "旧ユーザーID" TEXT PRIMARY KEY,
    "アカウントID" BIGINT NOT NULL
) ON COMMIT DROP;
INSERT INTO migr_jpn_owner VALUES ('liu', 1), ('ljz', 2);

-- 1-1. 単語母表
CREATE TEMP TABLE migr_jpn_word ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "日本語単語ID", "見出し語", "読み", "見出し語キー", "読みキー",
              "代表JLPTレベル", "代表品詞", "状態", "登録日時", "更新日時"
         FROM public."STY_日本語単語母表"
       $remote$
       ) AS remote (
           "日本語単語ID"     BIGINT,
           "見出し語"         TEXT,
           "読み"             TEXT,
           "見出し語キー"     TEXT,
           "読みキー"         TEXT,
           "代表JLPTレベル"   TEXT,
           "代表品詞"         TEXT,
           "状態"             TEXT,
           "登録日時"         TIMESTAMP,
           "更新日時"         TIMESTAMP
       );

-- 1-2. 教材収録情報
CREATE TEMP TABLE migr_jpn_collect ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "収録ID", "日本語単語ID", "レベル", "書籍", "分類", "単語SEQ",
              "掲載見出し語", "掲載読み", "掲載品詞", "掲載中国語意味",
              "出典JSON", "状態", "登録日時", "更新日時"
         FROM public."STY_日本語単語収録情報"
       $remote$
       ) AS remote (
           "収録ID"           BIGINT,
           "日本語単語ID"     BIGINT,
           "レベル"           TEXT,
           "書籍"             TEXT,
           "分類"             TEXT,
           "単語SEQ"          INTEGER,
           "掲載見出し語"     TEXT,
           "掲載読み"         TEXT,
           "掲載品詞"         TEXT,
           "掲載中国語意味"   TEXT,
           "出典JSON"         JSONB,
           "状態"             TEXT,
           "登録日時"         TIMESTAMP,
           "更新日時"         TIMESTAMP
       );

-- 1-3. AI 詳細（本体）
CREATE TEMP TABLE migr_jpn_detail ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "日本語単語詳細ID", "日本語単語ID", "品詞", "活用種類", "自他区分",
              "JLPTレベル", "重要度", "代表中国語意味", "日本語説明", "中国語説明",
              "内容版数", "構造化スキーマ版", "構造化JSON", "手動修正済フラグ",
              "AIプロバイダ", "AIモデル", "登録日時", "更新日時"
         FROM public."STY_日本語単語詳細情報"
       $remote$
       ) AS remote (
           "日本語単語詳細ID"   BIGINT,
           "日本語単語ID"       BIGINT,
           "品詞"               TEXT,
           "活用種類"           TEXT,
           "自他区分"           TEXT,
           "JLPTレベル"         TEXT,
           "重要度"             SMALLINT,
           "代表中国語意味"     TEXT,
           "日本語説明"         TEXT,
           "中国語説明"         TEXT,
           "内容版数"           INTEGER,
           "構造化スキーマ版"   TEXT,
           "構造化JSON"         JSONB,
           "手動修正済フラグ"   BOOLEAN,
           "AIプロバイダ"       TEXT,
           "AIモデル"           TEXT,
           "登録日時"           TIMESTAMP,
           "更新日時"           TIMESTAMP
       );

-- 1-4. AI 詳細の 6 子テーブル
CREATE TEMP TABLE migr_jpn_sense ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "語義ID", "日本語単語詳細ID", "語義番号", "意味_日本語", "意味_中国語",
              "使用場面", "文体区分", "補足説明_日本語", "補足説明_中国語", "表示順"
         FROM public."STY_日本語単語詳細_語義情報"
       $remote$
       ) AS remote (
           "語義ID"               BIGINT,
           "日本語単語詳細ID"     BIGINT,
           "語義番号"             SMALLINT,
           "意味_日本語"          TEXT,
           "意味_中国語"          TEXT,
           "使用場面"             TEXT,
           "文体区分"             TEXT,
           "補足説明_日本語"      TEXT,
           "補足説明_中国語"      TEXT,
           "表示順"               SMALLINT
       );

CREATE TEMP TABLE migr_jpn_example ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "例文ID", "日本語単語詳細ID", "語義ID", "例文_日本語", "例文読み",
              "例文_中国語", "文脈意味_日本語", "文脈意味_中国語", "出典", "表示順"
         FROM public."STY_日本語単語詳細_例文情報"
       $remote$
       ) AS remote (
           "例文ID"               BIGINT,
           "日本語単語詳細ID"     BIGINT,
           "語義ID"               BIGINT,
           "例文_日本語"          TEXT,
           "例文読み"             TEXT,
           "例文_中国語"          TEXT,
           "文脈意味_日本語"      TEXT,
           "文脈意味_中国語"      TEXT,
           "出典"                 TEXT,
           "表示順"               SMALLINT
       );

CREATE TEMP TABLE migr_jpn_pron ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "発音ID", "日本語単語詳細ID", "読み", "アクセント表記", "アクセント型",
              "モーラ数", "音声URL", "音声プロバイダ", "表示順"
         FROM public."STY_日本語単語詳細_発音情報"
       $remote$
       ) AS remote (
           "発音ID"               BIGINT,
           "日本語単語詳細ID"     BIGINT,
           "読み"                 TEXT,
           "アクセント表記"       TEXT,
           "アクセント型"         SMALLINT,
           "モーラ数"             SMALLINT,
           "音声URL"              TEXT,
           "音声プロバイダ"       TEXT,
           "表示順"               SMALLINT
       );

CREATE TEMP TABLE migr_jpn_collo ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "コロケーションID", "日本語単語詳細ID", "表現", "読み", "意味_中国語",
              "例文_日本語", "例文_中国語", "表示順"
         FROM public."STY_日本語単語詳細_コロケーション情報"
       $remote$
       ) AS remote (
           "コロケーションID"     BIGINT,
           "日本語単語詳細ID"     BIGINT,
           "表現"                 TEXT,
           "読み"                 TEXT,
           "意味_中国語"          TEXT,
           "例文_日本語"          TEXT,
           "例文_中国語"          TEXT,
           "表示順"               SMALLINT
       );

CREATE TEMP TABLE migr_jpn_related ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "関連語ID", "日本語単語詳細ID", "関連先日本語単語ID", "関係区分", "表記",
              "読み", "意味_中国語", "相違点_日本語", "相違点_中国語",
              "E選択肢候補フラグ", "表示順"
         FROM public."STY_日本語単語詳細_関連語情報"
       $remote$
       ) AS remote (
           "関連語ID"                 BIGINT,
           "日本語単語詳細ID"         BIGINT,
           "関連先日本語単語ID"       BIGINT,
           "関係区分"                 TEXT,
           "表記"                     TEXT,
           "読み"                     TEXT,
           "意味_中国語"              TEXT,
           "相違点_日本語"            TEXT,
           "相違点_中国語"            TEXT,
           "E選択肢候補フラグ"        BOOLEAN,
           "表示順"                   SMALLINT
       );

CREATE TEMP TABLE migr_jpn_caution ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "使用注意ID", "日本語単語詳細ID", "注意区分", "説明_日本語", "説明_中国語",
              "誤用例", "正用例", "表示順"
         FROM public."STY_日本語単語詳細_使用注意情報"
       $remote$
       ) AS remote (
           "使用注意ID"           BIGINT,
           "日本語単語詳細ID"     BIGINT,
           "注意区分"             TEXT,
           "説明_日本語"          TEXT,
           "説明_中国語"          TEXT,
           "誤用例"               TEXT,
           "正用例"               TEXT,
           "表示順"               SMALLINT
       );

-- 1-5. C/D/E 問題と選択肢
CREATE TEMP TABLE migr_jpn_question ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "問題ID", "日本語単語ID", "問題種別", "問題番号",
              "問題文_日本語", "問題文_中国語", "対象表記", "対象読み",
              "例文_日本語", "例文読み", "音声テキスト",
              "正解値", "正解補足", "解説_日本語", "解説_中国語",
              "難易度", "状態", "内容版数", "構造化JSON", "登録日時", "更新日時"
         FROM public."STY_日本語単語問題情報"
       $remote$
       ) AS remote (
           "問題ID"           BIGINT,
           "日本語単語ID"     BIGINT,
           "問題種別"         TEXT,
           "問題番号"         SMALLINT,
           "問題文_日本語"    TEXT,
           "問題文_中国語"    TEXT,
           "対象表記"         TEXT,
           "対象読み"         TEXT,
           "例文_日本語"      TEXT,
           "例文読み"         TEXT,
           "音声テキスト"     TEXT,
           "正解値"           TEXT,
           "正解補足"         TEXT,
           "解説_日本語"      TEXT,
           "解説_中国語"      TEXT,
           "難易度"           TEXT,
           "状態"             TEXT,
           "内容版数"         INTEGER,
           "構造化JSON"       JSONB,
           "登録日時"         TIMESTAMP,
           "更新日時"         TIMESTAMP
       );

CREATE TEMP TABLE migr_jpn_choice ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "選択肢ID", "問題ID", "表示順", "選択肢値", "選択肢読み",
              "正解フラグ", "誤答区分", "説明_日本語", "説明_中国語"
         FROM public."STY_日本語単語問題選択肢情報"
       $remote$
       ) AS remote (
           "選択肢ID"         BIGINT,
           "問題ID"           BIGINT,
           "表示順"           SMALLINT,
           "選択肢値"         TEXT,
           "選択肢読み"       TEXT,
           "正解フラグ"       BOOLEAN,
           "誤答区分"         TEXT,
           "説明_日本語"      TEXT,
           "説明_中国語"      TEXT
       );

-- 1-6. テストと出題
CREATE TEMP TABLE migr_jpn_test ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "テストID", "テスト番号", "ユーザーID", "テスト種別",
              "レベル", "書籍", "分類開始", "分類終了", "難易度", "出題方式",
              "検索条件JSON", "出題数", "完了出題数",
              "課題数", "完了課題数", "正解課題数", "不正解課題数",
              "状態", "開始日時", "終了日時", "最終学習日時", "有効学習時間ms",
              "登録日時", "更新日時"
         FROM public."STY_日本語単語テスト情報"
       $remote$
       ) AS remote (
           "テストID"         BIGINT,
           "テスト番号"       TEXT,
           "ユーザーID"       TEXT,
           "テスト種別"       TEXT,
           "レベル"           TEXT,
           "書籍"             TEXT,
           "分類開始"         TEXT,
           "分類終了"         TEXT,
           "難易度"           TEXT,
           "出題方式"         TEXT,
           "検索条件JSON"     JSONB,
           "出題数"           INTEGER,
           "完了出題数"       INTEGER,
           "課題数"           INTEGER,
           "完了課題数"       INTEGER,
           "正解課題数"       INTEGER,
           "不正解課題数"     INTEGER,
           "状態"             TEXT,
           "開始日時"         TIMESTAMP,
           "終了日時"         TIMESTAMP,
           "最終学習日時"     TIMESTAMP,
           "有効学習時間ms"   BIGINT,
           "登録日時"         TIMESTAMP,
           "更新日時"         TIMESTAMP
       );

CREATE TEMP TABLE migr_jpn_test_question ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "出題ID", "テストID", "出題順", "日本語単語ID", "収録ID",
              "出題状態", "最終判定", "回答回数", "誤答回数", "有効学習時間ms",
              "回答完了日時", "単語スナップショットJSON", "登録日時", "更新日時"
         FROM public."STY_日本語単語テスト出題情報"
       $remote$
       ) AS remote (
           "出題ID"                   BIGINT,
           "テストID"                 BIGINT,
           "出題順"                   INTEGER,
           "日本語単語ID"             BIGINT,
           "収録ID"                   BIGINT,
           "出題状態"                 TEXT,
           "最終判定"                 TEXT,
           "回答回数"                 INTEGER,
           "誤答回数"                 INTEGER,
           "有効学習時間ms"           BIGINT,
           "回答完了日時"             TIMESTAMP,
           "単語スナップショットJSON" JSONB,
           "登録日時"                 TIMESTAMP,
           "更新日時"                 TIMESTAMP
       );

-- 1-7. 学習状況・技能習得・日次
CREATE TEMP TABLE migr_jpn_status ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "ユーザーID", "日本語単語ID", "学習状態", "総合習得度", "習得済フラグ",
              "習得日時", "お気に入りフラグ", "お気に入り日時", "A確認回数",
              "回答回数", "正解回数", "不正解回数", "連続正解回数", "最大連続正解回数",
              "有効学習時間ms", "最終テスト種別", "最終課題コード", "最終判定",
              "初回学習日時", "最終学習日時", "次回復習日時", "復習間隔日数",
              "登録日時", "更新日時"
         FROM public."STY_日本語単語学習状況情報"
       $remote$
       ) AS remote (
           "ユーザーID"           TEXT,
           "日本語単語ID"         BIGINT,
           "学習状態"             TEXT,
           "総合習得度"           NUMERIC(5,2),
           "習得済フラグ"         BOOLEAN,
           "習得日時"             TIMESTAMP,
           "お気に入りフラグ"     BOOLEAN,
           "お気に入り日時"       TIMESTAMP,
           "A確認回数"            INTEGER,
           "回答回数"             INTEGER,
           "正解回数"             INTEGER,
           "不正解回数"           INTEGER,
           "連続正解回数"         INTEGER,
           "最大連続正解回数"     INTEGER,
           "有効学習時間ms"       BIGINT,
           "最終テスト種別"       TEXT,
           "最終課題コード"       TEXT,
           "最終判定"             TEXT,
           "初回学習日時"         TIMESTAMP,
           "最終学習日時"         TIMESTAMP,
           "次回復習日時"         TIMESTAMP,
           "復習間隔日数"         INTEGER,
           "登録日時"             TIMESTAMP,
           "更新日時"             TIMESTAMP
       );

CREATE TEMP TABLE migr_jpn_skill ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "ユーザーID", "日本語単語ID", "テスト種別", "技能区分", "学習状態",
              "習得度", "回答回数", "正解回数", "不正解回数",
              "連続正解回数", "最大連続正解回数", "最終判定",
              "最終学習日時", "次回復習日時", "登録日時", "更新日時"
         FROM public."STY_日本語単語技能習得情報"
       $remote$
       ) AS remote (
           "ユーザーID"           TEXT,
           "日本語単語ID"         BIGINT,
           "テスト種別"           TEXT,
           "技能区分"             TEXT,
           "学習状態"             TEXT,
           "習得度"               NUMERIC(5,2),
           "回答回数"             INTEGER,
           "正解回数"             INTEGER,
           "不正解回数"           INTEGER,
           "連続正解回数"         INTEGER,
           "最大連続正解回数"     INTEGER,
           "最終判定"             TEXT,
           "最終学習日時"         TIMESTAMP,
           "次回復習日時"         TIMESTAMP,
           "登録日時"             TIMESTAMP,
           "更新日時"             TIMESTAMP
       );

CREATE TEMP TABLE migr_jpn_daily ON COMMIT DROP AS
SELECT remote.*
  FROM dblink(
       'dbname=study3',
       $remote$
       SELECT "ユーザーID", "学習日", "有効学習時間ms",
              "A学習時間ms", "B学習時間ms", "C学習時間ms", "D学習時間ms", "E学習時間ms",
              "学習単語数", "完了テスト数", "完了課題数", "正解課題数", "不正解課題数",
              "登録日時", "更新日時"
         FROM public."STY_日本語学習日次情報"
       $remote$
       ) AS remote (
           "ユーザーID"           TEXT,
           "学習日"               DATE,
           "有効学習時間ms"       BIGINT,
           "A学習時間ms"          BIGINT,
           "B学習時間ms"          BIGINT,
           "C学習時間ms"          BIGINT,
           "D学習時間ms"          BIGINT,
           "E学習時間ms"          BIGINT,
           "学習単語数"           INTEGER,
           "完了テスト数"         INTEGER,
           "完了課題数"           INTEGER,
           "正解課題数"           INTEGER,
           "不正解課題数"         INTEGER,
           "登録日時"             TIMESTAMP,
           "更新日時"             TIMESTAMP
       );

-- ---------------------------------------------------------------------------
-- 2. 事前チェック（2.1 の NOT NULL / CHECK / 桁数で落ちる値を先に見つける）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n BIGINT;
BEGIN
    RAISE NOTICE '2.0 読み込み: 単語 % / 収録 % / 詳細 % / 問題 % / 選択肢 % / テスト % / 出題 % / 学習状況 % / 技能習得 % / 日次 %',
        (SELECT COUNT(*) FROM migr_jpn_word),
        (SELECT COUNT(*) FROM migr_jpn_collect),
        (SELECT COUNT(*) FROM migr_jpn_detail),
        (SELECT COUNT(*) FROM migr_jpn_question),
        (SELECT COUNT(*) FROM migr_jpn_choice),
        (SELECT COUNT(*) FROM migr_jpn_test),
        (SELECT COUNT(*) FROM migr_jpn_test_question),
        (SELECT COUNT(*) FROM migr_jpn_status),
        (SELECT COUNT(*) FROM migr_jpn_skill),
        (SELECT COUNT(*) FROM migr_jpn_daily);

    IF EXISTS (
        SELECT 1 FROM migr_jpn_word
         WHERE NULLIF(BTRIM("見出し語"), '') IS NULL
            OR NULLIF(BTRIM("読み"), '') IS NULL
            OR NULLIF(BTRIM("見出し語キー"), '') IS NULL
            OR NULLIF(BTRIM("読みキー"), '') IS NULL
    ) THEN
        RAISE EXCEPTION '2.0 の単語母表に空の見出し語・読み・キーがある';
    END IF;

    SELECT COUNT(*) INTO n FROM (
        SELECT "見出し語キー", "読みキー" FROM migr_jpn_word
         GROUP BY 1, 2 HAVING COUNT(*) > 1
    ) x;
    IF n > 0 THEN
        RAISE EXCEPTION '2.0 の単語母表で (見出し語キー, 読みキー) が % 件重複している', n;
    END IF;

    IF EXISTS (SELECT 1 FROM migr_jpn_word WHERE "状態" NOT IN ('ACTIVE', 'INACTIVE')) THEN
        RAISE EXCEPTION '2.0 の単語母表に未対応の 状態 がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_collect
         WHERE "単語SEQ" <= 0
            OR NULLIF(BTRIM("レベル"), '') IS NULL
            OR NULLIF(BTRIM("書籍"), '') IS NULL
            OR NULLIF(BTRIM("分類"), '') IS NULL
    ) THEN
        RAISE EXCEPTION '2.0 の収録情報に 単語SEQ<=0 か 空の レベル/書籍/分類 がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_detail
         WHERE "内容版数" <= 0
            OR NULLIF(BTRIM(COALESCE("JLPTレベル", '')), '') IS NOT NULL
               AND "JLPTレベル" NOT IN ('N5', 'N4', 'N3', 'N2', 'N1')
    ) THEN
        RAISE EXCEPTION '2.0 の詳細情報に 内容版数<=0 か 未対応の JLPTレベル がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_question
         WHERE "問題番号" <= 0 OR "内容版数" <= 0
            OR "難易度" NOT IN ('EASY', 'NORMAL', 'HARD')
            OR "状態" NOT IN ('GENERATED', 'ACTIVE', 'REJECTED', 'ARCHIVED')
            OR NULLIF(BTRIM("正解値"), '') IS NULL
    ) THEN
        RAISE EXCEPTION '2.0 の問題情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_choice
         WHERE "表示順" <= 0 OR NULLIF(BTRIM("選択肢値"), '') IS NULL
            OR NULLIF(BTRIM(COALESCE("誤答区分", '')), '') NOT IN (
                   'READING_SIMILAR', 'SOUND_SIMILAR', 'KANJI_SIMILAR', 'HOMOPHONE',
                   'MEANING_SIMILAR', 'CONTEXT_MISMATCH', 'OTHER'
               )
    ) THEN
        RAISE EXCEPTION '2.0 の選択肢情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_test
         WHERE "テスト種別" NOT IN ('A', 'B', 'C', 'D', 'E')
            OR "状態" NOT IN ('CREATED', 'RUNNING', 'COMPLETED')
            OR "難易度" NOT IN ('EASY', 'NORMAL', 'HARD')
            OR "出題方式" NOT IN ('ALL', 'RANDOM', 'REVIEW', 'WRONG_ONLY')
            OR "完了出題数" > "出題数"
            OR "正解課題数" < 0 OR "不正解課題数" < 0
            OR "有効学習時間ms" < 0
    ) THEN
        RAISE EXCEPTION '2.0 のテスト情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_test_question
         WHERE "出題順" <= 0
            OR "出題状態" NOT IN ('WAITING', 'ANSWERING', 'ANSWERED', 'SKIPPED')
            OR "誤答回数" > "回答回数"
            OR "有効学習時間ms" < 0
    ) THEN
        RAISE EXCEPTION '2.0 の出題情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_status
         WHERE "学習状態" NOT IN ('NOT_STARTED', 'LEARNING', 'REVIEW', 'MASTERED')
            OR "総合習得度" < 0 OR "総合習得度" > 100
            OR "正解回数" + "不正解回数" > "回答回数"
            OR "連続正解回数" > "最大連続正解回数"
            OR NULLIF(BTRIM(COALESCE("最終テスト種別", '')), '') NOT IN ('A', 'B', 'C', 'D', 'E')
               AND NULLIF(BTRIM(COALESCE("最終テスト種別", '')), '') IS NOT NULL
    ) THEN
        RAISE EXCEPTION '2.0 の学習状況情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_skill
         WHERE "テスト種別" NOT IN ('B', 'C', 'D', 'E')
            OR "学習状態" NOT IN ('NOT_STARTED', 'LEARNING', 'REVIEW', 'MASTERED')
            OR "習得度" < 0 OR "習得度" > 100
            OR "正解回数" + "不正解回数" > "回答回数"
            OR "連続正解回数" > "最大連続正解回数"
            OR NOT (
                   ("テスト種別" = 'B' AND "技能区分" IN ('B_ORTHOGRAPHY', 'B_READING_RECALL'))
                OR ("テスト種別" = 'C' AND "技能区分" IN ('C_READING_RECOGNITION', 'C_KANJI_RECOGNITION'))
                OR ("テスト種別" = 'D' AND "技能区分" = 'D_CONTEXT_MEANING')
                OR ("テスト種別" = 'E' AND "技能区分" = 'E_KANJI_USAGE')
            )
    ) THEN
        RAISE EXCEPTION '2.0 の技能習得情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    IF EXISTS (
        SELECT 1 FROM migr_jpn_daily
         WHERE "A学習時間ms" + "B学習時間ms" + "C学習時間ms"
             + "D学習時間ms" + "E学習時間ms" > "有効学習時間ms"
            OR "正解課題数" + "不正解課題数" > "完了課題数"
    ) THEN
        RAISE EXCEPTION '2.0 の日次情報に 2.1 の CHECK を満たさない行がある';
    END IF;

    -- 既に中身の違う行が入っている場合は中断（冪等な再実行だけを許容する）
    IF EXISTS (
        SELECT 1 FROM migr_jpn_word source
          JOIN public."JPN_単語情報" target ON target."旧単語ID" = source."日本語単語ID"
         WHERE target."見出し語" <> source."見出し語"
            OR target."読み" <> source."読み"
    ) THEN
        RAISE EXCEPTION '移行先に同じ 旧単語ID で内容の違う行が既にある';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. 単語母表
-- ---------------------------------------------------------------------------
INSERT INTO public."JPN_単語情報" (
    "旧単語ID", "見出し語", "読み", "見出し語キー", "読みキー",
    "JLPTレベル", "品詞", "状態コード",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."日本語単語ID",
       source."見出し語",
       source."読み",
       source."見出し語キー",
       source."読みキー",
       NULLIF(BTRIM(COALESCE(source."代表JLPTレベル", '')), ''),
       NULLIF(BTRIM(COALESCE(source."代表品詞", '')), ''),
       source."状態",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_word source
  ON CONFLICT ("旧単語ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 教材収録情報（親 旧単語ID -> 単語ID）
-- ---------------------------------------------------------------------------
INSERT INTO public."JPN_単語収録情報" (
    "旧収録ID", "単語ID", "レベル", "書籍", "分類", "単語SEQ",
    "掲載見出し語", "掲載読み", "掲載品詞", "掲載中国語意味", "出典JSON", "状態コード",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."収録ID",
       word."単語ID",
       source."レベル",
       source."書籍",
       source."分類",
       source."単語SEQ",
       NULLIF(BTRIM(COALESCE(source."掲載見出し語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."掲載読み", '')), ''),
       NULLIF(BTRIM(COALESCE(source."掲載品詞", '')), ''),
       NULLIF(BTRIM(COALESCE(source."掲載中国語意味", '')), ''),
       COALESCE(source."出典JSON", '{}'::jsonb),
       source."状態",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_collect source
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = source."日本語単語ID"
  ON CONFLICT ("旧収録ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. AI 詳細（本体の列 ＋ 6 子テーブルを 詳細JSON 1 列に集約）
--    senses / examples / pronunciations / collocations / relatedWords / cautions
--    各要素のキーは 2.0 の列名を camelCase にしたもの。配列は 表示順 の昇順。
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE migr_jpn_detail_json ON COMMIT DROP AS
SELECT detail."日本語単語詳細ID" AS old_detail_id,
       jsonb_build_object(
           'jlptLevel',               NULLIF(BTRIM(COALESCE(detail."JLPTレベル", '')), ''),
           'partOfSpeech',            NULLIF(BTRIM(COALESCE(detail."品詞", '')), ''),
           'conjugation',             NULLIF(BTRIM(COALESCE(detail."活用種類", '')), ''),
           'transitivity',            NULLIF(BTRIM(COALESCE(detail."自他区分", '')), ''),
           'importance',              detail."重要度",
           'chineseMeaning',          NULLIF(BTRIM(COALESCE(detail."代表中国語意味", '')), ''),
           'descriptionJa',           NULLIF(BTRIM(COALESCE(detail."日本語説明", '')), ''),
           'descriptionZh',           NULLIF(BTRIM(COALESCE(detail."中国語説明", '')), ''),
           'structuredSchemaVersion', NULLIF(BTRIM(COALESCE(detail."構造化スキーマ版", '')), ''),
           'manuallyCorrected',       COALESCE(detail."手動修正済フラグ", FALSE),
           'structured',              COALESCE(detail."構造化JSON", '{}'::jsonb),
           'senses', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          'number',       sense."語義番号",
                          'japanese',     NULLIF(BTRIM(COALESCE(sense."意味_日本語", '')), ''),
                          'chinese',      NULLIF(BTRIM(COALESCE(sense."意味_中国語", '')), ''),
                          'context',      NULLIF(BTRIM(COALESCE(sense."使用場面", '')), ''),
                          'style',        NULLIF(BTRIM(COALESCE(sense."文体区分", '')), ''),
                          'noteJapanese', NULLIF(BTRIM(COALESCE(sense."補足説明_日本語", '')), ''),
                          'noteChinese',  NULLIF(BTRIM(COALESCE(sense."補足説明_中国語", '')), '')
                      ) ORDER BY sense."表示順")
                 FROM migr_jpn_sense sense
                WHERE sense."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb),
           'examples', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          'japanese',        NULLIF(BTRIM(COALESCE(example."例文_日本語", '')), ''),
                          'reading',         NULLIF(BTRIM(COALESCE(example."例文読み", '')), ''),
                          'chinese',         NULLIF(BTRIM(COALESCE(example."例文_中国語", '')), ''),
                          'contextJapanese', NULLIF(BTRIM(COALESCE(example."文脈意味_日本語", '')), ''),
                          'contextChinese',  NULLIF(BTRIM(COALESCE(example."文脈意味_中国語", '')), ''),
                          'source',          NULLIF(BTRIM(COALESCE(example."出典", '')), ''),
                          -- 2.0 の 語義ID は 2.1 に無い ID なので 語義番号 に読み替える
                          'senseNumber',     sense_of_example."語義番号"
                      ) ORDER BY example."表示順")
                 FROM migr_jpn_example example
                 LEFT JOIN migr_jpn_sense sense_of_example
                        ON sense_of_example."語義ID" = example."語義ID"
                WHERE example."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb),
           'pronunciations', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          'reading',        NULLIF(BTRIM(COALESCE(pron."読み", '')), ''),
                          'accentNotation', NULLIF(BTRIM(COALESCE(pron."アクセント表記", '')), ''),
                          'accentType',     pron."アクセント型",
                          'moraCount',      pron."モーラ数",
                          'audioUrl',       NULLIF(BTRIM(COALESCE(pron."音声URL", '')), ''),
                          'audioProvider',  NULLIF(BTRIM(COALESCE(pron."音声プロバイダ", '')), '')
                      ) ORDER BY pron."表示順")
                 FROM migr_jpn_pron pron
                WHERE pron."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb),
           'collocations', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          'expression',      NULLIF(BTRIM(COALESCE(collo."表現", '')), ''),
                          'reading',         NULLIF(BTRIM(COALESCE(collo."読み", '')), ''),
                          'chinese',         NULLIF(BTRIM(COALESCE(collo."意味_中国語", '')), ''),
                          'exampleJapanese', NULLIF(BTRIM(COALESCE(collo."例文_日本語", '')), ''),
                          'exampleChinese',  NULLIF(BTRIM(COALESCE(collo."例文_中国語", '')), '')
                      ) ORDER BY collo."表示順")
                 FROM migr_jpn_collo collo
                WHERE collo."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb),
           'relatedWords', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          -- 2.0 の 関連先日本語単語ID は 2.1 の 単語ID に読み替える
                          'relatedWordId',     related_word."単語ID",
                          'relationType',      NULLIF(BTRIM(COALESCE(related."関係区分", '')), ''),
                          'heading',           NULLIF(BTRIM(COALESCE(related."表記", '')), ''),
                          'reading',           NULLIF(BTRIM(COALESCE(related."読み", '')), ''),
                          'chinese',           NULLIF(BTRIM(COALESCE(related."意味_中国語", '')), ''),
                          'differenceJapanese', NULLIF(BTRIM(COALESCE(related."相違点_日本語", '')), ''),
                          'differenceChinese', NULLIF(BTRIM(COALESCE(related."相違点_中国語", '')), ''),
                          'eCandidate',        related."E選択肢候補フラグ"
                      ) ORDER BY related."表示順")
                 FROM migr_jpn_related related
                 LEFT JOIN public."JPN_単語情報" related_word
                        ON related_word."旧単語ID" = related."関連先日本語単語ID"
                WHERE related."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb),
           'cautions', COALESCE((
               SELECT jsonb_agg(jsonb_build_object(
                          'noteType',       NULLIF(BTRIM(COALESCE(caution."注意区分", '')), ''),
                          'japanese',       NULLIF(BTRIM(COALESCE(caution."説明_日本語", '')), ''),
                          'chinese',        NULLIF(BTRIM(COALESCE(caution."説明_中国語", '')), ''),
                          'wrongExample',   NULLIF(BTRIM(COALESCE(caution."誤用例", '')), ''),
                          'correctExample', NULLIF(BTRIM(COALESCE(caution."正用例", '')), '')
                      ) ORDER BY caution."表示順")
                 FROM migr_jpn_caution caution
                WHERE caution."日本語単語詳細ID" = detail."日本語単語詳細ID"
           ), '[]'::jsonb)
       ) AS detail_json
  FROM migr_jpn_detail detail;

INSERT INTO public."JPN_単語詳細情報" (
    "旧詳細ID", "単語ID", "内容版数", "詳細JSON", "AIプロバイダ", "AIモデル", "取得日時",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT detail."日本語単語詳細ID",
       word."単語ID",
       detail."内容版数",
       built.detail_json,
       NULLIF(BTRIM(COALESCE(detail."AIプロバイダ", '')), ''),
       NULLIF(BTRIM(COALESCE(detail."AIモデル", '')), ''),
       COALESCE(detail."更新日時", detail."登録日時", CURRENT_TIMESTAMP),
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(detail."登録日時", CURRENT_TIMESTAMP),
       COALESCE(detail."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_detail detail
  JOIN migr_jpn_detail_json built ON built.old_detail_id = detail."日本語単語詳細ID"
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = detail."日本語単語ID"
  ON CONFLICT ("旧詳細ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. 問題と選択肢（親 旧問題ID -> 問題ID）
-- ---------------------------------------------------------------------------
INSERT INTO public."JPN_単語問題情報" (
    "旧問題ID", "単語ID", "問題種別", "問題番号",
    "問題文_日本語", "問題文_中国語", "対象表記", "対象読み",
    "例文_日本語", "例文読み", "音声テキスト",
    "正解値", "正解補足", "解説_日本語", "解説_中国語",
    "難易度", "状態コード", "内容版数", "構造化JSON",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."問題ID",
       word."単語ID",
       source."問題種別",
       source."問題番号",
       NULLIF(BTRIM(COALESCE(source."問題文_日本語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."問題文_中国語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."対象表記", '')), ''),
       NULLIF(BTRIM(COALESCE(source."対象読み", '')), ''),
       NULLIF(BTRIM(COALESCE(source."例文_日本語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."例文読み", '')), ''),
       NULLIF(BTRIM(COALESCE(source."音声テキスト", '')), ''),
       source."正解値",
       NULLIF(BTRIM(COALESCE(source."正解補足", '')), ''),
       NULLIF(BTRIM(COALESCE(source."解説_日本語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."解説_中国語", '')), ''),
       source."難易度",
       source."状態",
       source."内容版数",
       COALESCE(source."構造化JSON", '{}'::jsonb),
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_question source
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = source."日本語単語ID"
  ON CONFLICT ("旧問題ID") DO NOTHING;

INSERT INTO public."JPN_単語問題選択肢情報" (
    "旧選択肢ID", "問題ID", "表示順", "選択肢値", "選択肢読み", "正解フラグ",
    "誤答区分", "説明_日本語", "説明_中国語",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."選択肢ID",
       question."問題ID",
       source."表示順",
       source."選択肢値",
       NULLIF(BTRIM(COALESCE(source."選択肢読み", '')), ''),
       source."正解フラグ",
       -- 正解の行は 2.0 で空文字。2.1 は NULL に寄せる
       NULLIF(BTRIM(COALESCE(source."誤答区分", '')), ''),
       NULLIF(BTRIM(COALESCE(source."説明_日本語", '')), ''),
       NULLIF(BTRIM(COALESCE(source."説明_中国語", '')), ''),
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM migr_jpn_choice source
  JOIN public."JPN_単語問題情報" question ON question."旧問題ID" = source."問題ID"
  ON CONFLICT ("旧選択肢ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. テストと出題（利用者アカウントID が引けない行は移行せず SKIP 件数を出す）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    skipped BIGINT;
BEGIN
    SELECT COUNT(*) INTO skipped
      FROM migr_jpn_test source
      LEFT JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
     WHERE owner."アカウントID" IS NULL;
    IF skipped > 0 THEN
        RAISE NOTICE 'テスト: 利用者アカウントID が引けないため % 件を SKIP しました', skipped;
    ELSE
        RAISE NOTICE 'テスト: SKIP 0 件（全件 ユーザーID -> アカウントID を解決）';
    END IF;
END $$;

INSERT INTO public."JPN_テスト情報" (
    "旧テストID", "テスト番号", "利用者アカウントID", "テスト種別",
    "レベル", "書籍", "分類開始", "分類終了", "難易度", "出題方式", "検索条件JSON",
    "出題数", "完了出題数", "正解数", "不正解数", "状態コード",
    "開始日時", "終了日時", "最終学習日時", "有効学習時間ms",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."テストID",
       source."テスト番号",
       owner."アカウントID",
       source."テスト種別",
       NULLIF(BTRIM(COALESCE(source."レベル", '')), ''),
       NULLIF(BTRIM(COALESCE(source."書籍", '')), ''),
       NULLIF(BTRIM(COALESCE(source."分類開始", '')), ''),
       NULLIF(BTRIM(COALESCE(source."分類終了", '')), ''),
       source."難易度",
       source."出題方式",
       COALESCE(source."検索条件JSON", '{}'::jsonb),
       source."出題数",
       source."完了出題数",
       -- 2.0 の 正解課題数 / 不正解課題数（課題を廃止してこの列に集約）
       source."正解課題数",
       source."不正解課題数",
       source."状態",
       source."開始日時",
       source."終了日時",
       source."最終学習日時",
       source."有効学習時間ms",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_test source
  JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
  ON CONFLICT ("旧テストID") DO NOTHING;

DO $$
DECLARE
    skipped BIGINT;
BEGIN
    SELECT COUNT(*) INTO skipped
      FROM migr_jpn_test_question source
      JOIN migr_jpn_test test ON test."テストID" = source."テストID"
      LEFT JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = test."ユーザーID"
     WHERE owner."アカウントID" IS NULL;
    IF skipped > 0 THEN
        RAISE NOTICE '出題: 親テストの利用者アカウントID が引けないため % 件を SKIP しました', skipped;
    ELSE
        RAISE NOTICE '出題: SKIP 0 件';
    END IF;
END $$;

INSERT INTO public."JPN_テスト出題情報" (
    "旧出題ID", "テストID", "単語ID", "収録ID", "出題順", "問題ID", "出題状態",
    "最終判定", "回答回数", "誤答回数", "有効学習時間ms", "回答完了日時",
    "単語スナップショットJSON",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT source."出題ID",
       test."テストID",
       word."単語ID",
       collect."収録ID",
       source."出題順",
       NULL,  -- 2.0 は課題側が問題を参照していたため、移行データは問題なし
       CASE source."出題状態" WHEN 'WAITING' THEN 'PENDING' ELSE source."出題状態" END,
       NULLIF(BTRIM(COALESCE(source."最終判定", '')), ''),
       source."回答回数",
       source."誤答回数",
       source."有効学習時間ms",
       source."回答完了日時",
       COALESCE(source."単語スナップショットJSON", '{}'::jsonb),
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_test_question source
  JOIN public."JPN_テスト情報" test ON test."旧テストID" = source."テストID"
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = source."日本語単語ID"
  LEFT JOIN public."JPN_単語収録情報" collect ON collect."旧収録ID" = source."収録ID"
  ON CONFLICT ("旧出題ID") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. 学習状況・技能習得・日次
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    skipped BIGINT;
BEGIN
    SELECT COUNT(*) INTO skipped
      FROM migr_jpn_status source
      LEFT JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
     WHERE owner."アカウントID" IS NULL;
    IF skipped > 0 THEN
        RAISE NOTICE '学習状況: 利用者アカウントID が引けないため % 件を SKIP しました', skipped;
    ELSE
        RAISE NOTICE '学習状況: SKIP 0 件（全件 ユーザーID -> アカウントID を解決）';
    END IF;
END $$;

INSERT INTO public."JPN_学習状況情報" (
    "利用者アカウントID", "単語ID", "学習状態", "総合習得度", "習得済フラグ", "習得日時",
    "お気に入りフラグ", "お気に入り日時", "A確認回数",
    "回答回数", "正解回数", "不正解回数", "連続正解回数", "最大連続正解回数",
    "有効学習時間ms", "最終テスト種別", "最終課題コード", "最終判定",
    "初回学習日時", "最終学習日時", "次回復習日時", "復習間隔日数",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT owner."アカウントID",
       word."単語ID",
       source."学習状態",
       source."総合習得度",
       source."習得済フラグ",
       source."習得日時",
       source."お気に入りフラグ",
       source."お気に入り日時",
       source."A確認回数",
       source."回答回数",
       source."正解回数",
       source."不正解回数",
       source."連続正解回数",
       source."最大連続正解回数",
       source."有効学習時間ms",
       NULLIF(BTRIM(COALESCE(source."最終テスト種別", '')), ''),
       NULLIF(BTRIM(COALESCE(source."最終課題コード", '')), ''),
       NULLIF(BTRIM(COALESCE(source."最終判定", '')), ''),
       source."初回学習日時",
       source."最終学習日時",
       source."次回復習日時",
       source."復習間隔日数",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_status source
  JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = source."日本語単語ID"
  ON CONFLICT ("利用者アカウントID", "単語ID") DO NOTHING;

DO $$
DECLARE
    skipped BIGINT;
BEGIN
    SELECT COUNT(*) INTO skipped
      FROM migr_jpn_skill source
      LEFT JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
     WHERE owner."アカウントID" IS NULL;
    IF skipped > 0 THEN
        RAISE NOTICE '技能習得: 利用者アカウントID が引けないため % 件を SKIP しました', skipped;
    ELSE
        RAISE NOTICE '技能習得: SKIP 0 件（全件 ユーザーID -> アカウントID を解決）';
    END IF;
END $$;

INSERT INTO public."JPN_技能習得情報" (
    "利用者アカウントID", "単語ID", "テスト種別", "技能区分", "学習状態", "習得度",
    "回答回数", "正解回数", "不正解回数", "連続正解回数", "最大連続正解回数",
    "最終判定", "最終学習日時", "次回復習日時",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT owner."アカウントID",
       word."単語ID",
       source."テスト種別",
       source."技能区分",
       source."学習状態",
       source."習得度",
       source."回答回数",
       source."正解回数",
       source."不正解回数",
       source."連続正解回数",
       source."最大連続正解回数",
       NULLIF(BTRIM(COALESCE(source."最終判定", '')), ''),
       source."最終学習日時",
       source."次回復習日時",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_skill source
  JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
  JOIN public."JPN_単語情報" word ON word."旧単語ID" = source."日本語単語ID"
  ON CONFLICT ("利用者アカウントID", "単語ID", "テスト種別", "技能区分") DO NOTHING;

DO $$
DECLARE
    skipped BIGINT;
BEGIN
    SELECT COUNT(*) INTO skipped
      FROM migr_jpn_daily source
      LEFT JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
     WHERE owner."アカウントID" IS NULL;
    IF skipped > 0 THEN
        RAISE NOTICE '日次: 利用者アカウントID が引けないため % 件を SKIP しました', skipped;
    ELSE
        RAISE NOTICE '日次: SKIP 0 件（全件 ユーザーID -> アカウントID を解決）';
    END IF;
END $$;

INSERT INTO public."JPN_学習日次情報" (
    "利用者アカウントID", "学習日", "有効学習時間ms",
    "A学習時間ms", "B学習時間ms", "C学習時間ms", "D学習時間ms", "E学習時間ms",
    "学習単語数", "完了テスト数", "完了課題数", "正解課題数", "不正解課題数",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT owner."アカウントID",
       source."学習日",
       source."有効学習時間ms",
       source."A学習時間ms",
       source."B学習時間ms",
       source."C学習時間ms",
       source."D学習時間ms",
       source."E学習時間ms",
       source."学習単語数",
       source."完了テスト数",
       source."完了課題数",
       source."正解課題数",
       source."不正解課題数",
       NULL, NULL, 'MIGRATION', 'MIGRATION',
       COALESCE(source."登録日時", CURRENT_TIMESTAMP),
       COALESCE(source."更新日時", CURRENT_TIMESTAMP)
  FROM migr_jpn_daily source
  JOIN migr_jpn_owner owner ON owner."旧ユーザーID" = source."ユーザーID"
  ON CONFLICT ("利用者アカウントID", "学習日") DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. 件数検証（2.0 の件数と一致しなければ中断）
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    expected CONSTANT TEXT[] := ARRAY[
        '単語:9847', '収録:9886', '詳細:383', '問題:1700', '選択肢:6800',
        'テスト:15', '出題:1105', '学習状況:213', '技能習得:906', '日次:13'];
    item TEXT;
    label TEXT;
    want BIGINT;
    got BIGINT;
BEGIN
    FOREACH item IN ARRAY expected LOOP
        label := SPLIT_PART(item, ':', 1);
        want  := SPLIT_PART(item, ':', 2)::BIGINT;
        got := CASE label
                   WHEN '単語'     THEN (SELECT COUNT(*) FROM public."JPN_単語情報")
                   WHEN '収録'     THEN (SELECT COUNT(*) FROM public."JPN_単語収録情報")
                   WHEN '詳細'     THEN (SELECT COUNT(*) FROM public."JPN_単語詳細情報")
                   WHEN '問題'     THEN (SELECT COUNT(*) FROM public."JPN_単語問題情報")
                   WHEN '選択肢'   THEN (SELECT COUNT(*) FROM public."JPN_単語問題選択肢情報")
                   WHEN 'テスト'   THEN (SELECT COUNT(*) FROM public."JPN_テスト情報")
                   WHEN '出題'     THEN (SELECT COUNT(*) FROM public."JPN_テスト出題情報")
                   WHEN '学習状況' THEN (SELECT COUNT(*) FROM public."JPN_学習状況情報")
                   WHEN '技能習得' THEN (SELECT COUNT(*) FROM public."JPN_技能習得情報")
                   WHEN '日次'     THEN (SELECT COUNT(*) FROM public."JPN_学習日次情報")
               END;
        IF got <> want THEN
            RAISE EXCEPTION '件数が一致しません: % は % 件（期待 %）', label, got, want;
        END IF;
        RAISE NOTICE 'OK % = % 件', label, got;
    END LOOP;

    -- 詳細JSON の 6 配列の総数が 2.0 の子テーブル件数と一致すること
    IF (SELECT SUM(jsonb_array_length("詳細JSON" -> 'senses')) FROM public."JPN_単語詳細情報") <> 653
       OR (SELECT SUM(jsonb_array_length("詳細JSON" -> 'examples')) FROM public."JPN_単語詳細情報") <> 1011
       OR (SELECT SUM(jsonb_array_length("詳細JSON" -> 'pronunciations')) FROM public."JPN_単語詳細情報") <> 384
       OR (SELECT SUM(jsonb_array_length("詳細JSON" -> 'collocations')) FROM public."JPN_単語詳細情報") <> 926
       OR (SELECT SUM(jsonb_array_length("詳細JSON" -> 'relatedWords')) FROM public."JPN_単語詳細情報") <> 676
       OR (SELECT SUM(jsonb_array_length("詳細JSON" -> 'cautions')) FROM public."JPN_単語詳細情報") <> 485
    THEN
        RAISE EXCEPTION '詳細JSON の配列件数が 2.0 の子テーブルと一致しません';
    END IF;
    RAISE NOTICE 'OK 詳細JSON の配列 = 語義 653 / 例文 1011 / 発音 384 / コロケーション 926 / 関連語 676 / 使用注意 485';
END $$;

COMMIT;

-- ============================================================================
-- 移行結果の確認（読み取りのみ）
-- ============================================================================
\echo '--- 移行結果: テーブル別件数 ---'
SELECT 'JPN_単語情報' AS "テーブル", COUNT(*) AS "件数" FROM public."JPN_単語情報"
UNION ALL SELECT 'JPN_単語収録情報',     COUNT(*) FROM public."JPN_単語収録情報"
UNION ALL SELECT 'JPN_単語詳細情報',     COUNT(*) FROM public."JPN_単語詳細情報"
UNION ALL SELECT 'JPN_単語問題情報',     COUNT(*) FROM public."JPN_単語問題情報"
UNION ALL SELECT 'JPN_単語問題選択肢情報', COUNT(*) FROM public."JPN_単語問題選択肢情報"
UNION ALL SELECT 'JPN_テスト情報',       COUNT(*) FROM public."JPN_テスト情報"
UNION ALL SELECT 'JPN_テスト出題情報',   COUNT(*) FROM public."JPN_テスト出題情報"
UNION ALL SELECT 'JPN_学習状況情報',     COUNT(*) FROM public."JPN_学習状況情報"
UNION ALL SELECT 'JPN_技能習得情報',     COUNT(*) FROM public."JPN_技能習得情報"
UNION ALL SELECT 'JPN_学習日次情報',     COUNT(*) FROM public."JPN_学習日次情報";

\echo '--- 移行結果: 詳細JSON の 1 件（トップレベルのキーと配列の長さ） ---'
SELECT "旧詳細ID" AS "旧詳細ID",
       jsonb_object_keys("詳細JSON") AS "キー"
  FROM public."JPN_単語詳細情報"
 WHERE "旧詳細ID" = 4
 ORDER BY 2;

SELECT "旧詳細ID" AS "旧詳細ID",
       "詳細JSON" -> 'jlptLevel'   AS "jlptLevel",
       "詳細JSON" -> 'partOfSpeech' AS "partOfSpeech",
       "詳細JSON" -> 'chineseMeaning' AS "chineseMeaning",
       jsonb_array_length("詳細JSON" -> 'senses')         AS "senses",
       jsonb_array_length("詳細JSON" -> 'examples')       AS "examples",
       jsonb_array_length("詳細JSON" -> 'pronunciations') AS "pronunciations",
       jsonb_array_length("詳細JSON" -> 'collocations')   AS "collocations",
       jsonb_array_length("詳細JSON" -> 'relatedWords')   AS "relatedWords",
       jsonb_array_length("詳細JSON" -> 'cautions')       AS "cautions"
  FROM public."JPN_単語詳細情報"
 WHERE "旧詳細ID" = 4;

\echo '--- 移行結果: 詳細JSON senses の先頭1件 ---'
SELECT jsonb_pretty("詳細JSON" -> 'senses' -> 0) FROM public."JPN_単語詳細情報" WHERE "旧詳細ID" = 4;

\echo '--- 移行結果: テストの内訳（種別 × 状態） ---'
SELECT "テスト種別" AS "種別", "状態コード" AS "状態", COUNT(*) AS "件数",
       SUM("出題数") AS "出題数", SUM("完了出題数") AS "完了出題数",
       SUM("正解数") AS "正解数", SUM("不正解数") AS "不正解数"
  FROM public."JPN_テスト情報"
 GROUP BY 1, 2 ORDER BY 1, 2;

\echo '--- 移行結果: 持ち主と監査 ---'
SELECT "利用者アカウントID" AS "アカウントID", COUNT(*) AS "テスト件数",
       MIN("登録元コード") AS "登録元コード"
  FROM public."JPN_テスト情報" GROUP BY 1 ORDER BY 1;
