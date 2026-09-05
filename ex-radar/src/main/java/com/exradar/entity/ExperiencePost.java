package com.exradar.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "experience_posts")
public class ExperiencePost extends BaseEntity {
  /**
   * 教訓(lesson)を「有効」とみなす最小文字数。投稿フォームのBean Validation
   * (ExperiencePostForm)・投稿保存時のService層チェック(ExperiencePostService)・
   * 報酬対象投稿数の判定(ExperiencePostRepository/RewardService)の3箇所で
   * 条件がずれないよう、ここを唯一の定義箇所とする。
   */
  public static final int LESSON_MIN_LENGTH = 10;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User author;

  // 下書きはカテゴリ未選択のまま自動保存されうるため、公開済みと異なりnullを許容する。
  @ManyToOne(fetch = FetchType.LAZY)
  private Category category;

  @Column(nullable = false, length = 150)
  private String title;

  private Integer ageAtChoice;

  @Column(length = 100)
  private String statusAtChoice;

  @Column(length = 20)
  private String currentAgeGroup;

  private Integer yearsElapsed;

  @Column(nullable = false, length = 3000)
  private String situationBefore;

  @Column(nullable = false, length = 3000)
  private String worries;

  @Column(nullable = false, length = 3000)
  private String alternatives;

  @Column(nullable = false, length = 3000)
  private String choiceMade;

  @Column(nullable = false, length = 3000)
  private String reason;

  @Column(nullable = false, length = 5000)
  private String outcome;

  @Column(nullable = false, length = 3000)
  private String goodThings;

  @Column(nullable = false, length = 3000)
  private String difficulties;

  @Column(nullable = false, length = 3000)
  private String unexpectedThings;

  @Column(length = 3000)
  private String decisionCriteria;

  @Column(length = 5000)
  private String learned;

  @Column(length = 3000)
  private String wishKnown;

  @Column(length = 3000)
  private String unexpectedlyOkay;

  @Column(length = 3000)
  private String preparationHelped;

  @Column(length = 3000)
  private String missedRegret;

  @Column(length = 3000)
  private String lesson;

  @Column(length = 3000)
  private String suitableFor;

  @Column(length = 3000)
  private String cautionFor;

  // 下書きは評価未入力のまま保存されうるためnull許容(CHECK(satisfaction BETWEEN 1 AND 10)は
  // SQLの三値論理によりnullではUNKNOWNとなり違反にならない)。公開済みは常に非null。
  private Integer satisfaction;

  private Integer regret;

  @Column(nullable = false)
  private boolean chooseAgain;

  @Column(nullable = false, length = 3000)
  private String adviceToPastSelf;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PostStatus status = PostStatus.DRAFT;

  // 投稿ボタン連打・通信再送による二重投稿の防止用(フォーム表示時にサーバー側で発行するUUID)。
  // 一度設定したら変更しない。同じ値の投稿は1回しか受理しない(DB UNIQUE制約でも担保)。
  @Column(name = "submission_token")
  private String submissionToken;

  // 主要な自由記述項目を正規化・連結してSHA-256でハッシュ化したもの。同一ユーザーの
  // 過去投稿との完全一致検出を高速化するために保持する(DuplicatePostDetectionService参照)。
  @Column(name = "content_fingerprint")
  private String contentFingerprint;

  @ManyToMany
  @JoinTable(
      name = "experience_post_tags",
      joinColumns = @JoinColumn(name = "post_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<Tag> tags = new LinkedHashSet<>();

  @ManyToMany
  @JoinTable(
      name = "experience_post_values",
      joinColumns = @JoinColumn(name = "post_id"),
      inverseJoinColumns = @JoinColumn(name = "value_id"))
  private Set<PersonalValue> values = new LinkedHashSet<>();

  @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("displayOrder asc")
  private List<LifeEvent> lifeEvents = new ArrayList<>();

  protected ExperiencePost() {}

  public ExperiencePost(User author) {
    this.author = author;
  }

  /**
   * 本文フィールドのみを更新する。公開状態(status)はここでは一切変更しない。
   * 下書き保存(自動保存含む)がこのメソッドを使うことで、公開済み投稿が
   * 遅延到着したリクエストによって誤ってDRAFTへ戻ることは構造的に起こりえない。
   */
  public void updateContent(
      Category category,
      String title,
      Integer ageAtChoice,
      String statusAtChoice,
      String currentAgeGroup,
      Integer yearsElapsed,
      String situationBefore,
      String worries,
      String alternatives,
      String choiceMade,
      String reason,
      String outcome,
      String goodThings,
      String difficulties,
      String unexpectedThings,
      Integer satisfaction,
      Integer regret,
      boolean chooseAgain,
      String adviceToPastSelf) {
    this.category = category;
    this.title = title;
    this.ageAtChoice = ageAtChoice;
    this.statusAtChoice = statusAtChoice;
    this.currentAgeGroup = currentAgeGroup;
    this.yearsElapsed = yearsElapsed;
    this.situationBefore = situationBefore;
    this.worries = worries;
    this.alternatives = alternatives;
    this.choiceMade = choiceMade;
    this.reason = reason;
    this.outcome = outcome;
    this.goodThings = goodThings;
    this.difficulties = difficulties;
    this.unexpectedThings = unexpectedThings;
    this.satisfaction = satisfaction;
    this.regret = regret;
    this.chooseAgain = chooseAgain;
    this.adviceToPastSelf = adviceToPastSelf;
  }

  public void publish() {
    this.status = PostStatus.PUBLISHED;
  }

  /** 投稿作成時に一度だけ設定する(以後は変更しない)。二重送信検出のキーとして使う。 */
  public void assignSubmissionToken(String token) {
    if (this.submissionToken == null) this.submissionToken = token;
  }

  /** 本文を正規化・連結してハッシュ化した値を保存する(内容が変わるたびにapplyContent経由で更新される)。 */
  public void assignContentFingerprint(String fingerprint) {
    this.contentFingerprint = fingerprint;
  }

  public String getSubmissionToken() {
    return submissionToken;
  }

  public String getContentFingerprint() {
    return contentFingerprint;
  }

  /**
   * 管理者による通報対応「非公開対応」専用。既存のDRAFT/PUBLISHEDの仕組みをそのまま再利用し、
   * 投稿者の下書きへ戻す(削除はしない)。通常のユーザー操作(自動保存等)からは
   * 呼び出されない、管理者専用の状態遷移。
   */
  public void hideByModeration() {
    this.status = PostStatus.DRAFT;
  }

  public void replaceLifeEvents(List<LifeEvent> events) {
    lifeEvents.clear();
    for (var event : events) {
      event.attachTo(this);
      lifeEvents.add(event);
    }
  }

  public void replaceTags(Collection<Tag> values) {
    tags.clear();
    tags.addAll(values);
  }

  public void updateWisdom(
      String decisionCriteria,
      String learned,
      String wishKnown,
      String unexpectedlyOkay,
      String preparationHelped,
      String missedRegret,
      String lesson,
      String suitableFor,
      String cautionFor,
      Collection<PersonalValue> values) {
    this.decisionCriteria = decisionCriteria;
    this.learned = learned;
    this.wishKnown = wishKnown;
    this.unexpectedlyOkay = unexpectedlyOkay;
    this.preparationHelped = preparationHelped;
    this.missedRegret = missedRegret;
    this.lesson = lesson;
    this.suitableFor = suitableFor;
    this.cautionFor = cautionFor;
    this.values.clear();
    this.values.addAll(values);
  }

  public User getAuthor() {
    return author;
  }

  public Category getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public Integer getAgeAtChoice() {
    return ageAtChoice;
  }

  public String getStatusAtChoice() {
    return statusAtChoice;
  }

  public String getCurrentAgeGroup() {
    return currentAgeGroup;
  }

  public Integer getYearsElapsed() {
    return yearsElapsed;
  }

  public String getSituationBefore() {
    return situationBefore;
  }

  public String getWorries() {
    return worries;
  }

  public String getAlternatives() {
    return alternatives;
  }

  public String getChoiceMade() {
    return choiceMade;
  }

  public String getReason() {
    return reason;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getGoodThings() {
    return goodThings;
  }

  public String getDifficulties() {
    return difficulties;
  }

  public String getUnexpectedThings() {
    return unexpectedThings;
  }

  public String getDecisionCriteria() {
    return decisionCriteria;
  }

  public String getLearned() {
    return learned;
  }

  public String getWishKnown() {
    return wishKnown;
  }

  public String getUnexpectedlyOkay() {
    return unexpectedlyOkay;
  }

  public String getPreparationHelped() {
    return preparationHelped;
  }

  public String getMissedRegret() {
    return missedRegret;
  }

  public String getLesson() {
    return lesson;
  }

  public String getSuitableFor() {
    return suitableFor;
  }

  public String getCautionFor() {
    return cautionFor;
  }

  public Set<PersonalValue> getValues() {
    return values;
  }

  public Integer getSatisfaction() {
    return satisfaction;
  }

  public Integer getRegret() {
    return regret;
  }

  public boolean isChooseAgain() {
    return chooseAgain;
  }

  public String getAdviceToPastSelf() {
    return adviceToPastSelf;
  }

  public PostStatus getStatus() {
    return status;
  }

  public boolean isPublished() {
    return status == PostStatus.PUBLISHED;
  }

  public boolean isDraft() {
    return status == PostStatus.DRAFT;
  }

  public Set<Tag> getTags() {
    return tags;
  }

  public List<LifeEvent> getLifeEvents() {
    return lifeEvents;
  }
}
