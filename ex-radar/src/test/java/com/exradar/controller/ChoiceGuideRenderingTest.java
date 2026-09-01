package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.*;
import com.exradar.service.ExperiencePostService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChoiceGuideRenderingTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostService posts;
  @Autowired ExperiencePostRepository postRepository;

  @BeforeEach
  void prepareContributor() {
    if (users.findByEmailIgnoreCase("choice@example.com").isEmpty())
      users.save(new User("choice@example.com", "encoded", "選択肢利用者", Role.USER));
    var category =
        categories
            .findBySlug("choice-rendering")
            .orElseGet(() -> categories.save(new Category("選択肢表示", "choice-rendering", 999)));
    if (!posts.canReadExperiences("choice@example.com"))
      posts.create(validForm(category.getId()), "choice@example.com");
  }

  @AfterEach
  void removeFixture() {
    users
        .findByEmailIgnoreCase("choice@example.com")
        .ifPresent(
            user -> {
              postRepository.deleteAll(postRepository.findByAuthorIdAndPublishedTrue(user.getId()));
              users.delete(user);
            });
    categories.findBySlug("choice-rendering").ifPresent(categories::delete);
  }

  @Test
  void contributorCanRenderChoiceGuideWithOpenInViewDisabled() throws Exception {
    mvc.perform(get("/choices/choice-rendering").with(user("choice@example.com")))
        .andExpect(status().isOk())
        .andExpect(view().name("choices/detail"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("選択肢表示を経験した人から学ぶ")));
  }

  private ExperiencePostForm validForm(Long categoryId) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle("選択肢ページ表示用の経験");
    f.setSituationBefore("選択前の状況");
    f.setWorries("当時の悩み");
    f.setAlternatives("検討した選択肢");
    f.setChoiceMade("実際に選んだこと");
    f.setReason("選んだ理由");
    f.setOutcome("その後の結果");
    f.setGoodThings("良かったこと");
    f.setDifficulties("大変だったこと");
    f.setUnexpectedThings("想定外だったこと");
    f.setLearned("経験して分かったこと");
    f.setLesson("この経験から得た教訓");
    f.setSatisfaction(7);
    f.setRegret(3);
    f.setAdviceToPastSelf("過去の自分への言葉");
    f.setPublished(true);
    return f;
  }
}
