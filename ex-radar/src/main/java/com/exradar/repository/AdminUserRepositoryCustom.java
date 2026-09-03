package com.exradar.repository;

import com.exradar.dto.AdminUserSortField;
import com.exradar.entity.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 投稿数・初回投稿日等の相関サブクエリでの並び替えは、Spring Dataの
 * Specification+Sort(プロパティ名文字列によるパス解決)では表現できないため、
 * CriteriaBuilder/CriteriaQueryを直接組み立てるカスタム実装(AdminUserRepositoryCustomImpl)
 * を別途用意している。
 */
public interface AdminUserRepositoryCustom {
  Page<User> search(
      Specification<User> spec, AdminUserSortField sortField, Sort.Direction direction, Pageable pageable);

  /** 現在の検索条件に一致する全ユーザーのIDを、ページングせずまとめて取得する。将来の一括メッセージ配信で「現在の検索条件に一致する全員」を対象にする用途向け。 */
  List<Long> findAllMatchingIds(Specification<User> spec);
}
