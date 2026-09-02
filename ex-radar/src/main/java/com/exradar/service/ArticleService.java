package com.exradar.service;

import com.exradar.entity.Article;
import com.exradar.entity.ArticleStatus;
import com.exradar.exception.DuplicateSlugException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.form.ArticleForm;
import com.exradar.repository.ArticleRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {
  private final ArticleRepository articles;

  public ArticleService(ArticleRepository articles) {
    this.articles = articles;
  }

  @Transactional(readOnly = true)
  public List<Article> publishedList() {
    return articles.findByStatusOrderByPublishedAtDesc(ArticleStatus.PUBLISHED);
  }

  /** ホームページの「新着記事」向け。公開済みの記事を新しい順に最大3件返す。 */
  @Transactional(readOnly = true)
  public List<Article> latest() {
    return articles.findTop3ByStatusOrderByPublishedAtDesc(ArticleStatus.PUBLISHED);
  }

  @Transactional(readOnly = true)
  public Article publishedBySlug(String slug) {
    return articles
        .findBySlugAndStatus(slug, ArticleStatus.PUBLISHED)
        .orElseThrow(() -> new ResourceNotFoundException("記事が見つかりません"));
  }

  @Transactional(readOnly = true)
  public List<Article> all() {
    return articles.findAllByOrderByUpdatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Article find(Long id) {
    return articles.findById(id).orElseThrow(() -> new ResourceNotFoundException("記事が見つかりません"));
  }

  @Transactional
  public Article create(ArticleForm form) {
    if (articles.existsBySlug(form.getSlug())) throw new DuplicateSlugException();
    var article =
        new Article(form.getTitle(), form.getSlug(), form.getDescription(), form.getContent());
    article.updateSeo(form.getSeoTitle(), form.getMetaDescription());
    return articles.save(article);
  }

  @Transactional
  public void update(Long id, ArticleForm form) {
    if (articles.existsBySlugAndIdNot(form.getSlug(), id)) throw new DuplicateSlugException();
    Article article = find(id);
    article.update(form.getTitle(), form.getSlug(), form.getDescription(), form.getContent());
    article.updateSeo(form.getSeoTitle(), form.getMetaDescription());
  }

  @Transactional
  public void publish(Long id) {
    find(id).publish();
  }

  @Transactional
  public void unpublish(Long id) {
    find(id).unpublish();
  }

  @Transactional
  public void delete(Long id) {
    articles.delete(find(id));
  }
}
