-- カテゴリのマスタデータをFlywayで一元管理する(唯一の定義元)。
-- これまでカテゴリはdevプロファイル限定のDevDataInitializerでのみ作成されており、
-- 本番(prodプロファイル)ではcategoriesテーブルが常に空だったため、
-- 新規投稿画面・検索画面のどちらでもカテゴリを選択できなかった。
-- WHERE NOT EXISTSで安全にしているため、何らかの理由で一部のカテゴリが
-- 既に存在していても失敗しない(冪等)。既存カテゴリの削除・変更は一切行わない。

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '勉強', 'study', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'study');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '高校進学', 'high-school', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'high-school');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '大学進学', 'university', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'university');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '専門学校', 'vocational-school', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'vocational-school');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '高卒就職', 'work-after-high-school', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'work-after-high-school');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '大学中退', 'university-dropout', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'university-dropout');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '就職', 'employment', 6, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'employment');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '転職', 'career-change', 7, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'career-change');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '異業種転職', 'career-change-industry', 8, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'career-change-industry');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '公務員', 'public-servant', 9, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'public-servant');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '資格取得', 'qualification', 10, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'qualification');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '上京', 'move-to-tokyo', 11, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'move-to-tokyo');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '地元就職', 'local-employment', 12, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'local-employment');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '地元へ戻る', 'return-home', 13, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'return-home');

INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT 'フリーランス', 'freelance', 14, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'freelance');

-- 新規追加カテゴリ
INSERT INTO categories(name, slug, display_order, active, created_at, updated_at)
SELECT '恋愛', 'love', 15, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'love');
