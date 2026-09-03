-- 管理者ユーザー検索基盤のための新規カラム。
-- last_login_at / first_login_at: これまで一切記録していなかったログイン日時。
--   今回のリリース以降のログインからのみ記録が始まるため、既存ユーザーは全員NULL
--   (=「一度もログインしていない」ではなく「記録開始前のためデータなし」。過去の
--   ログイン日時を推測して埋めることはしない)。
-- current_plan / first_paid_at / premium_period_started_at / premium_period_ended_at:
--   有料プランの概念自体がこれまでシステムに存在しなかったため、既存ユーザーは
--   全員current_plan='FREE'・日時カラムは全てNULLとなる(推測でのバックフィルはしない)。
--   実際の有料プラン加入・解約を行う機能は本マイグレーションの対象外(今後実装)。
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN first_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN current_plan VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN first_paid_at TIMESTAMP;
ALTER TABLE users ADD COLUMN premium_period_started_at TIMESTAMP;
ALTER TABLE users ADD COLUMN premium_period_ended_at TIMESTAMP;

-- 管理者ユーザー検索の絞り込み・並び替え対象になる列にインデックスを追加する。
CREATE INDEX idx_users_last_login_at ON users(last_login_at);
CREATE INDEX idx_users_first_login_at ON users(first_login_at);
CREATE INDEX idx_users_current_plan ON users(current_plan);
CREATE INDEX idx_users_first_paid_at ON users(first_paid_at);

-- 投稿数・初回投稿日の絞り込み(author_id+statusでの相関サブクエリ)を高速化する。
CREATE INDEX idx_experience_posts_author_status ON experience_posts(author_id, status);
