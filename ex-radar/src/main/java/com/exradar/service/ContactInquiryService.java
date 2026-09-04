package com.exradar.service;

import com.exradar.dto.ContactInquiryHistoryDto;
import com.exradar.entity.ContactCategory;
import com.exradar.entity.ContactInquiry;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.InquiryStatus;
import com.exradar.entity.User;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.ContactInquiryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * お問い合わせ(ContactInquiry)の受付・管理者対応。新しい問い合わせ返信システムは持たず、
 * ログインユーザーからの問い合わせへの対応は既存の運営メッセージ機能(AdminMessagingService)
 * への導線を提供するのみに留める(このクラス自身はメール送信・返信は行わない)。
 */
@Service
public class ContactInquiryService {
  public static final int LIST_PAGE_SIZE = 20;

  private final ContactInquiryRepository inquiries;
  private final UserRepository users;
  private final ExperiencePostRepository posts;

  public ContactInquiryService(
      ContactInquiryRepository inquiries, UserRepository users, ExperiencePostRepository posts) {
    this.inquiries = inquiries;
    this.users = users;
    this.posts = posts;
  }

  /**
   * 関連投稿IDの存在確認(コントローラーからの事前検証用)。存在しないIDを問い合わせに
   * 紐付けられないようにするための読み取り専用ヘルパー。
   */
  @Transactional(readOnly = true)
  public boolean postExists(Long postId) {
    return posts.existsById(postId);
  }

  @Transactional
  public ContactInquiry submit(
      String loggedInEmail,
      ContactCategory category,
      String name,
      String email,
      String subject,
      String body,
      Long relatedPostId) {
    User user = loggedInEmail == null ? null : users.findByEmailIgnoreCase(loggedInEmail).orElse(null);
    ExperiencePost relatedPost = relatedPostId == null ? null : posts.findById(relatedPostId).orElse(null);
    var inquiry =
        new ContactInquiry(
            user, category, blankToNull(name), email.trim(), subject.trim(), body.trim(), relatedPost);
    return inquiries.save(inquiry);
  }

  @Transactional(readOnly = true)
  public Page<ContactInquiryHistoryDto> history(InquiryStatus statusFilter, int page) {
    var pageable = PageRequest.of(Math.max(0, page), LIST_PAGE_SIZE);
    var result =
        statusFilter == null
            ? inquiries.findAllByOrderByCreatedAtDesc(pageable)
            : inquiries.findAllByStatusOrderByCreatedAtDesc(statusFilter, pageable);
    return result.map(
        i ->
            new ContactInquiryHistoryDto(
                i.getId(),
                i.getCategory(),
                i.getSubject(),
                i.getUser() != null ? i.getUser().getDisplayName() : null,
                i.getEmail(),
                i.getStatus(),
                i.getCreatedAt()));
  }

  @Transactional(readOnly = true)
  public long countByStatus(InquiryStatus status) {
    return inquiries.countByStatus(status);
  }

  @Transactional(readOnly = true)
  public ContactInquiry detail(Long id) {
    return inquiries.findByIdWithAssociations(id).orElseThrow(() -> new ResourceNotFoundException("お問い合わせが見つかりません"));
  }

  @Transactional
  public void changeStatus(Long id, InquiryStatus status) {
    find(id).changeStatus(status, LocalDateTime.now());
  }

  @Transactional
  public void updateAdminMemo(Long id, String memo) {
    find(id).updateAdminMemo(blankToNull(memo));
  }

  private ContactInquiry find(Long id) {
    return inquiries.findById(id).orElseThrow(() -> new ResourceNotFoundException("お問い合わせが見つかりません"));
  }

  private String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
