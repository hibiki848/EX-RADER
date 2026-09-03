package com.exradar.repository;

import com.exradar.dto.AdminUserSortField;
import com.exradar.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.SortDirection;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public class AdminUserRepositoryCustomImpl implements AdminUserRepositoryCustom {
  private final EntityManager entityManager;

  public AdminUserRepositoryCustomImpl(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public Page<User> search(
      Specification<User> spec, AdminUserSortField sortField, Sort.Direction direction, Pageable pageable) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // distinct(true)は付けない: AdminUserSpecificationsが使うのはEXISTS/相関スカラーサブクエリ
    // のみで、Userを複数行に増やすようなコレクションJOINを行わないため本来不要な上、
    // H2はSELECT DISTINCTと「SELECT句に無い式でのORDER BY」(投稿数などの相関サブクエリ
    // ソート)を組み合わせるとエラーになるため、むしろ付けてはいけない。
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> root = query.from(User.class);
    Predicate where = spec.toPredicate(root, query, cb);
    query.select(root).where(where);
    query.orderBy(order(cb, query, root, sortField, direction));

    TypedQuery<User> typedQuery = entityManager.createQuery(query);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    List<User> content = typedQuery.getResultList();

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<User> countRoot = countQuery.from(User.class);
    countQuery.select(cb.count(countRoot)).where(spec.toPredicate(countRoot, countQuery, cb));
    long total = entityManager.createQuery(countQuery).getSingleResult();

    return new PageImpl<>(content, pageable, total);
  }

  @Override
  public List<Long> findAllMatchingIds(Specification<User> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<User> root = query.from(User.class);
    query.select(root.get("id")).where(spec.toPredicate(root, query, cb));
    return entityManager.createQuery(query).getResultList();
  }

  /**
   * 直接カラムのソート(登録日・初回/最終ログイン日・初回有料加入日)はroot.get(...)、
   * 投稿数・初回投稿日・最新投稿日・加入期間はAdminUserSpecificationsの相関サブクエリ/
   * durationBetween式をそのまま並び替えにも使う(WHERE句とORDER BY句で計算方法がずれない
   * ようにするため)。null値は常に末尾(NULLS LAST相当)になるようHibernateの
   * NullPrecedence.LASTを明示する(H2 MODE=PostgreSQL・PostgreSQLどちらでも同じ結果)。
   */
  private List<Order> order(
      CriteriaBuilder cb,
      CriteriaQuery<?> query,
      Root<User> root,
      AdminUserSortField field,
      Sort.Direction direction) {
    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    Expression<?> expression =
        switch (field) {
          case REGISTERED_AT -> root.get("createdAt");
          case FIRST_LOGIN_AT -> root.get("firstLoginAt");
          case LAST_LOGIN_AT -> root.get("lastLoginAt");
          case FIRST_PAID_AT -> root.get("firstPaidAt");
          case FIRST_POST_AT -> AdminUserSpecifications.firstPostAtSubquery(query, cb, root);
          case LAST_POST_AT -> AdminUserSpecifications.lastPostAtSubquery(query, cb, root);
          case POST_COUNT -> AdminUserSpecifications.postCountSubquery(query, cb, root);
          case PAID_DURATION_DAYS -> AdminUserSpecifications.paidDurationDaysExpression(cb, root);
        };
    SortDirection sortDirection =
        direction == Sort.Direction.ASC ? SortDirection.ASCENDING : SortDirection.DESCENDING;
    // 常に安定した順序になるよう、同値の場合はid降順を副次キーとして加える。
    return List.of(
        hcb.sort((JpaExpression<?>) expression, sortDirection, NullPrecedence.LAST),
        cb.desc(root.get("id")));
  }
}
