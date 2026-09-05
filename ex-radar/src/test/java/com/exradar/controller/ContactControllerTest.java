package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Category;
import com.exradar.entity.ContactInquiry;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ContactInquiryRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.ExperiencePostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * お問い合わせフォーム(/contact)。ContactRateLimiterはアプリ全体で共有される単一の
 * インメモリ状態を持つため、各テストは(送信元IPアドレスごとに制限がかかる仕組みを
 * 悪用して)専用の偽IPアドレスを使うことでテスト間の干渉を避けている
 * (レート制限そのものを検証するテストだけは、あえて同一IPを使い回す)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContactControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired ContactInquiryRepository inquiries;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostService postService;
  @Autowired PasswordEncoder encoder;

  private static int ipCounter = 0;

  private RequestPostProcessor uniqueIp() {
    return request -> {
      request.setRemoteAddr("10.20.30." + (++ipCounter % 250 + 1));
      return request;
    };
  }

  @Test
  void anonymousCanOpenTheForm() throws Exception {
    mvc.perform(get("/contact")).andExpect(status().isOk()).andExpect(view().name("contact"));
  }

  @Test
  void loggedInUsersNameAndEmailArePrefilled() throws Exception {
    var loggedIn =
        users.save(new User("contact-prefill@example.com", encoder.encode("password123"), "問い合わせ太郎", Role.USER));
    mvc.perform(get("/contact").with(user(loggedIn.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("問い合わせ太郎")))
        .andExpect(content().string(containsString("contact-prefill@example.com")));
  }

  @Test
  void anonymousCanSubmitSuccessfullyAndGetsPrgRedirect() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "GENERAL")
                .param("email", "anonymous-sender@example.com")
                .param("subject", "サービスについての質問")
                .param("body", "サービスの使い方について教えてください。"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/contact"));

    assertThat(inquiries.count()).isEqualTo(1);
    var saved = inquiries.findAll().get(0);
    assertThat(saved.getUser()).isNull();
    assertThat(saved.getEmail()).isEqualTo("anonymous-sender@example.com");
  }

  @Test
  void loggedInUsersSubmissionIsLinkedToTheirAccount() throws Exception {
    var loggedIn =
        users.save(new User("contact-linked@example.com", encoder.encode("password123"), "リンク確認", Role.USER));
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .with(user(loggedIn.getEmail()).roles("USER"))
                .param("category", "ACCOUNT")
                .param("email", loggedIn.getEmail())
                .param("subject", "アカウントについて")
                .param("body", "アカウントの件でお問い合わせです。"))
        .andExpect(status().is3xxRedirection());

    ContactInquiry saved = inquiries.findAll().get(0);
    assertThat(saved.getUser()).isNotNull();
    assertThat(saved.getUser().getId()).isEqualTo(loggedIn.getId());
  }

  @Test
  void requiredFieldsAreValidated() throws Exception {
    mvc.perform(post("/contact").with(csrf()).with(uniqueIp()))
        .andExpect(status().isOk())
        .andExpect(view().name("contact"))
        .andExpect(
            model()
                .attributeHasFieldErrors("contactInquiryForm", "category", "email", "subject", "body"));
    assertThat(inquiries.count()).isZero();
  }

  @Test
  void invalidEmailFormatIsRejected() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "GENERAL")
                .param("email", "not-an-email")
                .param("subject", "件名")
                .param("body", "本文"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("contactInquiryForm", "email"));
    assertThat(inquiries.count()).isZero();
  }

  @Test
  void subjectOver100CharsIsRejected() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "GENERAL")
                .param("email", "long-subject@example.com")
                .param("subject", "あ".repeat(101))
                .param("body", "本文"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("contactInquiryForm", "subject"));
    assertThat(inquiries.count()).isZero();
  }

  @Test
  void bodyOver3000CharsIsRejected() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "GENERAL")
                .param("email", "long-body@example.com")
                .param("subject", "件名")
                .param("body", "あ".repeat(3001)))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("contactInquiryForm", "body"));
    assertThat(inquiries.count()).isZero();
  }

  @Test
  void existingRelatedPostCanBeLinked() throws Exception {
    var author = users.save(new User("related-post-author@example.com", encoder.encode("password123"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "contact-related-post-category", 1));
    var post = postService.create(validForm(category.getId()), author.getEmail());

    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "REPORT")
                .param("email", "reporter@example.com")
                .param("subject", "不適切な投稿の報告")
                .param("body", "この投稿は不適切だと思います。")
                .param("relatedPostId", post.getId().toString()))
        .andExpect(status().is3xxRedirection());

    var saved = inquiries.findAll().get(0);
    assertThat(saved.getRelatedPost()).isNotNull();
    assertThat(saved.getRelatedPost().getId()).isEqualTo(post.getId());
  }

  @Test
  void nonExistentRelatedPostIsRejectedByServerSideValidation() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "REPORT")
                .param("email", "reporter2@example.com")
                .param("subject", "不適切な投稿の報告")
                .param("body", "この投稿は不適切だと思います。")
                .param("relatedPostId", "999999999"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("contactInquiryForm", "relatedPostId"));
    assertThat(inquiries.count()).isZero();
  }

  @Test
  void submissionRequiresCsrf() throws Exception {
    mvc.perform(
            post("/contact")
                .with(uniqueIp())
                .param("category", "GENERAL")
                .param("email", "no-csrf@example.com")
                .param("subject", "件名")
                .param("body", "本文"))
        .andExpect(status().isForbidden());
    assertThat(inquiries.count()).isZero();
  }

  /** 管理画面はth:textでのみ表示するため保存内容自体はそのままでよいが、少なくとも保存された生スクリプトが原因でエラーにならないことを確認する。 */
  @Test
  void scriptLikeInputIsStoredAsPlainTextWithoutError() throws Exception {
    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(uniqueIp())
                .param("category", "BUG")
                .param("email", "xss-check@example.com")
                .param("subject", "<script>alert(1)</script>")
                .param("body", "<img src=x onerror=alert(1)>"))
        .andExpect(status().is3xxRedirection());

    var saved = inquiries.findAll().get(0);
    assertThat(saved.getSubject()).isEqualTo("<script>alert(1)</script>");

    var admin = users.save(new User("xss-admin@example.com", encoder.encode("password123"), "運営", Role.ADMIN));
    // 管理画面の詳細表示では、生HTMLとして解釈されない(th:text使用によりエスケープされる)ことを確認する。
    mvc.perform(get("/admin/inquiries/{id}", saved.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
        .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
  }

  /** 短時間の大量送信を制限する(現在の実装では同一IPアドレスから10分間に5件まで)。 */
  @Test
  void repeatedSubmissionsFromTheSameIpAreRateLimited() throws Exception {
    var ip = "203.0.113.77";
    RequestPostProcessor sameIp = request -> {
      request.setRemoteAddr(ip);
      return request;
    };

    for (int i = 0; i < 5; i++) {
      mvc.perform(
              post("/contact")
                  .with(csrf())
                  .with(sameIp)
                  .param("category", "GENERAL")
                  .param("email", "rate-limit-" + i + "@example.com")
                  .param("subject", "件名" + i)
                  .param("body", "本文" + i))
          .andExpect(status().is3xxRedirection());
    }

    mvc.perform(
            post("/contact")
                .with(csrf())
                .with(sameIp)
                .param("category", "GENERAL")
                .param("email", "rate-limit-blocked@example.com")
                .param("subject", "6件目")
                .param("body", "これは制限されるはずです。"))
        .andExpect(status().isOk())
        .andExpect(view().name("contact"))
        .andExpect(model().attribute("rateLimitError", containsString("しばらく時間をおいて")));

    assertThat(inquiries.count()).isEqualTo(5);
  }

  private ExperiencePostForm validForm(Long categoryId) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle("お問い合わせ確認用の体験談");
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
