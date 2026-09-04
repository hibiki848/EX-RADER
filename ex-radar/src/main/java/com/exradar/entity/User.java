package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User extends BaseEntity {
  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false, length = 50)
  private String displayName;

  @Column(length = 20)
  private String ageGroup;

  @Column(length = 100)
  private String education;

  @Column(length = 100)
  private String occupation;

  @Column(length = 20)
  private String prefecture;

  @Column(length = 1000)
  private String biography;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role = Role.USER;

  @Column(nullable = false)
  private boolean suspended;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false, length = 20)
  private AuthProvider authProvider = AuthProvider.LOCAL;

  @Column(name = "provider_user_id", length = 255)
  private String providerUserId;

  @Column(name = "display_name_pending", nullable = false)
  private boolean displayNamePending;

  // 管理者アカウントや運営者が動作確認用に使う一般アカウントなど、実際の利用者ではない
  // アクセスをGA4・EXレーダー内部のアクセス解析の両方から除外するためのフラグ。
  // ROLE_ADMINは常に除外されるため、このフラグは主にADMIN以外のアカウント向け。
  @Column(name = "analytics_excluded", nullable = false)
  private boolean analyticsExcluded;

  // ログイン日時の記録は本カラム追加以降のログインからのみ始まる。既存ユーザーは
  // マイグレーション時点で両方ともNULL(過去のログイン日時は推測で埋めない)。
  @Column(name = "first_login_at")
  private LocalDateTime firstLoginAt;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  // 利用規約・プライバシーポリシーへの同意日時。本カラム追加以前に登録した既存ユーザーは
  // 同意日時を推測で埋めずNULLのままにする(新規登録時のみ、登録直後に記録する)。
  @Column(name = "terms_agreed_at")
  private LocalDateTime termsAgreedAt;

  @Column(name = "privacy_policy_agreed_at")
  private LocalDateTime privacyPolicyAgreedAt;

  // Google認証による「新規」ユーザー作成時(forGoogleSignup)だけtrueで作られるフラグ。
  // 通常登録は/registerのチェックボックスで登録と同時に同意させるため常にfalse。
  // 既存ユーザーはマイグレーション時点でDEFAULT FALSEのため、本カラム追加より前に
  // 作られたGoogleユーザーが強制同意の対象になることはない(termsAgreedAt==nullだけを
  // 理由に既存ユーザー全員へ強制しないため、この専用フラグで「新規」を明示的に区別する)。
  @Column(name = "terms_consent_pending", nullable = false)
  private boolean termsConsentPending;

  // 有料プランの概念自体が本カラム追加まで存在しなかったため、既存ユーザーは全員
  // FREE・関連日時は全てNULLから始まる。実際の加入・解約フローは別機能(未実装)が
  // changePlan(...)を呼び出すことを想定している。
  @Enumerated(EnumType.STRING)
  @Column(name = "current_plan", nullable = false, length = 20)
  private PlanType currentPlan = PlanType.FREE;

  @Column(name = "first_paid_at")
  private LocalDateTime firstPaidAt;

  @Column(name = "premium_period_started_at")
  private LocalDateTime premiumPeriodStartedAt;

  @Column(name = "premium_period_ended_at")
  private LocalDateTime premiumPeriodEndedAt;

  @ManyToMany
  @JoinTable(
      name = "user_values",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "value_id"))
  @OrderBy("displayOrder asc")
  private Set<PersonalValue> values = new LinkedHashSet<>();

  protected User() {}

  public User(String email, String password, String displayName, Role role) {
    this.email = email.toLowerCase();
    this.password = password;
    this.displayName = displayName;
    this.role = role;
  }

  /**
   * Googleアカウントでの初回ログイン時にユーザーを作成するための専用コンストラクタ。
   * Googleの氏名は公開表示名として使わないため、displayNameは仮の値を渡し、
   * displayNamePending=trueとして「表示名を設定してください」画面へ誘導する。
   * ログインパスワードは持たないため、他人が推測できないランダムなパスワードハッシュを設定する
   * (encodedRandomPasswordは呼び出し側でPasswordEncoderにより生成済みのものを渡す)。
   */
  public static User forGoogleSignup(
      String email, String providerUserId, String placeholderDisplayName, String encodedRandomPassword) {
    User user = new User(email, encodedRandomPassword, placeholderDisplayName, Role.USER);
    user.authProvider = AuthProvider.GOOGLE;
    user.providerUserId = providerUserId;
    user.displayNamePending = true;
    user.termsConsentPending = true;
    return user;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Role getRole() {
    return role;
  }

  public boolean isSuspended() {
    return suspended;
  }

  public AuthProvider getAuthProvider() {
    return authProvider;
  }

  public String getProviderUserId() {
    return providerUserId;
  }

  public boolean isDisplayNamePending() {
    return displayNamePending;
  }

  public boolean isAnalyticsExcluded() {
    return analyticsExcluded;
  }

  /** GA4・EXレーダー内部のアクセス解析、どちらでも計測対象から除外すべきかどうか。 */
  public boolean isExcludedFromAnalytics() {
    return role == Role.ADMIN || analyticsExcluded;
  }

  public void setAnalyticsExcluded(boolean analyticsExcluded) {
    this.analyticsExcluded = analyticsExcluded;
  }

  public LocalDateTime getFirstLoginAt() {
    return firstLoginAt;
  }

  public LocalDateTime getLastLoginAt() {
    return lastLoginAt;
  }

  /** ログイン成功のたびに呼ぶ。初回ログイン日時は最初の1回だけ設定され、以後は上書きしない。 */
  public void recordLogin(LocalDateTime at) {
    if (firstLoginAt == null) firstLoginAt = at;
    lastLoginAt = at;
  }

  public LocalDateTime getTermsAgreedAt() {
    return termsAgreedAt;
  }

  public LocalDateTime getPrivacyPolicyAgreedAt() {
    return privacyPolicyAgreedAt;
  }

  public boolean isTermsConsentPending() {
    return termsConsentPending;
  }

  /**
   * 利用規約・プライバシーポリシーへの同意を記録する。通常登録(/register)・Google新規登録後の
   * 同意画面(/auth/consent)の両方から共通で呼ばれる(同意日時更新ロジックの共通化)。
   * termsConsentPendingは元々falseの通常登録では単なる無害な代入になる。
   */
  public void agreeToTermsAndPrivacyPolicy(LocalDateTime at) {
    termsAgreedAt = at;
    privacyPolicyAgreedAt = at;
    termsConsentPending = false;
  }

  public PlanType getCurrentPlan() {
    return currentPlan;
  }

  public LocalDateTime getFirstPaidAt() {
    return firstPaidAt;
  }

  public LocalDateTime getPremiumPeriodStartedAt() {
    return premiumPeriodStartedAt;
  }

  public LocalDateTime getPremiumPeriodEndedAt() {
    return premiumPeriodEndedAt;
  }

  /**
   * プラン変更(FREE⇔PREMIUM)。初回有料加入日(firstPaidAt)は最初にPREMIUMへ変わった
   * ときだけ設定し、以後は上書きしない。PREMIUMへ変わるたびに「今回の加入期間」の
   * 開始日時を記録し、FREEへ戻るときに終了日時を記録する(加入期間の算出に使う)。
   * 実際の課金処理そのものはこのメソッドの外(未実装の別機能)が担う想定。
   */
  public void changePlan(PlanType newPlan, LocalDateTime at) {
    if (newPlan == currentPlan) return;
    if (newPlan == PlanType.PREMIUM) {
      if (firstPaidAt == null) firstPaidAt = at;
      premiumPeriodStartedAt = at;
      premiumPeriodEndedAt = null;
    } else {
      premiumPeriodEndedAt = at;
    }
    currentPlan = newPlan;
  }

  public void completeDisplayNameSetup(String displayName) {
    this.displayName = displayName;
    this.displayNamePending = false;
  }

  /**
   * 既存アカウント(通常はLOCAL)へGoogleアカウントを連携する。
   * authProviderは変更しない(元のログイン方法の情報として残す)ため、
   * 連携後もメールアドレス+パスワードでのログインは引き続き利用できる。
   */
  public void linkGoogleAccount(String providerUserId) {
    this.providerUserId = providerUserId;
  }

  public String getAgeGroup() {
    return ageGroup;
  }

  public String getEducation() {
    return education;
  }

  public String getOccupation() {
    return occupation;
  }

  public String getPrefecture() {
    return prefecture;
  }

  public String getBiography() {
    return biography;
  }

  public Set<PersonalValue> getValues() {
    return values;
  }

  public void replaceValues(Collection<PersonalValue> selected) {
    values.clear();
    values.addAll(selected);
  }

  public void setSuspended(boolean suspended) {
    this.suspended = suspended;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
  }

  public void updateProfile(
      String displayName,
      String ageGroup,
      String education,
      String occupation,
      String prefecture,
      String biography) {
    this.displayName = displayName;
    this.ageGroup = ageGroup;
    this.education = education;
    this.occupation = occupation;
    this.prefecture = prefecture;
    this.biography = biography;
  }
}
