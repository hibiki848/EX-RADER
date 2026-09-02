-- 公開記事にSEOタイトルとmeta descriptionを個別設定できるようにする。
-- どちらも未入力(NULL)を許容し、既存記事はそのまま(NULL)で正常に表示され続ける
-- (アプリ側でtitleタグ・meta descriptionへのフォールバックを行う)。
ALTER TABLE articles ADD COLUMN seo_title VARCHAR(120);
ALTER TABLE articles ADD COLUMN meta_description VARCHAR(300);
