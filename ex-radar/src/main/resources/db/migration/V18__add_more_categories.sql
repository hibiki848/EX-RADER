-- 体験談カテゴリに「仕事」「人間関係」「お金」「その他」を追加する。
-- V7と同じ冪等パターン(WHERE NOT EXISTS)を用い、既存カテゴリの削除・変更は一切行わない。

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '仕事', 'work', 16, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'work');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '人間関係', 'relationships', 17, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'relationships');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT 'お金', 'money', 18, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'money');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT 'その他', 'other', 19, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'other');
