package com.exradar.repository;

import static org.assertj.core.api.Assertions.*;

import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.entity.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.*;

@DataJpaTest
class ExperiencePostSearchRepositoryTest {
  @Autowired ExperiencePostRepository posts;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired TagRepository tags;
  User author;
  Category career, school;

  @BeforeEach
  void setup() {
    author = users.save(new User("search@example.com", "encoded", "検索ユーザー", Role.USER));
    career = categories.save(new Category("転職", "search-career", 1));
    school = categories.save(new Category("進学", "search-school", 2));
  }

  @Test
  void combinesConditionsWithAndAndExcludesDraft() {
    save("Java転職", career, true, 28, "30代", 8, 2, 3, true, "IT", "Java");
    save("Java進学", school, true, 20, "20代", 9, 1, 2, true, "Java");
    save("非公開Java転職", career, false, 28, "30代", 10, 1, 3, true, "Java");
    var c =
        new ExperienceSearchCriteria(
            "Java", career.getId(), "Java", 25, 30, "30代", null, null, 8, 3, 3, 3, true);
    var result = posts.findAll(ExperiencePostSpecifications.publicSearch(c), PageRequest.of(0, 10));
    assertThat(result.getContent()).extracting(ExperiencePost::getTitle).containsExactly("Java転職");
  }

  @Test
  void distinctAndPagingRemainStableWhenPostHasSeveralTags() {
    for (int i = 0; i < 13; i++)
      save("公開投稿" + i, career, true, 25, "20代", 7, 3, 1, true, "共通", "タグ" + i);
    var c =
        new ExperienceSearchCriteria(
            null, null, "共通", null, null, null, null, null, null, null, null, null, null);
    var first =
        posts.findAll(
            ExperiencePostSpecifications.publicSearch(c), PageRequest.of(0, 5, Sort.by("id")));
    var third =
        posts.findAll(
            ExperiencePostSpecifications.publicSearch(c), PageRequest.of(2, 5, Sort.by("id")));
    assertThat(first.getTotalElements()).isEqualTo(13);
    assertThat(first.getContent())
        .hasSize(5)
        .extracting(ExperiencePost::getId)
        .doesNotHaveDuplicates();
    assertThat(third.getContent()).hasSize(3);
  }

  private ExperiencePost save(
      String title,
      Category category,
      boolean published,
      int age,
      String group,
      int satisfaction,
      int regret,
      int years,
      boolean again,
      String... tagNames) {
    var p = new ExperiencePost(author);
    p.updateContent(
        category,
        title,
        age,
        "会社員",
        group,
        years,
        "選択前の状況",
        "悩み",
        "選択肢",
        "選択",
        "理由",
        "Javaを使う結果",
        "良かった",
        "大変",
        "想定外",
        satisfaction,
        regret,
        again,
        "助言");
    if (published) p.publish();
    var values = new LinkedHashSet<com.exradar.entity.Tag>();
    for (String name : tagNames)
      values.add(
          tags.findByName(name).orElseGet(() -> tags.save(new com.exradar.entity.Tag(name))));
    p.replaceTags(values);
    return posts.save(p);
  }
}
