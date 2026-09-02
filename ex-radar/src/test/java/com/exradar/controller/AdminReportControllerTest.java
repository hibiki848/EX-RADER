package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ReportRepository;
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

/** 通報管理画面(/admin/reports)の権限制御・ステータス変更・非公開対応・削除対応の検証。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminReportControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ReportRepository reports;
  @Autowired ExperiencePostService postService;
  @Autowired PasswordEncoder encoder;

  @Test
  void regularUserCannotOpenReportManagement() throws Exception {
    var user = users.save(new User("report-mgmt-user@example.com", encoder.encode("password"), "一般", Role.USER));
    mvc.perform(get("/admin/reports").with(user(user.getEmail()).roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanOpenReportManagementAndSeeReportDetails() throws Exception {
    var admin = users.save(new User("report-mgmt-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-mgmt-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-mgmt-category", 1));
    var post = publish(author, category, "通報される体験談");
    var reporter =
        users.save(new User("report-mgmt-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "不適切な内容です"));

    mvc.perform(get("/admin/reports").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/reports/list"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("通報される体験談")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("不適切な内容です")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("未対応")));
  }

  @Test
  void adminCanChangeReportStatusToReviewing() throws Exception {
    var admin = users.save(new User("report-status-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-status-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-status-category", 1));
    var post = publish(author, category, "確認中にする体験談");
    var reporter =
        users.save(new User("report-status-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    var report = reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "理由"));

    mvc.perform(
            post("/admin/reports/{id}/status", report.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("status", "REVIEWING"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/reports"));

    assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REVIEWING);
  }

  @Test
  void statusEndpointRejectsHiddenAndDeletedAsPlainStatusChange() throws Exception {
    var admin = users.save(new User("report-badstatus-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-badstatus-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-badstatus-category", 1));
    var post = publish(author, category, "不正なステータス変更対象の体験談");
    var reporter =
        users.save(new User("report-badstatus-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    var report = reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "理由"));

    mvc.perform(
            post("/admin/reports/{id}/status", report.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("status", "DELETED"))
        .andExpect(status().is3xxRedirection());

    // /statusエンドポイント経由でのHIDDEN/DELETEDへの変更は拒否され、ステータスは変わらない
    assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.PENDING);
    assertThat(postService.getVisible(post.getId(), author.getEmail()).isPublished()).isTrue();
  }

  @Test
  void adminCanHideReportedExperiencePost() throws Exception {
    var admin = users.save(new User("report-hide-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-hide-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-hide-category", 1));
    var post = publish(author, category, "非公開にされる体験談");
    var reporter =
        users.save(new User("report-hide-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    var report = reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "理由"));

    mvc.perform(
            post("/admin/reports/{id}/hide", report.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/reports"));

    assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.HIDDEN);
    // 削除はされていない(既存のDRAFT/PUBLISHED状態を再利用した非公開化であることを確認)
    assertThat(postService.getVisible(post.getId(), author.getEmail()).isPublished()).isFalse();
  }

  @Test
  void adminCanDeleteReportedExperiencePost() throws Exception {
    var admin = users.save(new User("report-delete-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-delete-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-delete-category", 1));
    var post = publish(author, category, "削除される体験談");
    var reporter =
        users.save(new User("report-delete-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    var report = reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "理由"));

    mvc.perform(
            post("/admin/reports/{id}/delete", report.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());

    assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.DELETED);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> postService.getVisible(post.getId(), author.getEmail()))
        .isInstanceOf(com.exradar.exception.ResourceNotFoundException.class);
  }

  @Test
  void statusChangeRequiresCsrf() throws Exception {
    var admin = users.save(new User("report-csrf-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var author = users.save(new User("report-csrf-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "report-csrf-category", 1));
    var post = publish(author, category, "CSRF確認用の体験談");
    var reporter =
        users.save(new User("report-csrf-reporter@example.com", encoder.encode("password"), "通報者", Role.USER));
    var report = reports.save(new Report(reporter, ReportTargetType.EXPERIENCE_POST, post.getId(), "理由"));

    mvc.perform(
            post("/admin/reports/{id}/status", report.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .param("status", "REVIEWING"))
        .andExpect(status().isForbidden());

    assertThat(reports.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.PENDING);
  }

  private ExperiencePost publish(User author, Category category, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle(title);
    f.setSituationBefore("状況");
    f.setWorries("悩み");
    f.setAlternatives("選択肢");
    f.setChoiceMade("選んだこと");
    f.setReason("理由");
    f.setOutcome("結果");
    f.setGoodThings("良かったこと");
    f.setDifficulties("大変だったこと");
    f.setUnexpectedThings("想定外だったこと");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    return postService.create(f, author.getEmail());
  }
}
