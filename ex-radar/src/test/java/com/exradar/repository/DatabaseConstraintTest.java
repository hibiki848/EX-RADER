package com.exradar.repository;

import static org.assertj.core.api.Assertions.*;

import com.exradar.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class DatabaseConstraintTest {
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired TestEntityManager em;

  @Test
  void emailAndCategorySlugAreUnique() {
    users.saveAndFlush(new User("unique@example.com", "hash", "A", Role.USER));
    assertThatThrownBy(
            () -> {
              users.saveAndFlush(new User("unique@example.com", "hash", "B", Role.USER));
            })
        .isInstanceOfAny(DataIntegrityViolationException.class, RuntimeException.class);
    em.clear();
    categories.saveAndFlush(new Category("大学進学", "university", 1));
    assertThatThrownBy(
            () -> {
              categories.saveAndFlush(new Category("別名", "university", 2));
            })
        .isInstanceOfAny(DataIntegrityViolationException.class, RuntimeException.class);
    em.clear();
  }
}
