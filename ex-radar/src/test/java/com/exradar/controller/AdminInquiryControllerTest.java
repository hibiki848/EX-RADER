package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.ContactCategory;
import com.exradar.entity.ContactInquiry;
import com.exradar.entity.InquiryStatus;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.ContactInquiryRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 管理者向けお問い合わせ管理画面(/admin/inquiries/**)。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInquiryControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired ContactInquiryRepository inquiries;
  @Autowired PasswordEncoder encoder;

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private User regularUser(String email) {
    return users.save(new User(email, encoder.encode("password123"), "一般ユーザー", Role.USER));
  }

  private ContactInquiry save(User user, InquiryStatus status, String subject) {
    var inquiry =
        new ContactInquiry(
            user, ContactCategory.GENERAL, "問い合わせ者", "inquiry-" + subject + "@example.com", subject, "本文です", null);
    inquiry.changeStatus(status, java.time.LocalDateTime.now());
    return inquiries.save(inquiry);
  }

  @Test
  void adminCanAccessList() throws Exception {
    admin("inquiry-list-admin@example.com");
    mvc.perform(get("/admin/inquiries").with(user("inquiry-list-admin@example.com").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/inquiries/list"));
  }

  @Test
  void regularUserIsDeniedAccess() throws Exception {
    regularUser("inquiry-denied-user@example.com");
    mvc.perform(get("/admin/inquiries").with(user("inquiry-denied-user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/inquiries/1/status")
                .with(user("inquiry-denied-user@example.com").roles("USER"))
                .with(csrf())
                .param("status", "RESOLVED"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsDeniedAccess() throws Exception {
    mvc.perform(get("/admin/inquiries")).andExpect(status().is3xxRedirection());
  }

  @Test
  void listShowsAllInquiriesNewestFirst() throws Exception {
    var admin = admin("inquiry-order-admin@example.com");
    save(null, InquiryStatus.NEW, "1件目");
    save(null, InquiryStatus.NEW, "2件目");

    mvc.perform(get("/admin/inquiries").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("1件目")))
        .andExpect(content().string(containsString("2件目")));
  }

  @Test
  void listCanBeFilteredByStatus() throws Exception {
    var admin = admin("inquiry-filter-admin@example.com");
    save(null, InquiryStatus.NEW, "未対応の問い合わせ");
    save(null, InquiryStatus.RESOLVED, "解決済みの問い合わせ");

    var body =
        mvc.perform(
                get("/admin/inquiries")
                    .param("status", "NEW")
                    .with(user(admin.getEmail()).roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("未対応の問い合わせ").doesNotContain("解決済みの問い合わせ");
  }

  @Test
  void detailShowsFullContent() throws Exception {
    var admin = admin("inquiry-detail-admin@example.com");
    var user = regularUser("inquiry-detail-sender@example.com");
    var inquiry = save(user, InquiryStatus.NEW, "詳細確認用の件名");

    mvc.perform(get("/admin/inquiries/{id}", inquiry.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/inquiries/detail"))
        .andExpect(content().string(containsString("詳細確認用の件名")))
        .andExpect(content().string(containsString("このユーザーに運営メッセージを送る")))
        .andExpect(content().string(containsString("/admin/messages/new?selectedUserId=" + user.getId())));
  }

  @Test
  void sendMessageLinkIsNotShownForAnonymousSender() throws Exception {
    var admin = admin("inquiry-anon-link-admin@example.com");
    var inquiry = save(null, InquiryStatus.NEW, "未ログイン送信の件名");

    mvc.perform(get("/admin/inquiries/{id}", inquiry.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("このユーザーに運営メッセージを送る"))));
  }

  @Test
  void adminCanChangeStatusAndResolvedAtIsRecordedAndClearedConsistently() throws Exception {
    var admin = admin("inquiry-status-admin@example.com");
    var inquiry = save(null, InquiryStatus.NEW, "ステータス変更確認");

    mvc.perform(
            post("/admin/inquiries/{id}/status", inquiry.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("status", "RESOLVED"))
        .andExpect(status().is3xxRedirection());
    var resolved = inquiries.findById(inquiry.getId()).orElseThrow();
    assertThat(resolved.getStatus()).isEqualTo(InquiryStatus.RESOLVED);
    assertThat(resolved.getResolvedAt()).isNotNull();

    // 再度未解決系のステータスへ戻すと、resolvedAtは一貫してクリアされる。
    mvc.perform(
            post("/admin/inquiries/{id}/status", inquiry.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("status", "IN_PROGRESS"))
        .andExpect(status().is3xxRedirection());
    var reopened = inquiries.findById(inquiry.getId()).orElseThrow();
    assertThat(reopened.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
    assertThat(reopened.getResolvedAt()).isNull();
  }

  @Test
  void adminCanSaveAdminMemo() throws Exception {
    var admin = admin("inquiry-memo-admin@example.com");
    var inquiry = save(null, InquiryStatus.NEW, "メモ確認");

    mvc.perform(
            post("/admin/inquiries/{id}/memo", inquiry.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("memo", "電話で確認済み"))
        .andExpect(status().is3xxRedirection());

    assertThat(inquiries.findById(inquiry.getId()).orElseThrow().getAdminMemo()).isEqualTo("電話で確認済み");
  }

  @Test
  void pendingInquiryCountReflectsOnlyNewStatus() throws Exception {
    var admin = admin("inquiry-count-admin@example.com");
    save(null, InquiryStatus.NEW, "未対応A");
    save(null, InquiryStatus.NEW, "未対応B");
    save(null, InquiryStatus.RESOLVED, "解決済みC");

    mvc.perform(get("/admin").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("pendingInquiryCount", 2L));
  }

  @Test
  void adminDashboardLinksToInquiriesWithCount() throws Exception {
    var admin = admin("inquiry-dashboard-link-admin@example.com");
    save(null, InquiryStatus.NEW, "リンク確認用");

    mvc.perform(get("/admin").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("未対応のお問い合わせ")))
        .andExpect(content().string(containsString("href=\"/admin/inquiries\"")));
  }
}
