package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Category;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * (author_id, content_fingerprint)へのDB UNIQUE制約(V22)の検証。異なるsubmissionTokenで
 * 送信された完全同一内容の投稿が、事前チェック(DuplicatePostDetectionService)の
 * すり抜け(TOCTOUレース)によって二重にDBへ保存されないこと、その際に500エラーではなく
 * 通常の重複投稿エラーとして扱われることを確認する。
 *
 * 実際の同時実行(データベース行レベルのレース)を検証する必要があるため、
 * このクラスはあえて@Transactionalを付けない(テストごとのロールバックに頼ると、
 * 別スレッドの独立したトランザクションからセットアップ済みデータが見えなくなるため)。
 * その代わり、各テストで作成したユーザー・カテゴリ・投稿はremoveFixtures()で確実に
 * 後片付けし、他のテストクラス(InsightServiceTest等、グローバルな集計を検証するテスト)を
 * 汚染しないようにする。
 */
@SpringBootTest
@ActiveProfiles("test")
class ExperiencePostFingerprintUniquenessTest {
  @Autowired ExperiencePostService postService;
  @Autowired ExperiencePostRepository posts;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired PasswordEncoder encoder;

  private final List<Long> userIdsToClean = new ArrayList<>();
  private final List<Long> categoryIdsToClean = new ArrayList<>();

  @AfterEach
  void removeFixtures() {
    for (Long userId : userIdsToClean) {
      posts.deleteAll(posts.findByAuthorId(userId));
      users.findById(userId).ifPresent(users::delete);
    }
    for (Long categoryId : categoryIdsToClean) {
      categories.findById(categoryId).ifPresent(categories::delete);
    }
    userIdsToClean.clear();
    categoryIdsToClean.clear();
  }

  private User newAuthor(String label) {
    var user =
        users.save(
            new User(
                "fingerprint-test-" + label + "-" + UUID.randomUUID() + "@example.com",
                encoder.encode("password"),
                "投稿者",
                Role.USER));
    userIdsToClean.add(user.getId());
    return user;
  }

  private Category newCategory(String label) {
    var category = categories.save(new Category("転職", "fingerprint-test-" + label + "-" + UUID.randomUUID(), 1));
    categoryIdsToClean.add(category.getId());
    return category;
  }

  /** DBレベルのUNIQUE制約そのものの確認: 同一ユーザー+同一fingerprintの2件目はDataIntegrityViolationExceptionになる。 */
  @Test
  void savingTwoPublishedPostsWithSameAuthorAndFingerprintViolatesDbUniqueConstraint() {
    var author = newAuthor("same-author");
    var category = newCategory("same-author");
    String sharedFingerprint = "test-fixed-fingerprint-" + UUID.randomUUID();

    var first = legacyPublishedPost(author, category, "DB制約確認用の投稿1");
    first.assignContentFingerprint(sharedFingerprint);
    posts.saveAndFlush(first);

    var second = legacyPublishedPost(author, category, "DB制約確認用の投稿2");
    second.assignContentFingerprint(sharedFingerprint);

    assertThatThrownBy(() -> posts.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** 別ユーザーであれば、同一のfingerprintを持つPUBLISHED投稿を問題なく保存できる。 */
  @Test
  void differentAuthorsCanShareTheSamePublishedContentFingerprint() {
    var authorA = newAuthor("cross-author-a");
    var authorB = newAuthor("cross-author-b");
    var category = newCategory("cross-author");
    String sharedFingerprint = "test-fixed-fingerprint-" + UUID.randomUUID();

    var postA = legacyPublishedPost(authorA, category, "他人と同じ内容A");
    postA.assignContentFingerprint(sharedFingerprint);
    posts.saveAndFlush(postA);

    var postB = legacyPublishedPost(authorB, category, "他人と同じ内容B");
    postB.assignContentFingerprint(sharedFingerprint);

    assertThatCode(() -> posts.saveAndFlush(postB)).doesNotThrowAnyException();
  }

  /**
   * 異なるsubmissionTokenで完全同一内容を同時にcreate()した場合でも、DBには1件しか残らず、
   * どちらの結果も500(未処理の例外)にはならず、通常の重複投稿エラーと同じユーザー向け
   * メッセージになる(事前チェックで弾かれるか、DB UNIQUE制約のフォールバックで弾かれるかは
   * タイミング次第だが、いずれの経路でも観測される振る舞いは同じであることを確認する)。
   */
  @Test
  void identicalContentSubmittedWithDifferentTokensSimultaneouslyResultsInOnlyOneRow() throws Exception {
    var author = newAuthor("toctou-race");
    var category = newCategory("toctou-race");

    var barrier = new CyclicBarrier(2);
    Callable<Object> task = () -> {
      var f = validForm(category.getId(), "同時実行TOCTOU確認用の投稿");
      f.setSubmissionToken(UUID.randomUUID().toString());
      barrier.await(10, TimeUnit.SECONDS);
      try {
        return postService.create(f, author.getEmail());
      } catch (Exception e) {
        return e;
      }
    };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var future1 = executor.submit(task);
      var future2 = executor.submit(task);
      var result1 = future1.get(15, TimeUnit.SECONDS);
      var result2 = future2.get(15, TimeUnit.SECONDS);

      for (var result : List.of(result1, result2)) {
        if (result instanceof Exception e) {
          assertThat(e).isInstanceOf(IllegalArgumentException.class);
          assertThat(e.getMessage()).isEqualTo(DuplicatePostDetectionService.DUPLICATE_MESSAGE);
        }
      }
      assertThat(posts.findByAuthorIdAndStatus(author.getId(), PostStatus.PUBLISHED)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private ExperiencePost legacyPublishedPost(User author, Category category, String title) {
    var post = new ExperiencePost(author);
    post.updateContent(
        category, title, null, null, null, null,
        "状況", "悩み", "選択肢", "選んだこと", "理由", "結果", "良かったこと", "大変だったこと", "想定外だったこと",
        8, 2, false, "アドバイス");
    post.publish();
    return post;
  }

  private ExperiencePostForm validForm(Long categoryId, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle(title);
    f.setSituationBefore("状況の詳細です。" + title);
    f.setChoiceMade("選んだことです。" + title);
    f.setOutcome("結果です。" + title);
    f.setLesson("この経験から得た教訓です。" + title);
    f.setSatisfaction(8);
    f.setRegret(2);
    return f;
  }
}
