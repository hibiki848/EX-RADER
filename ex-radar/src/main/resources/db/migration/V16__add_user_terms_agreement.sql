-- 利用規約・プライバシーポリシーへの同意日時。新規登録時に同意した瞬間の日時を記録する。
-- 既存ユーザーは本カラム追加以前に登録しているため、同意日時を推測で埋めずNULLのままにする。
ALTER TABLE users ADD COLUMN terms_agreed_at TIMESTAMP;
ALTER TABLE users ADD COLUMN privacy_policy_agreed_at TIMESTAMP;
