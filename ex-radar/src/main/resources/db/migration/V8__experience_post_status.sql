-- 「体験談」にDRAFT(下書き)/PUBLISHED(公開)のステータスを導入する。
-- 既存のpublished(boolean)を置き換える。既存データはpublished=TRUEだったものだけを
-- PUBLISHEDにし、それ以外はデフォルトのDRAFTのままとすることで、
-- 現在公開されている経験談が消えたり非公開になったりしないことを保証する。
ALTER TABLE experience_posts ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
UPDATE experience_posts SET status = 'PUBLISHED' WHERE published = TRUE;
ALTER TABLE experience_posts DROP COLUMN published;

-- 下書きはカテゴリ未選択・満足度/後悔度未入力のまま保存されうるため、
-- これらのNOT NULL制約を緩和する。CHECK(satisfaction BETWEEN 1 AND 10)などの
-- 既存制約はnullをUNKNOWNとして扱い違反にならないため、そのまま残してよい。
ALTER TABLE experience_posts ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE experience_posts ALTER COLUMN satisfaction DROP NOT NULL;
ALTER TABLE experience_posts ALTER COLUMN regret DROP NOT NULL;
