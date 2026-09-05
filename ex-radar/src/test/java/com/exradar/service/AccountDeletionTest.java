package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Category;
import com.exradar.entity.ExperienceRead;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.AdminAnnouncementRecipientRepository;
import com.exradar.repository.AdminAnnouncementRepository;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperienceReadRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountDeletionTest {
  @Autowired AccountService service;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostService postService;
  @Autowired ExperienceReadRepository reads;
  @Autowired AdminAnnouncementService announcementService;
  @Autowired AdminAnnouncementRepository announcements;
  @Autowired AdminAnnouncementRecipientRepository announcementRecipients;
  @Autowired PasswordEncoder encoder;

  @Test
  void rejectsDeletionWithWrongPassword() {
    var user = users.save(new User("delete@example.com", encoder.encode("password123"), "削除対象", Role.USER));

    assertThatThrownBy(() -> service.deleteAccount(user.getEmail(), "wrong-password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("現在のパスワードが正しくありません");
    assertThat(users.findById(user.getId())).isPresent();
  }

  @Test
  void deletesAccountWithCorrectPassword() {
    var user = users.save(new User("delete@example.com", encoder.encode("password123"), "削除対象", Role.USER));

    service.deleteAccount(user.getEmail(), "password123");

    assertThat(users.findByEmailIgnoreCase(user.getEmail())).isEmpty();
  }

  /**
   * 既読レコード(experience_reads)があっても、退会処理がFK制約違反で失敗しないこと。
   * 「読んだ側」の退会(reads.deleteByUserId)と「投稿者」の退会(所有する投稿に紐づく
   * 既読レコードをreads.deleteByPostIdで先に消してから投稿を削除)の両方を確認する。
   */
  @Test
  void deletingAccountCleansUpExperienceReadsForBothReaderAndAuthor() {
    var author = users.save(new User("read-cleanup-author@example.com", encoder.encode("password123"), "投稿者", Role.USER));
    var reader = users.save(new User("read-cleanup-reader@example.com", encoder.encode("password123"), "読者", Role.USER));
    var reader2 = users.save(new User("read-cleanup-reader2@example.com", encoder.encode("password123"), "読者2", Role.USER));
    var category = categories.save(new Category("削除テスト", "account-deletion-read-category", 1));
    var post = postService.create(validForm(category.getId()), author.getEmail());
    reads.save(new ExperienceRead(reader, post));
    reads.save(new ExperienceRead(reader2, post));
    assertThat(reads.count()).isEqualTo(2);

    // 読んだ側(reader)の退会: reader自身の既読レコードだけが消える。
    service.deleteAccount(reader.getEmail(), "password123");
    assertThat(reads.count()).isEqualTo(1);

    // 投稿者(author)の退会: authorの投稿に紐づく残りの既読レコード(reader2分)も
    // 投稿削除の前に片付けられ、FK制約違反にならない。
    service.deleteAccount(author.getEmail(), "password123");
    assertThat(reads.count()).isZero();
  }

  /** お知らせの対象者(受信側)が退会しても、FK制約違反にならず自分のRecipientレコードだけが消える。 */
  @Test
  void deletingRecipientAccountRemovesTheirAnnouncementRecipientRecordWithoutFkViolation() {
    var admin = users.save(new User("announcement-delete-admin@example.com", encoder.encode("password123"), "運営", Role.ADMIN));
    var target = users.save(new User("announcement-delete-target@example.com", encoder.encode("password123"), "対象者", Role.USER));
    var announcement =
        announcementService.create(
            "退会確認お知らせ", "本文", null, LocalDateTime.now().minusDays(1), null, 0, admin.getEmail(), List.of(target.getId()));
    assertThat(announcementRecipients.countByAnnouncementId(announcement.getId())).isEqualTo(1);

    service.deleteAccount(target.getEmail(), "password123");

    assertThat(announcementRecipients.countByAnnouncementId(announcement.getId())).isZero();
    assertThat(announcements.findById(announcement.getId())).isPresent();
  }

  /**
   * お知らせを作成した管理者が退会しても、お知らせ本体・他の対象者のRecipientレコードは
   * 削除されない(送信者情報だけがNULLになる)。
   */
  @Test
  void deletingCreatingAdminAccountClearsAttributionButKeepsAnnouncementAndOtherRecipients() {
    var admin = users.save(new User("announcement-admin-delete-admin@example.com", encoder.encode("password123"), "運営", Role.ADMIN));
    var target = users.save(new User("announcement-admin-delete-target@example.com", encoder.encode("password123"), "対象者", Role.USER));
    var announcement =
        announcementService.create(
            "作成者退会確認お知らせ", "本文", null, LocalDateTime.now().minusDays(1), null, 0, admin.getEmail(), List.of(target.getId()));

    service.deleteAccount(admin.getEmail(), "password123");

    var reloaded = announcements.findById(announcement.getId()).orElseThrow();
    assertThat(reloaded.getCreatedByAdmin()).isNull();
    assertThat(announcementRecipients.countByAnnouncementId(announcement.getId())).isEqualTo(1);
  }

  private com.exradar.form.ExperiencePostForm validForm(Long categoryId) {
    var f = new com.exradar.form.ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle("削除テスト用の体験談");
    f.setSituationBefore("状況");
    f.setWorries("悩み");
    f.setAlternatives("選択肢");
    f.setChoiceMade("選んだこと");
    f.setReason("理由");
    f.setOutcome("結果");
    f.setGoodThings("良かったこと");
    f.setDifficulties("大変だったこと");
    f.setUnexpectedThings("想定外だったこと");
    f.setLesson("削除テスト用の教訓の本文です");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    return f;
  }
}
