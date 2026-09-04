package com.exradar.service;

import com.exradar.entity.BenefitSourceType;
import com.exradar.entity.Notification;
import com.exradar.entity.NotificationType;
import com.exradar.entity.PostRewardMilestone;
import com.exradar.entity.PostStatus;
import com.exradar.entity.User;
import com.exradar.entity.UserBenefit;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.NotificationRepository;
import com.exradar.repository.PostRewardMilestoneRepository;
import com.exradar.repository.UserBenefitRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「特典対象投稿数」に応じたプレミアム特典の自動付与を担う。特典対象投稿数は
 * ExperiencePostRepository.countByAuthorIdAndStatus(authorId, PUBLISHED)で判定する
 * (下書き・削除済み・管理者による非公開対応済みの投稿は、既存のPostStatus設計上すべて
 * PUBLISHED以外になるため、追加のフラグ管理なしに自動的に除外される)。
 *
 * 通常投稿は投稿完了時点(create/update経由でPUBLISHEDになった瞬間)で即座に特典対象として扱い、
 * 審査待ちにはしない。ただし後から管理者が不正投稿としてhideByModeration()した場合、
 * その投稿はカウントから外れる(以後の再評価には反映される)。既に付与済みの特典自体を
 * 自動で取り消す仕組みは持たず、管理者がBenefitServiceのrevoke経由で個別に取り消す
 * (要件どおり、自動取消ではなく「取り消せる構造」のみを用意する)。
 *
 * 二重配布防止はreward_grant_key(例: POST_COUNT:{userId}:5)のDB UNIQUE制約に委ねる。
 * このメソッドは何度呼ばれても安全(既に付与済みの閾値はexistsByRewardGrantKeyで
 * スキップされる)。
 */
@Service
public class RewardService {
  private final ExperiencePostRepository posts;
  private final PostRewardMilestoneRepository milestones;
  private final UserBenefitRepository userBenefits;
  private final NotificationRepository notifications;

  public RewardService(
      ExperiencePostRepository posts,
      PostRewardMilestoneRepository milestones,
      UserBenefitRepository userBenefits,
      NotificationRepository notifications) {
    this.posts = posts;
    this.milestones = milestones;
    this.userBenefits = userBenefits;
    this.notifications = notifications;
  }

  /** 投稿保存成功後に呼ぶ。新たに達成したマイルストームがあればUserBenefitを付与し、通知も作成する。 */
  @Transactional
  public List<UserBenefit> evaluateAndGrant(User user) {
    int count = eligiblePostCount(user.getId());
    var granted = new ArrayList<UserBenefit>();
    for (var milestone : milestones.findByActiveTrueOrderByRequiredPostCountAsc()) {
      for (int threshold : achievedThresholds(milestone, count)) {
        grantIfNew(user, milestone, threshold).ifPresent(granted::add);
      }
    }
    return granted;
  }

  @Transactional(readOnly = true)
  public int eligiblePostCount(Long userId) {
    return (int) posts.countByAuthorIdAndStatus(userId, PostStatus.PUBLISHED);
  }

  /** マイページの進捗表示用。次に到達する閾値と、それによって得られる特典名を返す。 */
  @Transactional(readOnly = true)
  public RewardProgress progressFor(User user) {
    int count = eligiblePostCount(user.getId());
    Integer nextThreshold = null;
    String nextBenefitName = null;
    for (var milestone : milestones.findByActiveTrueOrderByRequiredPostCountAsc()) {
      Integer candidate = nextThresholdFor(milestone, count);
      if (candidate == null) continue;
      if (nextThreshold == null || candidate < nextThreshold) {
        nextThreshold = candidate;
        nextBenefitName = milestone.getBenefitDefinition().getName();
      }
    }
    if (nextThreshold == null) return new RewardProgress(count, null, null, 0, 100);
    int remaining = nextThreshold - count;
    int percent = nextThreshold == 0 ? 0 : Math.min(100, count * 100 / nextThreshold);
    return new RewardProgress(count, nextThreshold, nextBenefitName, remaining, percent);
  }

  /** thresholdをこのマイルストームの繰り返しルールに沿って未来方向へ展開し、countを超えて初めて到達する値を返す(なければnull)。 */
  private Integer nextThresholdFor(PostRewardMilestone milestone, int count) {
    int required = milestone.getRequiredPostCount();
    Integer interval = milestone.getRepeatInterval();
    if (interval == null || interval <= 0) return required > count ? required : null;
    if (count < required) return required;
    int stepsPast = (count - required) / interval + 1;
    return required + stepsPast * interval;
  }

  /** countまでに到達済みの、このマイルストームの全閾値(繰り返しルールを含む)を返す。 */
  private List<Integer> achievedThresholds(PostRewardMilestone milestone, int count) {
    var thresholds = new ArrayList<Integer>();
    int required = milestone.getRequiredPostCount();
    if (count < required) return thresholds;
    Integer interval = milestone.getRepeatInterval();
    if (interval == null || interval <= 0) {
      thresholds.add(required);
      return thresholds;
    }
    for (int t = required; t <= count; t += interval) thresholds.add(t);
    return thresholds;
  }

  private java.util.Optional<UserBenefit> grantIfNew(User user, PostRewardMilestone milestone, int threshold) {
    String key = "POST_COUNT:" + user.getId() + ":" + threshold;
    if (userBenefits.existsByRewardGrantKey(key)) return java.util.Optional.empty();
    var benefit =
        new UserBenefit(
            user,
            milestone.getBenefitDefinition(),
            BenefitSourceType.POST_MILESTONE,
            null,
            "体験談" + threshold + "件投稿達成",
            key,
            LocalDateTime.now());
    userBenefits.save(benefit);
    notifications.save(
        new Notification(
            user,
            NotificationType.BENEFIT_EARNED,
            "体験談" + threshold + "件達成！「" + benefit.getBenefitNameSnapshot() + "」特典を獲得しました。",
            benefit.getId()));
    return java.util.Optional.of(benefit);
  }

  public record RewardProgress(
      int currentCount, Integer nextThreshold, String nextBenefitName, int postsRemaining, int progressPercent) {}
}
