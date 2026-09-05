package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Article;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.ArticleRepository;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.ExperiencePostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired ExperiencePostRepository posts;
  @Autowired ExperiencePostService postService;
  @Autowired CategoryRepository categories;
  @Autowired ArticleRepository articles;
  @Autowired PasswordEncoder encoder;

  @Test
  void regularUserCannotOpenAdminDashboard() throws Exception {
    mvc.perform(get("/admin").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanSeeDashboardAndPromoteUser() throws Exception {
    User admin = users.save(new User("admin@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target = users.save(new User("target@example.com", encoder.encode("password123"), "Target", Role.USER));

    mvc.perform(get("/admin").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/dashboard"))
        .andExpect(model().attribute("userCount", 2L));

    mvc.perform(post("/admin/users/{id}/role", target.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).param("role", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin"));

    assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void adminCanToggleAnalyticsExclusionForRegularUser() throws Exception {
    User admin =
        users.save(new User("admin-ga@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target =
        users.save(new User("operator-account@example.com", encoder.encode("password123"), "Operator", Role.USER));
    assertThat(target.isAnalyticsExcluded()).isFalse();

    mvc.perform(
            post("/admin/users/{id}/analytics-exclusion", target.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("excluded", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin"));
    assertThat(users.findById(target.getId()).orElseThrow().isAnalyticsExcluded()).isTrue();

    // 通常ユーザーへ戻すと再びfalseになる
    mvc.perform(
            post("/admin/users/{id}/analytics-exclusion", target.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("excluded", "false"))
        .andExpect(status().is3xxRedirection());
    assertThat(users.findById(target.getId()).orElseThrow().isAnalyticsExcluded()).isFalse();
  }

  @Test
  void adminCanOpenSelectedUsersPosts() throws Exception {
    User admin = users.save(new User("admin-detail@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target = users.save(new User("target-detail@example.com", encoder.encode("password123"), "Target", Role.USER));

    mvc.perform(get("/admin/users/{id}", target.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/dashboard"))
        .andExpect(model().attribute("selectedUser", target));
  }

  /**
   * 管理者ダッシュボードの「投稿数」(publishedPostCount)は体験談(ExperiencePost)のみを
   * 数え、管理者が作成した公開記事(Article)は含めないことを確認する。
   * ExperiencePostRepository/ArticleRepositoryは別テーブル(別リポジトリ)だが、
   * publishedPostCount()の実装が誤って両方を合算するよう変更されても検知できるよう、
   * 実際に両方を1件ずつ用意してAPIレスポンス上の数値で検証する。
   */
  @Test
  void publishedPostCountCountsExperiencePostsOnlyAndExcludesArticles() throws Exception {
    User admin =
        users.save(new User("post-count-admin@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User author =
        users.save(new User("post-count-author@example.com", encoder.encode("password123"), "Author", Role.USER));
    var category = categories.save(new com.exradar.entity.Category("転職", "post-count-category", 1));

    postService.create(validForm(category.getId()), author.getEmail());

    var article = new Article("投稿数カウント確認用記事", "post-count-article", "概要", "本文");
    article.publish();
    articles.save(article);

    mvc.perform(get("/admin").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        // 体験談1件のみを数える。記事1件を加えた2にはならない。
        .andExpect(model().attribute("publishedPostCount", 1L));
  }

  /**
   * 「このブラウザをアクセス解析から除外」トグルは、ログイン中ユーザーのDBフラグとは別に
   * Cookie(NavigationAdvice.BROWSER_EXCLUSION_COOKIE)でブラウザ単位に持たせる。
   * Set-Cookieの属性(HttpOnly/Secure/SameSite/Max-Age)と、除外解除時に即time失効させる
   * (Max-Age=0)ことをここで検証する。
   */
  @Test
  void togglingBrowserAnalyticsExclusionSetsAndClearsCookie() throws Exception {
    User admin =
        users.save(new User("admin-browser-ex@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));

    var excludeResult =
        mvc.perform(
                post("/admin/browser-analytics-exclusion")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("excluded", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin"))
            .andReturn();
    String setCookie = excludeResult.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("exr_ga_excluded=1");
    assertThat(setCookie).contains("HttpOnly");
    assertThat(setCookie).contains("Secure");
    assertThat(setCookie).contains("SameSite=Lax");
    assertThat(setCookie).contains("Max-Age=34560000"); // 400日

    // ダッシュボードのモデル属性にも反映される
    mvc.perform(
            get("/admin")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .cookie(new jakarta.servlet.http.Cookie("exr_ga_excluded", "1")))
        .andExpect(model().attribute("browserAnalyticsExcluded", true));

    var restoreResult =
        mvc.perform(
                post("/admin/browser-analytics-exclusion")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("excluded", "false"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String clearCookie = restoreResult.getResponse().getHeader("Set-Cookie");
    assertThat(clearCookie).contains("exr_ga_excluded=");
    assertThat(clearCookie).contains("Max-Age=0");
  }

  private ExperiencePostForm validForm(Long categoryId) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle("投稿数カウント確認用の体験談");
    f.setSituationBefore("状況");
    f.setWorries("悩み");
    f.setAlternatives("選択肢");
    f.setChoiceMade("選んだこと");
    f.setReason("理由");
    f.setOutcome("結果");
    f.setGoodThings("良かったこと");
    f.setDifficulties("大変だったこと");
    f.setUnexpectedThings("想定外だったこと");
    f.setLesson("この経験から得た教訓の本文です");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    return f;
  }
}
