-- ============================================================================
-- Study 2.1  TODO のデータ移行
--   2.0 study2.COM_TODO管理情報 -> study21.COM_TODO情報（169 件・子タスク 33 件）
-- ----------------------------------------------------------------------------
-- 冪等: 旧 TODOID を維持して ON CONFLICT DO NOTHING（親子の自己参照は 1 文で入れる）。
-- 持ち主の対応: 'ljz'（劉競澤）-> アカウント 2 / 'liu'（劉季＝保護者）-> アカウント 1
--   日報と同じ判断（ljz = Liu Jingze の頭文字）。相違があれば下の対応表を直す。
-- 値の対応: 状態 O->TODO / C->DOING / X->DONE / D->TODO（1 件。2.0 で意味が不明瞭なため）
--           優先度 H->HIGH / M->NORMAL / L->LOW
-- 補正: 状態 DONE は実施完了日時が必要（2.1 の CHECK）なので、無ければ 更新日時 で埋める。
-- ============================================================================
BEGIN;

CREATE TEMP TABLE migr_todo_owner ("旧ユーザーID" TEXT PRIMARY KEY, "アカウントID" BIGINT) ON COMMIT DROP;
INSERT INTO migr_todo_owner VALUES ('ljz', 2), ('liu', 1);

CREATE TEMP TABLE migr_todo ON COMMIT DROP AS
SELECT * FROM dblink('dbname=study2',
    $q$ SELECT "TODOID","ユーザーID","クライアントTODOID","タイトル","メモ","状態","優先度","期限日",
                "実施開始日時","実施完了日時","作成者","作成日時","更新者","更新日時","親TODOID","表示順"
          FROM public."COM_TODO管理情報" $q$)
    AS t("TODOID" BIGINT, "ユーザーID" VARCHAR(50), "クライアントTODOID" VARCHAR(64), "タイトル" TEXT,
         "メモ" TEXT, "状態" VARCHAR(2), "優先度" VARCHAR(2), "期限日" DATE,
         "実施開始日時" TIMESTAMP, "実施完了日時" TIMESTAMP, "作成者" VARCHAR(50),
         "作成日時" TIMESTAMP, "更新者" VARCHAR(50), "更新日時" TIMESTAMP,
         "親TODOID" BIGINT, "表示順" INTEGER);

DO $$
DECLARE n BIGINT;
BEGIN
    SELECT COUNT(*) INTO n FROM migr_todo;
    RAISE NOTICE '2.0 の TODO % 件を移行します（子タスク % 件）',
        n, (SELECT COUNT(*) FROM migr_todo WHERE "親TODOID" IS NOT NULL);
    IF EXISTS (SELECT 1 FROM migr_todo WHERE "状態" NOT IN ('O','C','X','D')) THEN
        RAISE EXCEPTION '2.0 TODO contains an unsupported status';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_todo WHERE "優先度" NOT IN ('H','M','L')) THEN
        RAISE EXCEPTION '2.0 TODO contains an unsupported priority';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_todo WHERE BTRIM(COALESCE("タイトル",'')) = '') THEN
        RAISE EXCEPTION '2.0 TODO contains a blank title';
    END IF;
    -- 親が移行対象に無い子タスクは親を NULL にする（外部キー違反を避ける）
    IF EXISTS (SELECT 1 FROM migr_todo c WHERE c."親TODOID" IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM migr_todo p WHERE p."TODOID" = c."親TODOID")) THEN
        RAISE NOTICE '親が存在しない子タスクがあります（親TODOID を NULL にします）';
    END IF;
    IF EXISTS (SELECT 1 FROM migr_todo t LEFT JOIN migr_todo_owner o ON o."旧ユーザーID" = t."ユーザーID"
                WHERE o."アカウントID" IS NULL) THEN
        RAISE NOTICE '対応表に無いユーザーID の TODO は移行しません';
    END IF;
END $$;

INSERT INTO public."COM_TODO情報" (
    "TODOID", "アカウントID", "作成者アカウントID", "親TODOID", "表示順", "タイトル", "メモ",
    "状態コード", "優先度コード", "期限日", "実施開始日時", "実施完了日時",
    "クライアントTODOID", "旧ユーザーID", "旧作成者名",
    "登録元コード", "登録日時", "更新元コード", "更新日時")
SELECT t."TODOID",
       o."アカウントID",
       o."アカウントID",                     -- 2.0 の 作成者 は表示名だけなので、持ち主を実行者とみなす
       CASE WHEN p."TODOID" IS NULL THEN NULL ELSE t."親TODOID" END,
       COALESCE(t."表示順", 0),
       t."タイトル", t."メモ",
       CASE t."状態" WHEN 'C' THEN 'DOING' WHEN 'X' THEN 'DONE' ELSE 'TODO' END,
       CASE t."優先度" WHEN 'H' THEN 'HIGH' WHEN 'L' THEN 'LOW' ELSE 'NORMAL' END,
       t."期限日", t."実施開始日時",
       CASE WHEN t."状態" = 'X' THEN COALESCE(t."実施完了日時", t."更新日時", t."作成日時") ELSE t."実施完了日時" END,
       t."クライアントTODOID", t."ユーザーID", t."作成者",
       'MIGRATION', COALESCE(t."作成日時", CURRENT_TIMESTAMP), 'MIGRATION', COALESCE(t."更新日時", CURRENT_TIMESTAMP)
  FROM migr_todo t
  JOIN migr_todo_owner o ON o."旧ユーザーID" = t."ユーザーID"
  LEFT JOIN migr_todo p ON p."TODOID" = t."親TODOID"
 ON CONFLICT ("TODOID") DO NOTHING;

SELECT setval(pg_get_serial_sequence('public."COM_TODO情報"', 'TODOID'),
              GREATEST((SELECT COALESCE(MAX("TODOID"), 0) FROM public."COM_TODO情報"), 1), true);

DO $$
DECLARE src BIGINT; dst BIGINT;
BEGIN
    SELECT COUNT(*) INTO src FROM migr_todo t JOIN migr_todo_owner o ON o."旧ユーザーID" = t."ユーザーID";
    SELECT COUNT(*) INTO dst FROM public."COM_TODO情報" t JOIN migr_todo m ON m."TODOID" = t."TODOID";
    IF src <> dst THEN RAISE EXCEPTION 'TODO count mismatch: source %, migrated %', src, dst; END IF;
    RAISE NOTICE 'TODO の移行完了: % 件（子タスク % 件）', dst,
        (SELECT COUNT(*) FROM public."COM_TODO情報" WHERE "親TODOID" IS NOT NULL);
END $$;

SELECT "状態コード", "優先度コード", COUNT(*) FROM public."COM_TODO情報" GROUP BY 1, 2 ORDER BY 1, 2;
COMMIT;
