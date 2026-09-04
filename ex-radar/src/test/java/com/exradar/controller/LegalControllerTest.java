package com.exradar.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 利用規約・プライバシーポリシー・お問い合わせ。未ログイン・ログイン済みどちらからでも常に閲覧できる。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LegalControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void anonymousUserCanViewTerms() throws Exception {
    mvc.perform(get("/terms"))
        .andExpect(status().isOk())
        .andExpect(view().name("terms"))
        .andExpect(content().string(containsString("EXレーダー 利用規約")))
        .andExpect(content().string(containsString("第25条（お問い合わせ）")));
  }

  @Test
  void loggedInUserCanAlsoViewTerms() throws Exception {
    var loggedIn =
        users.save(new User("terms-logged-in@example.com", encoder.encode("password123"), "ログイン中ユーザー", Role.USER));
    mvc.perform(get("/terms").with(user(loggedIn.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  @Test
  void anonymousUserCanViewPrivacyAndContact() throws Exception {
    mvc.perform(get("/privacy")).andExpect(status().isOk()).andExpect(view().name("privacy"));
    mvc.perform(get("/contact")).andExpect(status().isOk()).andExpect(view().name("contact"));
  }

  @Test
  void loggedInUserCanAlsoViewPrivacy() throws Exception {
    var loggedIn =
        users.save(new User("privacy-logged-in@example.com", encoder.encode("password123"), "ログイン中ユーザー", Role.USER));
    mvc.perform(get("/privacy").with(user(loggedIn.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(view().name("privacy"));
  }

  /** プライバシーポリシーは実際に確認した取得情報・Google Analytics・Cookie等の説明を含む正式版であること。 */
  @Test
  void privacyPageContainsRealPolicySectionsNotAPlaceholder() throws Exception {
    mvc.perform(get("/privacy"))
        .andExpect(content().string(containsString("取得する情報")))
        .andExpect(content().string(containsString("Google Analytics")))
        .andExpect(content().string(containsString("Cookie")))
        .andExpect(content().string(not(containsString("準備中"))));
  }

  @Test
  void anonymousUserCanViewGuidelines() throws Exception {
    mvc.perform(get("/guidelines"))
        .andExpect(status().isOk())
        .andExpect(view().name("guidelines"))
        .andExpect(content().string(containsString("投稿してはいけない内容")));
  }

  @Test
  void loggedInUserCanAlsoViewGuidelines() throws Exception {
    var loggedIn =
        users.save(new User("guidelines-logged-in@example.com", encoder.encode("password123"), "ログイン中ユーザー", Role.USER));
    mvc.perform(get("/guidelines").with(user(loggedIn.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  @Test
  void termsPageHasCanonicalLinkAndIsIndexable() throws Exception {
    mvc.perform(get("/terms"))
        .andExpect(content().string(containsString("<title>利用規約 | EXレーダー</title>")))
        .andExpect(content().string(containsString("rel=\"canonical\"")))
        .andExpect(content().string(containsString("http://localhost/terms")));
  }

  @Test
  void footerLinksToTermsPrivacyGuidelinesAndContactAreRenderedOnAPublicPage() throws Exception {
    mvc.perform(get("/terms"))
        .andExpect(content().string(containsString("href=\"/terms\"")))
        .andExpect(content().string(containsString("href=\"/privacy\"")))
        .andExpect(content().string(containsString("href=\"/guidelines\"")))
        .andExpect(content().string(containsString("href=\"/contact\"")));
  }
}
