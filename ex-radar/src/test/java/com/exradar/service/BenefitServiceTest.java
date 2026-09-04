package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.BenefitSourceType;
import com.exradar.entity.BenefitStatus;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.entity.UserBenefit;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.BenefitDefinitionRepository;
import com.exradar.repository.UserBenefitRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** UserBenefitの状態遷移・所有者確認(BenefitService)の検証。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BenefitServiceTest {
  @Autowired BenefitService benefitService;
  @Autowired UserBenefitRepository userBenefits;
  @Autowired BenefitDefinitionRepository benefitDefinitions;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  private User newUser(String email) {
    return users.save(new User(email, encoder.encode("password"), "テストユーザー", Role.USER));
  }

  private UserBenefit grant(User user) {
    var definition = benefitDefinitions.findByCode("DISCOUNT_50").orElseThrow();
    var benefit =
        new UserBenefit(
            user, definition, BenefitSourceType.POST_MILESTONE, null, "テスト付与", "TEST:" + user.getId() + ":" + System.nanoTime(), LocalDateTime.now());
    return userBenefits.save(benefit);
  }

  @Test
  void availableBenefitCanBeReserved() {
    var user = newUser("benefit-available@example.com");
    var benefit = grant(user);

    var reserved = benefitService.reserve(user.getId(), benefit.getId());

    assertThat(reserved.getStatus()).isEqualTo(BenefitStatus.RESERVED);
  }

  @Test
  void otherUsersBenefitCannotBeAccessed() {
    var owner = newUser("benefit-owner@example.com");
    var stranger = newUser("benefit-stranger@example.com");
    var benefit = grant(owner);

    assertThatThrownBy(() -> benefitService.reserve(stranger.getId(), benefit.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void usedBenefitCannotBeReservedAgain() {
    var user = newUser("benefit-used@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "sess_1", "sub_1");
    benefitService.markUsed(benefit.getId(), "in_1");

    assertThatThrownBy(() -> benefitService.reserve(user.getId(), benefit.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void revokedBenefitCannotBeReserved() {
    var user = newUser("benefit-revoked@example.com");
    var admin = users.save(new User("benefit-revoke-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var benefit = grant(user);
    benefitService.revokeAsAdmin(admin, benefit.getId());

    assertThatThrownBy(() -> benefitService.reserve(user.getId(), benefit.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void expiredBenefitCannotBeReserved() {
    var user = newUser("benefit-expired@example.com");
    var definition = benefitDefinitions.findByCode("DISCOUNT_50").orElseThrow();
    var benefit =
        new UserBenefit(
            user, definition, BenefitSourceType.POST_MILESTONE, null, "期限切れテスト用",
            "TEST_EXPIRED:" + user.getId(), LocalDateTime.now().minusDays(10));
    benefit = userBenefits.save(benefit);
    // BenefitDefinitionのexpiresDaysはNULL(無期限)のため、reserve()内での期限判定を
    // 直接検証するためにリフレクションでexpiresAtを過去日時へ強制的に書き換える。
    reflectivelySetExpiresAt(benefit, LocalDateTime.now().minusDays(1));

    var benefitId = benefit.getId();
    assertThatThrownBy(() -> benefitService.reserve(user.getId(), benefitId))
        .isInstanceOf(IllegalStateException.class);
    assertThat(userBenefits.findById(benefitId).orElseThrow().getStatus()).isEqualTo(BenefitStatus.EXPIRED);
  }

  @Test
  void availableBenefitCanBeRevokedByAdmin() {
    var user = newUser("benefit-revoke-available@example.com");
    var admin = users.save(new User("benefit-revoke-available-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var benefit = grant(user);

    benefitService.revokeAsAdmin(admin, benefit.getId());

    assertThat(userBenefits.findById(benefit.getId()).orElseThrow().getStatus()).isEqualTo(BenefitStatus.REVOKED);
  }

  @Test
  void reservedBenefitCanBeRevokedByAdmin() {
    var user = newUser("benefit-revoke-reserved@example.com");
    var admin = users.save(new User("benefit-revoke-reserved-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());

    benefitService.revokeAsAdmin(admin, benefit.getId());

    assertThat(userBenefits.findById(benefit.getId()).orElseThrow().getStatus()).isEqualTo(BenefitStatus.REVOKED);
  }

  /** APPLIEDは既にStripe側へ割引設定済みの可能性があるため、EXレーダーDBだけREVOKEDにすると不整合を招く。今回は管理者取消の対象外。 */
  @Test
  void appliedBenefitCannotBeRevokedByAdmin() {
    var user = newUser("benefit-revoke-applied@example.com");
    var admin = users.save(new User("benefit-revoke-applied-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "sess_applied", "sub_applied");

    var benefitId = benefit.getId();
    assertThatThrownBy(() -> benefitService.revokeAsAdmin(admin, benefitId))
        .isInstanceOf(IllegalStateException.class);
    assertThat(userBenefits.findById(benefitId).orElseThrow().getStatus()).isEqualTo(BenefitStatus.APPLIED);
  }

  @Test
  void nonAdminCannotRevokeABenefit() {
    var user = newUser("benefit-nonadmin-revoke@example.com");
    var benefit = grant(user);

    assertThatThrownBy(() -> benefitService.revokeAsAdmin(user, benefit.getId()))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  private void reflectivelySetExpiresAt(UserBenefit benefit, LocalDateTime at) {
    try {
      var field = UserBenefit.class.getDeclaredField("expiresAt");
      field.setAccessible(true);
      field.set(benefit, at);
      userBenefits.save(benefit);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
