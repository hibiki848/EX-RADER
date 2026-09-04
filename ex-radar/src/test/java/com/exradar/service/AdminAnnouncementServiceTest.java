package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.AdminAnnouncementRecipientRepository;
import com.exradar.repository.AdminAnnouncementRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAnnouncementServiceTest {
  @Autowired AdminAnnouncementService service;
  @Autowired AdminAnnouncementRepository announcements;
  @Autowired AdminAnnouncementRecipientRepository recipients;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  private User save(String email, String name) {
    return users.save(new User(email, encoder.encode("password123"), name, Role.USER));
  }

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private AdminUserSearchCriteria criteriaWithEmailPrefix(String prefix) {
    return new AdminUserSearchCriteria(
        null, prefix, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private LocalDateTime past() {
    return LocalDateTime.now().minusDays(1);
  }

  private LocalDateTime future() {
    return LocalDateTime.now().plusDays(1);
  }

  // ---- 配信対象 ----

  @Test
  void createsForASingleUser() {
    var admin = admin("create-single-admin@example.com");
    var target = save("create-single-target@example.com", "対象者");

    var announcement = service.create("お知らせ", "本文です", null, past(), null, 0, admin.getEmail(), List.of(target.getId()));

    assertThat(recipients.countByAnnouncementId(announcement.getId())).isEqualTo(1);
    assertThat(announcement.isPublished()).isFalse();
  }

  @Test
  void createsForMultipleUsers() {
    var admin = admin("create-multi-admin@example.com");
    var a = save("create-multi-a@example.com", "A");
    var b = save("create-multi-b@example.com", "B");
    var c = save("create-multi-c@example.com", "C");

    var announcement =
        service.create("お知らせ", "本文です", null, past(), null, 0, admin.getEmail(), List.of(a.getId(), b.getId(), c.getId()));

    assertThat(recipients.countByAnnouncementId(announcement.getId())).isEqualTo(3);
  }

  @Test
  void createsForAllUsersMatchingSearchCriteriaByReusingAdminUserSearchService() {
    var admin = admin("create-criteria-admin@example.com");
    var matchA = save("create-criteria-match-a@example.com", "一致A");
    var matchB = save("create-criteria-match-b@example.com", "一致B");
    save("create-other-prefix-user@example.com", "対象外");

    var criteria = criteriaWithEmailPrefix("create-criteria-match-");
    var targetIds = service.resolveTargetIdsForCriteria(criteria);
    assertThat(targetIds).containsExactlyInAnyOrder(matchA.getId(), matchB.getId());

    var announcement = service.create("一致者へ", "本文です", null, past(), null, 0, admin.getEmail(), targetIds);

    assertThat(recipients.countByAnnouncementId(announcement.getId())).isEqualTo(2);
  }

  @Test
  void rejectsCreateWithZeroRecipients() {
    var admin = admin("create-empty-admin@example.com");

    assertThatThrownBy(() -> service.create("お知らせ", "本文です", null, past(), null, 0, admin.getEmail(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("配信先が0人のため作成できません");
    assertThat(announcements.count()).isZero();
  }

  @Test
  void rejectsEndsAtBeforeOrEqualToStartsAt() {
    var admin = admin("create-baddate-admin@example.com");
    var target = save("create-baddate-target@example.com", "対象者");
    var starts = LocalDateTime.now();

    assertThatThrownBy(
            () -> service.create("お知らせ", "本文です", null, starts, starts, 0, admin.getEmail(), List.of(target.getId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("配信終了日時は配信開始日時より後にしてください");
  }

  /** 送信対象は作成した瞬間に確定する。作成後にユーザーのプランが変わっても、既に確定した対象は変化しない。 */
  @Test
  void targetSetDoesNotChangeEvenIfUserAttributesChangeAfterCreation() {
    var admin = admin("target-fixed-admin@example.com");
    var free = save("fixed-target-free@example.com", "無料会員");
    var premium = save("fixed-target-premium@example.com", "有料会員");
    premium.changePlan(PlanType.PREMIUM, LocalDateTime.now());
    users.saveAndFlush(premium);

    var criteria =
        new AdminUserSearchCriteria(
            null, "fixed-target-", null, null, null, java.util.Set.of(PlanType.FREE), null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null);
    var targetIds = service.resolveTargetIdsForCriteria(criteria);
    assertThat(targetIds).containsExactly(free.getId());

    var announcement = service.create("無料会員へ", "本文です", null, past(), null, 0, admin.getEmail(), targetIds);
    assertThat(recipients.countByAnnouncementId(announcement.getId())).isEqualTo(1);

    free.changePlan(PlanType.PREMIUM, LocalDateTime.now());
    users.saveAndFlush(free);

    assertThat(recipients.countByAnnouncementId(announcement.getId())).isEqualTo(1);
  }

  // ---- 表示条件 ----

  @Test
  void unpublishedAnnouncementIsNotShown() {
    var admin = admin("unpublished-admin@example.com");
    var user = save("unpublished-user@example.com", "ユーザー");
    service.create("非公開お知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    // publish()を呼んでいないため非公開のまま

    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNull();
  }

  @Test
  void publishedButNotYetStartedIsNotShown() {
    var admin = admin("notstarted-admin@example.com");
    var user = save("notstarted-user@example.com", "ユーザー");
    var announcement = service.create("未来お知らせ", "本文", null, future(), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());

    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNull();
  }

  @Test
  void publishedWithinPeriodIsShown() {
    var admin = admin("active-admin@example.com");
    var user = save("active-user@example.com", "ユーザー");
    var announcement =
        service.create("配信中お知らせ", "本文", null, past(), future(), 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());

    var shown = service.pickAndRecordDisplayIfDue(user.getEmail());
    assertThat(shown).isNotNull();
    assertThat(shown.getAnnouncement().getId()).isEqualTo(announcement.getId());
  }

  @Test
  void publishedButAlreadyEndedIsNotShown() {
    var admin = admin("ended-admin@example.com");
    var user = save("ended-user@example.com", "ユーザー");
    var announcement =
        service.create("終了済みお知らせ", "本文", null, LocalDateTime.now().minusDays(2), past(), 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());

    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNull();
  }

  @Test
  void unpublishingAnActiveAnnouncementHidesIt() {
    var admin = admin("toggle-admin@example.com");
    var user = save("toggle-user@example.com", "ユーザー");
    var announcement = service.create("トグルお知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());
    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNotNull();

    service.unpublish(announcement.getId());
    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNull();
  }

  @Test
  void userNotInTargetListNeverSeesTheAnnouncement() {
    var admin = admin("outsider-admin@example.com");
    var target = save("outsider-target@example.com", "対象者");
    var outsider = save("outsider-outsider@example.com", "対象外");
    var announcement = service.create("限定お知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(target.getId()));
    service.publish(announcement.getId());

    assertThat(service.pickAndRecordDisplayIfDue(target.getEmail())).isNotNull();
    assertThat(service.pickAndRecordDisplayIfDue(outsider.getEmail())).isNull();
  }

  // ---- 永久非表示 ----

  @Test
  void dismissingPermanentlyStopsFurtherDisplayForThatUser() {
    var admin = admin("dismiss-admin@example.com");
    var user = save("dismiss-user@example.com", "ユーザー");
    var announcement = service.create("非表示お知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());
    var recipientId = service.pickAndRecordDisplayIfDue(user.getEmail()).getId();

    service.dismissPermanently(recipientId, user.getEmail());

    assertThat(service.pickAndRecordDisplayIfDue(user.getEmail())).isNull();
    assertThat(recipients.findById(recipientId).orElseThrow().isDismissedPermanently()).isTrue();
  }

  @Test
  void anotherUsersDismissedStateCannotBeChanged() {
    var admin = admin("idor-admin@example.com");
    var owner = save("idor-owner@example.com", "本人");
    var attacker = save("idor-attacker@example.com", "第三者");
    var announcement = service.create("本人限定お知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(owner.getId()));
    service.publish(announcement.getId());
    var recipientId = service.pickAndRecordDisplayIfDue(owner.getEmail()).getId();

    assertThatThrownBy(() -> service.dismissPermanently(recipientId, attacker.getEmail()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(recipients.findById(recipientId).orElseThrow().isDismissedPermanently()).isFalse();
  }

  // ---- 表示回数の記録 ----

  @Test
  void recordsFirstDisplayedAtLastDisplayedAtAndDisplayCount() {
    var admin = admin("record-admin@example.com");
    var user = save("record-user@example.com", "ユーザー");
    var announcement = service.create("記録お知らせ", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(announcement.getId());

    var first = service.pickAndRecordDisplayIfDue(user.getEmail());
    assertThat(first.getDisplayCount()).isEqualTo(1);
    assertThat(first.getFirstDisplayedAt()).isNotNull();
    assertThat(first.getLastDisplayedAt()).isEqualTo(first.getFirstDisplayedAt());

    // NavigationAdviceはセッション単位で1回だけ呼ぶ設計だが、サービス自体は複数回呼ばれても
    // 表示回数を正しく積み上げられることを確認する(次回ログイン時に再度呼ばれるケースに相当)。
    var second = service.pickAndRecordDisplayIfDue(user.getEmail());
    assertThat(second.getDisplayCount()).isEqualTo(2);
    assertThat(second.getFirstDisplayedAt()).isEqualTo(first.getFirstDisplayedAt());
    assertThat(second.getLastDisplayedAt()).isAfterOrEqualTo(first.getLastDisplayedAt());
  }

  // ---- 複数お知らせの優先順位 ----

  @Test
  void higherPriorityAnnouncementIsShownFirst() {
    var admin = admin("priority-admin@example.com");
    var user = save("priority-user@example.com", "ユーザー");
    var low = service.create("低優先度", "本文", null, past(), null, 1, admin.getEmail(), List.of(user.getId()));
    var high = service.create("高優先度", "本文", null, past(), null, 5, admin.getEmail(), List.of(user.getId()));
    service.publish(low.getId());
    service.publish(high.getId());

    var shown = service.pickAndRecordDisplayIfDue(user.getEmail());
    assertThat(shown.getAnnouncement().getId()).isEqualTo(high.getId());
  }

  @Test
  void whenPriorityTiesTheNewerAnnouncementIsShownFirst() {
    var admin = admin("priority-tie-admin@example.com");
    var user = save("priority-tie-user@example.com", "ユーザー");
    var older = service.create("古いお知らせ", "本文", null, LocalDateTime.now().minusDays(3), null, 0, admin.getEmail(), List.of(user.getId()));
    var newer = service.create("新しいお知らせ", "本文", null, LocalDateTime.now().minusDays(1), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(older.getId());
    service.publish(newer.getId());

    var shown = service.pickAndRecordDisplayIfDue(user.getEmail());
    assertThat(shown.getAnnouncement().getId()).isEqualTo(newer.getId());
  }

  @Test
  void onlyOneAnnouncementIsReturnedAtATimeEvenWithMultipleCandidates() {
    var admin = admin("single-pick-admin@example.com");
    var user = save("single-pick-user@example.com", "ユーザー");
    var a = service.create("お知らせA", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    var b = service.create("お知らせB", "本文", null, past(), null, 0, admin.getEmail(), List.of(user.getId()));
    service.publish(a.getId());
    service.publish(b.getId());

    assertThat(recipients.findDisplayCandidates(user.getId(), LocalDateTime.now(), PageRequest.of(0, 1))).hasSize(1);
  }
}
