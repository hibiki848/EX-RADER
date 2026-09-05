-- 完全同一投稿の同時リクエスト競合(TOCTOU)をDBレベルでも防ぐ。
-- 異なるsubmissionTokenで送信された完全同一内容の投稿は、アプリ側の事前チェック
-- (DuplicatePostDetectionService)だけではごく短時間のレースをすり抜けうるため、
-- (author_id, content_fingerprint)にUNIQUE制約を追加し、最終防衛線とする。

-- content_fingerprintは今後「公開済み(PUBLISHED)投稿のみ」に設定する運用に変更する
-- (ExperiencePostService.assignPublishedFingerprint参照)。下書きは常にnullのままにする。
-- 標準SQLのUNIQUE制約はnull同士を衝突とみなさないため、下書き同士・教訓なしの
-- レガシー投稿(いずれもcontent_fingerprintがnull)は互いに影響を受けない。
-- 万一このマイグレーション適用前に下書きへ値が入っていた場合に備え、念のため
-- 非公開投稿のcontent_fingerprintを先にnullへ揃えておく。
UPDATE experience_posts SET content_fingerprint = NULL WHERE status <> 'PUBLISHED';

DROP INDEX idx_experience_posts_author_fingerprint;
CREATE UNIQUE INDEX uk_experience_posts_author_fingerprint ON experience_posts(author_id, content_fingerprint);
