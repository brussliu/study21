-- ============================================================================
-- Study 2.1  アカウント 初期データ
-- テーブル: ACC_アカウント
-- ----------------------------------------------------------------------------
-- 2家族分（保護者1名 + 生徒1名 のペア × 2）を登録する。
--   アカウントID=1  保護者  bruss.ji.liu@gmail.com
--   アカウントID=2  生徒    ricky.jingze@gmail.com      （保護者ID=1）
--   アカウントID=3  保護者  testparent@gmail.com
--   アカウントID=4  生徒    teststudent@gmail.com       （保護者ID=3）
--
-- ※ アカウントID を明示指定する理由:
--    既存データ（DOC_フォルダ情報 / DOC_資料情報 / COM_臨時ファイル情報）が
--    家族学生ID = 2（生徒1）を参照しているため、生徒1 を アカウントID=2 に
--    固定して参照整合性を維持する。ID を変えると既存資料・臨時ファイルが
--    参照できなくなる点に注意。
-- ※ 初期パスワード: 全アカウント共通で「12345678」（BCrypt ハッシュを格納）。
--    運用開始後は速やかに変更すること。
-- ※ 有効期限は 2027-12-31（運用に合わせて変更可）。
-- ※ 冪等: アカウントID をキーに ON CONFLICT DO NOTHING（再実行しても重複しない）。
-- 実行順: TBL_ACC_アカウント.sql → TBL_ACC_権限.sql → TBL_ACC_権限_init.sql の後。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

BEGIN;

INSERT INTO public."ACC_アカウント"
    ("アカウントID", "ログインID", "パスワードハッシュ", "アカウント種別", "状態",
     "姓", "名", "姓かな", "名かな", "学年", "保護者ID", "有効期限", "利用規約同意日時",
     "登録ID", "更新ID")
VALUES
    (1, 'bruss.ji.liu@gmail.com',
        '$2a$10$kr5DAo6z6c8L7k/MA5r8n.G3FJbk2YN6e/KazgVtk.GSY9f.fkW5u', 'GUARDIAN', '1',
        'Liu', 'Bruss', 'Liu', 'Bruss', NULL, NULL,
        DATE '2027-12-31', CURRENT_TIMESTAMP, 'init', 'init'),
    (2, 'ricky.jingze@gmail.com',
        '$2a$10$kr5DAo6z6c8L7k/MA5r8n.G3FJbk2YN6e/KazgVtk.GSY9f.fkW5u', 'STUDENT', '1',
        'Jingze', 'Ricky', 'Jingze', 'Ricky', '未設定', 1,
        DATE '2027-12-31', CURRENT_TIMESTAMP, 'init', 'init'),
    (3, 'testparent@gmail.com',
        '$2a$10$kr5DAo6z6c8L7k/MA5r8n.G3FJbk2YN6e/KazgVtk.GSY9f.fkW5u', 'GUARDIAN', '1',
        '試験', '保護者', 'しけん', 'ほごしゃ', NULL, NULL,
        DATE '2027-12-31', CURRENT_TIMESTAMP, 'init', 'init'),
    (4, 'teststudent@gmail.com',
        '$2a$10$kr5DAo6z6c8L7k/MA5r8n.G3FJbk2YN6e/KazgVtk.GSY9f.fkW5u', 'STUDENT', '1',
        '試験', '生徒', 'しけん', 'せいと', '中学1年生', 3,
        DATE '2027-12-31', CURRENT_TIMESTAMP, 'init', 'init')
ON CONFLICT ("アカウントID") DO NOTHING;

-- 明示IDで登録したため、BIGSERIAL のシーケンスを最大IDに合わせる
SELECT setval(pg_get_serial_sequence('public."ACC_アカウント"', 'アカウントID'),
              (SELECT COALESCE(MAX("アカウントID"), 1) FROM public."ACC_アカウント"));

-- 確認用
SELECT "アカウントID", "ログインID", "アカウント種別", "保護者ID", "学年"
  FROM public."ACC_アカウント" ORDER BY "アカウントID";

COMMIT;
