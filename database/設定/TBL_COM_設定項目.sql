-- ============================================================================
-- Study 2.1  設定カタログ  DDL
-- テーブル: COM_設定項目
-- ----------------------------------------------------------------------------
-- 設定の「項目定義」を保持するカタログ。設定キーと型・必須・有効値を一元管理する。
-- 値そのものは COM_設定情報（別テーブル）に保持する。
-- 対象DB: study21 (PostgreSQL)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public."COM_設定項目" (
    "ページ区分"    VARCHAR(64)  NOT NULL,
    "設定キー"      VARCHAR(128) NOT NULL,
    "値タイプ"      VARCHAR(20)  NOT NULL,
    "必須フラグ"    CHAR(1)      NOT NULL DEFAULT '1',
    "有効値"        VARCHAR(1000) NULL,
    "説明"          VARCHAR(500) NULL,
    "登録ID"        VARCHAR(64)  NULL,
    "更新ID"        VARCHAR(64)  NULL,
    "登録日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "更新日時"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "PK_COM_設定項目" PRIMARY KEY ("ページ区分", "設定キー"),
    CONSTRAINT "CK_COM_設定項目_値タイプ" CHECK (
        "値タイプ" IN ('STRING','INTEGER','DECIMAL','BOOLEAN','TEXT','TIME','ENUM')
    ),
    CONSTRAINT "CK_COM_設定項目_必須" CHECK ("必須フラグ" IN ('0','1'))
);
