package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.ExperiencePostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExperienceFormRenderingTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostService postService;

  @BeforeEach
  void createLoginUser() {
    if (users.findByEmailIgnoreCase("form@example.com").isEmpty())
      users.save(new User("form@example.com", "encoded", "フォーム確認", Role.USER));
  }

  @Test
  void loggedInUserCanRenderNewExperienceForm() throws Exception {
    mvc.perform(get("/experiences/new").with(user("form@example.com")))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/form"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("経験して分かったこと・教訓")));
  }

  /** 「下書き復元」のサーバー側部分: 保存済みの下書きを編集画面で開くと、内容がフォームへ復元されること。 */
  @Test
  @Transactional
  void editingExistingDraftRestoresSavedContentIntoForm() throws Exception {
    var owner = users.save(new User("draft-restore-render@example.com", "encoded", "下書き確認", Role.USER));
    var category = categories.save(new Category("転職", "draft-restore-render-category", 1));
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle("復元されるべき下書きタイトル");
    f.setSituationBefore("復元されるべき状況の本文");
    var draft = postService.createDraft(f, owner.getEmail());

    mvc.perform(get("/experiences/" + draft.getId() + "/edit").with(user(owner.getEmail())))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/form"))
        .andExpect(model().attribute("editing", true))
        .andExpect(model().attribute("isDraft", true))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("復元されるべき下書きタイトル")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("復元されるべき状況の本文")));
  }
}
