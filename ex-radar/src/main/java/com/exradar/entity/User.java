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

  public void completeDisplayNameSetup(String displayName) {
    this.displayName = displayName;
    this.displayNamePending = false;
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
