-- reports.statusはこれまでJavaコード上に読み取り用のgetterが一切存在せず、書き込み経路も
-- エンティティのデフォルト値(OPEN)以外に存在しなかったため、既存の全行は実質的に必ず
-- status='OPEN'のはずである。念のため、旧enum(OPEN/REVIEWING/RESOLVED/DISMISSED)の
-- 他の値が万一存在していても安全に新enum(PENDING/REVIEWING/NO_ACTION/HIDDEN/DELETED)へ
-- 移行できるよう、想定される旧値すべてを明示的にマッピングする。
UPDATE reports SET status = 'PENDING' WHERE status = 'OPEN';
UPDATE reports SET status = 'NO_ACTION' WHERE status = 'RESOLVED';
UPDATE reports SET status = 'NO_ACTION' WHERE status = 'DISMISSED';
-- REVIEWINGは新enumにもそのまま存在するため変換不要。
