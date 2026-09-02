-- 運営者・管理者アカウントなど、実際の利用者ではないアクセスをGA4/内部アクセス解析の
-- どちらからも除外できるようにする。既存ユーザーはすべてデフォルトのFALSE(除外しない)。
ALTER TABLE users ADD COLUMN analytics_excluded BOOLEAN NOT NULL DEFAULT FALSE;
