package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Notification;
import com.exradar.entity.NotificationType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.form.PasswordChangeForm;
import com.exradar.form.ProfileForm;
import com.exradar.repository.NotificationRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {
  @Autowired AccountService service;
  @Autowired UserRepository users;
  @Autowired NotificationRepository notifications;
  @Autowired PasswordEncoder encoder;

  private User user;

  @BeforeEach
  void setup() {
    user =
        users.save(
            new User("account@example.com", encoder.encode("password123"), "変更前", Role.USER));
  }

  @Test
  void updatesProfileAndTrimsValues() {
    var form = new ProfileForm();
    form.setDisplayName(" 変更後 ");
    form.setAgeGroup(" 20代 ");
    form.setEducation(" 大学 ");
    form.setOccupation(" 会社員 ");
    form.setPrefecture(" 長崎県 ");
    form.setBiography(" 自分の判断軸を整理しています。 ");

    service.updateProfile(user.getEmail(), form);

    var updated = users.findById(user.getId()).orElseThrow();
    assertThat(updated.getDisplayName()).isEqualTo("変更後");
    assertThat(updated.getAgeGroup()).isEqualTo("20代");
    assertThat(updated.getPrefecture()).isEqualTo("長崎県");
    assertThat(updated.getBiography()).isEqualTo("自分の判断軸を整理しています。");
  }

  @Test
  void changesPasswordWhenCurrentPasswordAndConfirmationAreValid() {
    var form = passwordForm("password123", "new-password123", "new-password123");

    service.changePassword(user.getEmail(), form);

    var updated = users.findById(user.getId()).orElseThrow();
    assertThat(encoder.matches("new-password123", updated.getPassword())).isTrue();
  }

  @Test
  void rejectsWrongCurrentPasswordAndMismatchedConfirmation() {
    assertThatThrownBy(
            () ->
                service.changePassword(
                    user.getEmail(),
                    passwordForm("wrong-password", "new-password123", "new-password123")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("現在のパスワードが正しくありません");

    assertThatThrownBy(
            () ->
                service.changePassword(
                    user.getEmail(),
                    passwordForm("password123", "new-password123", "different-password")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("確認用パスワードが一致しません");
  }

  @Test
  void marksOnlyOwnedNotificationsAsRead() {
    var own =
        notifications.save(new Notification(user, NotificationType.COMMENT, "コメントが届きました", 1L));
    var other =
        users.save(
            new User(
                "other-account@example.com", encoder.encode("password123"), "別ユーザー", Role.USER));
    var others =
        notifications.save(new Notification(other, NotificationType.COMMENT, "別ユーザー宛て", 2L));

    service.read(user.getEmail(), own.getId());

    assertThat(notifications.findById(own.getId()).orElseThrow().isReadFlag()).isTrue();
    assertThatThrownBy(() -> service.read(user.getEmail(), others.getId()))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  private PasswordChangeForm passwordForm(String current, String next, String confirmation) {
    var form = new PasswordChangeForm();
    form.setCurrentPassword(current);
    form.setNewPassword(next);
    form.setConfirmation(confirmation);
    return form;
  }
}
