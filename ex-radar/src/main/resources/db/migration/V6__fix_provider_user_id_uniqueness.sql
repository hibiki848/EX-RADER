-- provider_user_id(Googleのsub等)はプロバイダーをまたいでも実質グローバルに一意なIDのため、
-- (auth_provider, provider_user_id)の複合UNIQUEではなく、provider_user_id単体でUNIQUEにする。
-- これにより「既存のLOCALアカウントにGoogleアカウントを連携する」際、auth_providerを
-- 'LOCAL'のまま維持してprovider_user_idだけを設定しても一意性が正しく保証される。
-- 既存データはprovider_user_idがNULLの行のみのため、この変更による既存データへの影響はない
-- (SQL標準・PostgreSQL・H2いずれもUNIQUE制約はNULL同士を別値として扱うため、複数のNULLが許容される)。
ALTER TABLE users DROP CONSTRAINT uk_users_provider;
ALTER TABLE users ADD CONSTRAINT uk_users_provider_user_id UNIQUE(provider_user_id);
