-- 先に TBL_ACC_権限.sql を実行する。初期権限案: 人員・権限管理の操作のみ。
-- 再実行しても既存の名称や設定を上書きしない。ただし削除した初期権限は再作成されるため、
-- 運用開始後の定期実行には使用しない。管理者アカウント・既定パスワードは作成しない。
BEGIN;

INSERT INTO public."ACC_ロール" ("ロールコード", "ロール名", "説明", "登録ID", "更新ID") VALUES
    ('STUDENT', '学生', '本人の情報を利用する学生アカウント', 'SYSTEM', 'SYSTEM'),
    ('GUARDIAN', '保護者', '本人と紐づく学生1名の情報を利用する保護者アカウント', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', '管理者', '人員とロール権限を管理する運用アカウント', 'SYSTEM', 'SYSTEM')
ON CONFLICT ("ロールコード") DO NOTHING;

INSERT INTO public."ACC_権限" ("権限コード", "権限名", "説明", "登録ID", "更新ID") VALUES
    ('account.read', 'アカウント参照', '指定範囲の基本情報を参照。パスワードハッシュは返さない。', 'SYSTEM', 'SYSTEM'),
    ('account.profile.update', 'プロフィール更新', '氏名・ふりがな・学生の学年のみ。ロール・状態・期限・認証情報は対象外。', 'SYSTEM', 'SYSTEM'),
    ('account.create', 'アカウント作成', '管理画面で作成。学生と保護者は必ずペアで作成。公開登録APIとは別権限。', 'SYSTEM', 'SYSTEM'),
    ('account.status.update', 'アカウント状態更新', 'アカウントの有効・無効を変更。', 'SYSTEM', 'SYSTEM'),
    ('account.role.update', 'アカウントロール更新', '関係制約を満たす場合のみ種別を変更。公開登録からは変更不可。', 'SYSTEM', 'SYSTEM'),
    ('role.read', 'ロール権限参照', 'ロールと権限割当を参照。', 'SYSTEM', 'SYSTEM'),
    ('role.permission.update', 'ロール権限更新', 'ロールの権限割当を追加・削除。', 'SYSTEM', 'SYSTEM')
ON CONFLICT ("権限コード") DO NOTHING;

INSERT INTO public."ACC_ロール権限" ("ロールコード", "権限コード", "データ範囲", "登録ID", "更新ID") VALUES
    ('STUDENT', 'account.read', 'SELF', 'SYSTEM', 'SYSTEM'),
    ('STUDENT', 'account.profile.update', 'SELF', 'SYSTEM', 'SYSTEM'),
    ('GUARDIAN', 'account.read', 'SELF', 'SYSTEM', 'SYSTEM'),
    ('GUARDIAN', 'account.read', 'LINKED_STUDENT', 'SYSTEM', 'SYSTEM'),
    ('GUARDIAN', 'account.profile.update', 'SELF', 'SYSTEM', 'SYSTEM'),
    ('GUARDIAN', 'account.profile.update', 'LINKED_STUDENT', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'account.read', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'account.profile.update', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'account.create', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'account.status.update', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'account.role.update', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'role.read', 'ALL', 'SYSTEM', 'SYSTEM'),
    ('ADMIN', 'role.permission.update', 'ALL', 'SYSTEM', 'SYSTEM')
ON CONFLICT ("ロールコード", "権限コード", "データ範囲") DO NOTHING;

COMMIT;
