-- ============================================================================
-- Study 2.1  プロキシ通信履歴のデータ移行
--   2.0 study2.NET_プロキシ通信履歴情報 -> study21.NET_プロキシ通信履歴情報
-- ----------------------------------------------------------------------------
-- 事前条件:
--   1. database/プロキシ/TBL_NET_プロキシ通信履歴情報.sql
--   2. 移行元: study2 の NET_プロキシ通信履歴情報（別 DB。dblink で読む）
--
-- 実行例:
--   psql -U postgres -d study21 -v ON_ERROR_STOP=1 -f MIG_NET_プロキシ通信履歴_20260912.sql
--
-- 冪等性:
--   通信履歴ID を維持して ON CONFLICT DO NOTHING で投入するため、何度実行しても
--   結果は変わらない（既存行は書き換えない）。
--
-- スナップショット:
--   2.0 のプロキシは稼働中で、同じテーブルに書き込み続けている。さらに
--   batR02（バッチ実行履歴・上網履歴クリーンアップ）が古い行を削除するため、
--   実行中も件数が変わる（実測: 2026-09-12 時点で 164,394 -> 138,840 行）。
--   そのため「開始時点の最大 通信履歴ID」を境界として一時表へ取り込み、
--   その一時表を今回の移行対象（スナップショット）として扱う。件数の検証も
--   移行元を読み直さず、この一時表と突き合わせる（再読込すると削除の影響を受ける）。
--
-- 値の対応:
--   2.0 の列をそのまま移す。監査列は 2.1 の規約に合わせて
--   登録元コード='MIGRATION'、登録者/更新者アカウントID は NULL にする
--   （2.0 は 登録ID/更新ID に固定値 'proxy' を入れていた）。
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 移行対象のスナップショットを一時表へ取り込む
-- ----------------------------------------------------------------------------
CREATE TEMP TABLE migr_proxy_log ON COMMIT DROP AS
SELECT *
  FROM dblink('dbname=study2',
              $q$
              SELECT "通信履歴ID", "受付日時", "要求日時", "クライアントIP", "クライアントポート",
                     "HTTPメソッド", "接続先ホスト", "接続先ポート", "要求URL", "要求パス",
                     "クエリ文字列", "プロトコル種別", "ユーザーエージェント", "参照元URL",
                     "応答状態コード", "エラー内容", "登録日時", "更新日時"
                FROM public."NET_プロキシ通信履歴情報"
               WHERE "通信履歴ID" <= (SELECT MAX("通信履歴ID") FROM public."NET_プロキシ通信履歴情報")
              $q$)
       AS t("通信履歴ID" BIGINT, "受付日時" TIMESTAMP, "要求日時" TIMESTAMP,
            "クライアントIP" VARCHAR(64), "クライアントポート" INTEGER,
            "HTTPメソッド" VARCHAR(16), "接続先ホスト" VARCHAR(255), "接続先ポート" INTEGER,
            "要求URL" TEXT, "要求パス" TEXT, "クエリ文字列" TEXT, "プロトコル種別" VARCHAR(20),
            "ユーザーエージェント" TEXT, "参照元URL" TEXT,
            "応答状態コード" INTEGER, "エラー内容" TEXT,
            "登録日時" TIMESTAMP, "更新日時" TIMESTAMP);

DO $$
DECLARE
    log_count BIGINT;
    min_id BIGINT;
    max_id BIGINT;
BEGIN
    SELECT COUNT(*), MIN("通信履歴ID"), MAX("通信履歴ID")
      INTO log_count, min_id, max_id
      FROM migr_proxy_log;
    RAISE NOTICE '2.0 のプロキシ通信履歴 % 件（通信履歴ID % 〜 %）を移行します', log_count, min_id, max_id;

    -- 2.1 の CHECK 制約に反する行があれば止める
    IF EXISTS (SELECT 1 FROM migr_proxy_log WHERE COALESCE(BTRIM("クライアントIP"), '') = '') THEN
        RAISE EXCEPTION '2.0 proxy log contains a blank client IP';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_proxy_log
                WHERE "応答状態コード" IS NOT NULL AND "応答状態コード" NOT BETWEEN 100 AND 599) THEN
        RAISE EXCEPTION '2.0 proxy log contains an out-of-range response status code';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_proxy_log
                WHERE ("クライアントポート" IS NOT NULL AND "クライアントポート" NOT BETWEEN 0 AND 65535)
                   OR ("接続先ポート" IS NOT NULL AND "接続先ポート" NOT BETWEEN 0 AND 65535)) THEN
        RAISE EXCEPTION '2.0 proxy log contains an out-of-range port';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_proxy_log
                WHERE "応答状態コード" = 403 AND COALESCE(BTRIM("エラー内容"), '') = '') THEN
        RAISE EXCEPTION '2.0 proxy log contains a denial without a reason';
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 2. 移行する（通信履歴ID は 2.0 の値を維持する）
-- ----------------------------------------------------------------------------
INSERT INTO public."NET_プロキシ通信履歴情報" (
    "通信履歴ID", "受付日時", "要求日時", "クライアントIP", "クライアントポート",
    "HTTPメソッド", "接続先ホスト", "接続先ポート", "要求URL", "要求パス", "クエリ文字列",
    "プロトコル種別", "ユーザーエージェント", "参照元URL", "応答状態コード", "エラー内容",
    "登録者アカウントID", "更新者アカウントID", "登録元コード", "更新元コード",
    "登録日時", "更新日時"
)
SELECT s."通信履歴ID", s."受付日時", s."要求日時", s."クライアントIP", s."クライアントポート",
       s."HTTPメソッド", s."接続先ホスト", s."接続先ポート", s."要求URL", s."要求パス", s."クエリ文字列",
       s."プロトコル種別", s."ユーザーエージェント", s."参照元URL", s."応答状態コード", s."エラー内容",
       NULL, NULL, 'MIGRATION', NULL,
       s."登録日時", s."更新日時"
  FROM migr_proxy_log s
 ON CONFLICT ("通信履歴ID") DO NOTHING;

-- 通信履歴ID は BIGSERIAL。移行した最大値の次から採番させる
SELECT setval(
    pg_get_serial_sequence('public."NET_プロキシ通信履歴情報"', '通信履歴ID'),
    GREATEST((SELECT COALESCE(MAX("通信履歴ID"), 0) FROM public."NET_プロキシ通信履歴情報"), 1),
    true
);

-- ----------------------------------------------------------------------------
-- 3. 件数の検証（スナップショットと突き合わせる。移行元は読み直さない）
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    snapshot_count BIGINT;
    migrated_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO snapshot_count FROM migr_proxy_log;
    SELECT COUNT(*) INTO migrated_count
      FROM public."NET_プロキシ通信履歴情報" t
      JOIN migr_proxy_log s ON s."通信履歴ID" = t."通信履歴ID";

    IF snapshot_count <> migrated_count THEN
        RAISE EXCEPTION 'Proxy log count mismatch: snapshot %, migrated %', snapshot_count, migrated_count;
    END IF;
    RAISE NOTICE 'プロキシ通信履歴 % 件を移行しました', migrated_count;
END $$;

-- 内容の抜き取り確認（先頭・末尾の 1 件が一致すること）
DO $$
DECLARE
    mismatch INTEGER;
BEGIN
    SELECT COUNT(*) INTO mismatch
      FROM migr_proxy_log s
      JOIN public."NET_プロキシ通信履歴情報" t ON t."通信履歴ID" = s."通信履歴ID"
     WHERE t."受付日時" IS DISTINCT FROM s."受付日時"
        OR t."クライアントIP" IS DISTINCT FROM s."クライアントIP"
        OR t."応答状態コード" IS DISTINCT FROM s."応答状態コード"
        OR t."エラー内容" IS DISTINCT FROM s."エラー内容";

    IF mismatch > 0 THEN
        RAISE EXCEPTION 'Proxy log content mismatch on % rows', mismatch;
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 4. 結果の確認
-- ----------------------------------------------------------------------------
SELECT COUNT(*) AS "件数",
       MIN("受付日時") AS "最古",
       MAX("受付日時") AS "最新",
       COUNT(*) FILTER (WHERE "応答状態コード" IS NOT NULL) AS "拒否"
  FROM public."NET_プロキシ通信履歴情報";

COMMIT;
