package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Category;
import com.exradar.entity.ExperienceRead;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperienceReadRepository;
import com.exradar.repository.UserRepository;
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
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    return f;
  }
}
