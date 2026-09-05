-- 投稿ボタン連打による二重投稿の防止(submission_token)と、同一ユーザーによる実質同一投稿の
-- 検出(content_fingerprint)のための列を追加する。どちらも既存投稿では未設定(NULL)のままで
-- 問題ない(過去投稿を遡って無効化・再判定することはしない。今後の新規投稿・編集保存から
-- 適用される)。

-- 投稿フォーム表示時にサーバー側で発行するUUID。同じトークンでの投稿は1回だけ受理し、
-- 2回目以降は新規作成せず既存の投稿をそのまま返す(冪等)。UNIQUE制約により、
-- 同時多発的な二重送信が万一INSERTまで到達しても、DBレベルで2件目を弾ける。
ALTER TABLE experience_posts ADD COLUMN submission_token VARCHAR(64);
CREATE UNIQUE INDEX uk_experience_posts_submission_token ON experience_posts(submission_token);

-- 主要な自由記述項目を正規化・連結してSHA-256でハッシュ化したもの。同一ユーザーの
-- 過去投稿との完全・実質同一判定の高速な事前チェックに使う(全文再正規化を毎回
-- 行わずに済むようにするため)。ユーザーをまたいだ一致は判定対象外のため、
-- author_idとの複合インデックスのみで十分(グローバルUNIQUEにはしない)。
ALTER TABLE experience_posts ADD COLUMN content_fingerprint VARCHAR(64);
CREATE INDEX idx_experience_posts_author_fingerprint ON experience_posts(author_id, content_fingerprint);
