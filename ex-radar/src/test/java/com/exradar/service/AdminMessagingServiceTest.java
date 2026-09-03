package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.AdminMessageRecipientRepository;
import com.exradar.repository.AdminMessageRepository;
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
class AdminMessagingServiceTest {
  @Autowired AdminMessagingService service;
  @Autowired AdminMessageRepository messages;
  @Autowired AdminMessageRecipientRepository recipients;
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

  // ---- 送信対象 ----

  @Test
  void sendsToASingleUser() {
    var admin = admin("send-single-admin@example.com");
    var target = save("send-single-target@example.com", "対象者");

    var message = service.send("お知らせ", "本文です", null, admin.getEmail(), List.of(target.getId()));

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(1);
    assertThat(recipients.findByMessageIdOrderByUserDisplayName(message.getId()))
        .extracting(r -> r.getUser().getId())
        .containsExactly(target.getId());
  }

  @Test
  void sendsToMultipleUsers() {
    var admin = admin("send-multi-admin@example.com");
    var a = save("send-multi-a@example.com", "A");
    var b = save("send-multi-b@example.com", "B");
    var c = save("send-multi-c@example.com", "C");

    var message = service.send("お知らせ", "本文です", null, admin.getEmail(), List.of(a.getId(), b.getId(), c.getId()));

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(3);
  }

  @Test
  void sendsToAllUsersMatchingSearchCriteriaByReusingAdminUserSearchService() {
    var admin = admin("send-criteria-admin@example.com");
    var matchA = save("send-criteria-match-a@example.com", "一致A");
    var matchB = save("send-criteria-match-b@example.com", "一致B");
    save("other-prefix-user@example.com", "対象外");

    var criteria = criteriaWithEmailPrefix("send-criteria-match-");
    var targetIds = service.resolveTargetIdsForCriteria(criteria);
    assertThat(targetIds).containsExactlyInAnyOrder(matchA.getId(), matchB.getId());

    var message = service.send("一致者へ", "本文です", null, admin.getEmail(), targetIds);

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(2);
  }

  @Test
  void rejectsSendWithZeroRecipients() {
    var admin = admin("send-empty-admin@example.com");

    assertThatThrownBy(() -> service.send("お知らせ", "本文です", null, admin.getEmail(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("送信先が0人のため送信できません");
    assertThat(messages.count()).isZero();
  }

  /**
   * 同一ユーザーIDが対象リストに重複して含まれていても(EXPLICITモードでの選択操作の
   * 取りこぼし等)、(message_id, user_id)のUNIQUE制約に反するRecipientが2件作られることはない。
   */
  @Test
  void doesNotCreateDuplicateRecipientRowsForTheSameUser() {
    var admin = admin("send-dup-admin@example.com");
    var target = save("send-dup-target@example.com", "対象者");

    var message = service.send("お知らせ", "本文です", null, admin.getEmail(), List.of(target.getId(), target.getId()));

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(1);
  }

  /**
   * 送信対象は送信した瞬間に確定する。送信後にユーザーのプラン(検索条件で使う属性)が
   * 変わっても、既に確定したRecipientの集合は変化しない(検索条件そのものを保存して
   * 後から再評価する設計にはしていないため)。
   */
  @Test
  void targetSetDoesNotChangeEvenIfUserAttributesChangeAfterSending() {
    var admin = admin("fixed-target-admin@example.com");
    var free = save("send-fixed-free@example.com", "無料会員");
    var premium = save("send-fixed-premium@example.com", "有料会員");
    premium.changePlan(PlanType.PREMIUM, LocalDateTime.now());
    users.saveAndFlush(premium);

    assertThat(free.getId()).isLessThan(premium.getId());
    assertThat(users.findById(free.getId()).orElseThrow().getCurrentPlan()).isEqualTo(PlanType.FREE);
    assertThat(users.findById(premium.getId()).orElseThrow().getCurrentPlan()).isEqualTo(PlanType.PREMIUM);

    var criteria =
        new AdminUserSearchCriteria(
            null, "send-fixed-", null, null, null, java.util.Set.of(PlanType.FREE), null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);
    var targetIds = service.resolveTargetIdsForCriteria(criteria);
    assertThat(targetIds).containsExactly(free.getId());

    var message = service.send("無料会員へ", "本文です", null, admin.getEmail(), targetIds);
    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(1);

    // 送信後にプランを変更しても、既に送ったメッセージの受信対象は変わらない
    free.changePlan(PlanType.PREMIUM, LocalDateTime.now());
    users.saveAndFlush(free);

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(1);
    assertThat(recipients.findByUserIdOrderByMessageSentAtDesc(free.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
        .extracting(r -> r.getMessage().getId())
        .containsExactly(message.getId());
  }

  // ---- ユーザー側の通知一覧・詳細 ----

  @Test
  void listForOnlyReturnsTheLoggedInUsersOwnMessages() {
    var admin = admin("list-admin@example.com");
    var owner = save("list-owner@example.com", "本人");
    var other = save("list-other@example.com", "他人");
    service.send("本人宛", "本文", null, admin.getEmail(), List.of(owner.getId()));
    service.send("他人宛", "本文", null, admin.getEmail(), List.of(other.getId()));

    var ownList = service.listFor(owner.getEmail(), 0);
    assertThat(ownList.getContent()).extracting(com.exradar.dto.UserMessageSummaryDto::title).containsExactly("本人宛");
  }

  @Test
  void anotherUsersNotificationDetailCannotBeOpened() {
    var admin = admin("idor-admin@example.com");
    var owner = save("idor-owner@example.com", "本人");
    var attacker = save("idor-attacker@example.com", "第三者");
    service.send("本人宛のみ", "本文", null, admin.getEmail(), List.of(owner.getId()));
    var recipientId = recipients.findByUserIdOrderByMessageSentAtDesc(owner.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0).getId();

    assertThatThrownBy(() -> service.open(recipientId, attacker.getEmail())).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void unreadCountReflectsOnlyUnreadMessagesForThatUser() {
    var admin = admin("unread-admin@example.com");
    var user = save("unread-user@example.com", "ユーザー");
    service.send("1通目", "本文", null, admin.getEmail(), List.of(user.getId()));
    service.send("2通目", "本文", null, admin.getEmail(), List.of(user.getId()));
    assertThat(service.unreadCount(user.getEmail())).isEqualTo(2);

    var firstRecipientId =
        recipients
            .findByUserIdOrderByMessageSentAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .stream()
            .filter(r -> r.getMessage().getTitle().equals("1通目"))
            .findFirst()
            .orElseThrow()
            .getId();
    service.open(firstRecipientId, user.getEmail());

    assertThat(service.unreadCount(user.getEmail())).isEqualTo(1);
  }

  @Test
  void openingANotificationMarksItReadIdempotently() {
    var admin = admin("read-admin@example.com");
    var user = save("read-user@example.com", "ユーザー");
    service.send("お知らせ", "本文", null, admin.getEmail(), List.of(user.getId()));
    var recipientId =
        recipients.findByUserIdOrderByMessageSentAtDesc(user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0).getId();

    var firstOpen = service.open(recipientId, user.getEmail());
    assertThat(firstOpen.isRead()).isTrue();
    var firstReadAt = firstOpen.getReadAt();

    // 同じ通知を再度開いても既読日時は変わらない(冪等)
    var secondOpen = service.open(recipientId, user.getEmail());
    assertThat(secondOpen.getReadAt()).isEqualTo(firstReadAt);
  }
}
