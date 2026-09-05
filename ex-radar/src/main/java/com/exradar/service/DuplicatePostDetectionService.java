package com.exradar.service;

import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.ExperiencePostRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「同一ユーザーの過去の投稿」との完全一致・実質同一投稿を検出する。他ユーザー同士の
 * 似た体験談は判定対象にしない(要件どおり)。外部AI APIは使わず、正規化した本文の
 * SHA-256ハッシュによる完全一致判定と、文字n-gramのJaccard類似度によるほぼ同一判定の
 * 2段構えにする。
 *
 * 正規化後の全文そのものはDBへ保存しない(既存の投稿本文カラムから毎回組み立てる)。
 * 保存するのはfingerprint(ハッシュ値)のみで、これは完全一致の高速な事前チェックと、
 * RewardService側の防御的な再確認に使う(ExperiencePost.contentFingerprint参照)。
 */
@Service
public class DuplicatePostDetectionService {
  /** 完全・実質同一と判断する類似度の閾値(90〜95%程度を目安に、やや厳しめの92%)。後から調整しやすいよう定数化する。 */
  static final double SIMILARITY_THRESHOLD = 0.92;

  /** 比較に使う文字n-gramの長さ。日本語の細かい言い回しの違いを拾えるよう3文字とする。 */
  static final int NGRAM_SIZE = 3;

  /** 正規化後の文字数がこれ未満の場合は類似度判定をスキップする(短文ほど誤判定(偽陽性)が増えるため)。完全一致判定は文字数に関わらず行う。 */
  static final int MIN_LENGTH_FOR_SIMILARITY_CHECK = 30;

  public static final String DUPLICATE_MESSAGE =
      "過去に投稿した内容と非常に似ています。別の体験談として投稿する場合は、具体的な状況・結果・教訓を追加してください。";

  private final ExperiencePostRepository posts;

  public DuplicatePostDetectionService(ExperiencePostRepository posts) {
    this.posts = posts;
  }

  /** フォームの主要な自由記述項目を正規化・連結する(比較・fingerprint計算の両方で使う共通の入力)。 */
  public String normalizedTextOf(ExperiencePostForm f) {
    return normalize(
        f.getTitle(), f.getSituationBefore(), f.getWorries(), f.getAlternatives(), f.getChoiceMade(),
        f.getReason(), f.getOutcome(), f.getGoodThings(), f.getDifficulties(), f.getUnexpectedThings(),
        f.getLearned(), f.getLesson(), f.getAdviceToPastSelf());
  }

  private String normalizedTextOf(ExperiencePost p) {
    return normalize(
        p.getTitle(), p.getSituationBefore(), p.getWorries(), p.getAlternatives(), p.getChoiceMade(),
        p.getReason(), p.getOutcome(), p.getGoodThings(), p.getDifficulties(), p.getUnexpectedThings(),
        p.getLearned(), p.getLesson(), p.getAdviceToPastSelf());
  }

  /**
   * Unicode正規化(全角半角統一等)・大文字小文字統一・改行統一・連続空白の統合・比較上意味の薄い
   * 記号の除去を行ったうえで連結する。空白・改行・記号だけを変えた投稿を実質同一として
   * 検出できるようにするための下処理。
   */
  private String normalize(String... fields) {
    var sb = new StringBuilder();
    for (var raw : fields) {
      if (raw == null || raw.isBlank()) continue;
      String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
      s = s.toLowerCase(Locale.ROOT);
      s = s.replace("\r\n", "\n").replace('\r', '\n');
      // 比較上意味の薄い記号(句読点・カギ括弧・中黒・長音等)を除去する。
      s = s.replaceAll("[\\p{Punct}、。・「」『』〜～…‥]", "");
      // 改行・タブ・全角スペースを含む空白類を単一の半角スペースへ統合する。
      s = s.replaceAll("[\\s\\u3000]+", " ").trim();
      if (!s.isEmpty()) {
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(s);
      }
    }
    return sb.toString();
  }

  public String fingerprint(String normalizedText) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(normalizedText.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256が利用できません", e);
    }
  }

  /**
   * 同一ユーザーの他のPUBLISHED投稿と完全一致・実質同一であればエラーメッセージを返す
   * (問題なければ空)。excludePostIdは編集中の投稿自身を比較対象から除くために使う。
   * 正規化後の本文が極端に短い(実質空に近い)場合は、他の必須項目バリデーションに
   * 判定を委ね、ここでは何もしない。
   */
  @Transactional(readOnly = true)
  public Optional<String> checkDuplicateOfOwnPosts(User author, ExperiencePostForm form, Long excludePostId) {
    String candidateNormalized = normalizedTextOf(form);
    if (candidateNormalized.isBlank()) return Optional.empty();
    String candidateFingerprint = fingerprint(candidateNormalized);
    boolean checkSimilarity = candidateNormalized.length() >= MIN_LENGTH_FOR_SIMILARITY_CHECK;
    Set<String> candidateGrams = checkSimilarity ? ngrams(candidateNormalized) : Set.of();

    for (var other : posts.findByAuthorIdAndStatus(author.getId(), PostStatus.PUBLISHED)) {
      if (excludePostId != null && excludePostId.equals(other.getId())) continue;
      if (candidateFingerprint.equals(other.getContentFingerprint())) return Optional.of(DUPLICATE_MESSAGE);
      if (!checkSimilarity) continue;
      String otherNormalized = normalizedTextOf(other);
      if (otherNormalized.length() < MIN_LENGTH_FOR_SIMILARITY_CHECK) continue;
      if (jaccard(candidateGrams, ngrams(otherNormalized)) >= SIMILARITY_THRESHOLD) {
        return Optional.of(DUPLICATE_MESSAGE);
      }
    }
    return Optional.empty();
  }

  private Set<String> ngrams(String text) {
    var result = new HashSet<String>();
    if (text.length() < NGRAM_SIZE) {
      if (!text.isEmpty()) result.add(text);
      return result;
    }
    for (int i = 0; i <= text.length() - NGRAM_SIZE; i++) result.add(text.substring(i, i + NGRAM_SIZE));
    return result;
  }

  private double jaccard(Set<String> a, Set<String> b) {
    if (a.isEmpty() && b.isEmpty()) return 0.0;
    var intersection = new HashSet<>(a);
    intersection.retainAll(b);
    var union = new HashSet<>(a);
    union.addAll(b);
    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
  }
}
