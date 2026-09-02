package com.exradar.service;

import com.exradar.dto.AdminReportDto;
import com.exradar.dto.AdminReportDto.TargetInfo;
import com.exradar.entity.Report;
import com.exradar.entity.ReportStatus;
import com.exradar.entity.ReportTargetType;
import com.exradar.entity.Role;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.CommentRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.ReportRepository;
import com.exradar.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通報の管理者対応フロー。「通報される」だけで終わらせず、管理者が一覧で確認し、
 * ステータス変更・非公開対応・削除対応まで行えるようにする。
 * 投稿・コメントの実際の非公開化/削除は、既存のExperiencePostService/InteractionServiceを
 * 再利用する(通報専用の重複ロジックは作らない)。
 */
@Service
public class AdminReportService {
  private final ReportRepository reports;
  private final ExperiencePostRepository posts;
  private final CommentRepository comments;
  private final UserRepository users;
  private final ExperiencePostService postService;
  private final InteractionService interactionService;

  public AdminReportService(
      ReportRepository reports,
      ExperiencePostRepository posts,
      CommentRepository comments,
      UserRepository users,
      ExperiencePostService postService,
      InteractionService interactionService) {
    this.reports = reports;
    this.posts = posts;
    this.comments = comments;
    this.users = users;
    this.postService = postService;
    this.interactionService = interactionService;
  }

  @Transactional(readOnly = true)
  public List<AdminReportDto> list() {
    return reports.findAllByOrderByCreatedAtDesc().stream()
        .map(r -> AdminReportDto.pending(r, resolveTarget(r)))
        .toList();
  }

  private TargetInfo resolveTarget(Report r) {
    if (r.getTargetType() == ReportTargetType.EXPERIENCE_POST) {
      return posts
          .findById(r.getTargetId())
          .map(
              p ->
                  new TargetInfo(
                      true, p.getTitle(), p.getAuthor().getDisplayName(), p.getId()))
          .orElseGet(TargetInfo::missing);
    }
    return comments
        .findById(r.getTargetId())
        .map(
            c ->
                new TargetInfo(
                    true,
                    SeoText.excerpt(c.getBody(), 80),
                    c.getAuthor().getDisplayName(),
                    c.getPost().getId()))
        .orElseGet(TargetInfo::missing);
  }

  /** ステータスのみを変更する(未対応・確認中・問題なし)。非公開対応・削除対応は専用メソッドを使う。 */
  @Transactional
  public void changeStatus(Long reportId, ReportStatus newStatus, String adminEmail) {
    requireAdmin(adminEmail);
    if (newStatus == ReportStatus.HIDDEN || newStatus == ReportStatus.DELETED) {
      throw new IllegalArgumentException("非公開対応・削除対応は専用の操作から行ってください");
    }
    find(reportId).changeStatus(newStatus);
  }

  /** 通報対象が体験談の場合のみ有効。既存のDRAFT/PUBLISHED状態をそのまま使って非公開に戻す。 */
  @Transactional
  public void hide(Long reportId, String adminEmail) {
    requireAdmin(adminEmail);
    var report = find(reportId);
    if (report.getTargetType() != ReportTargetType.EXPERIENCE_POST) {
      throw new IllegalArgumentException("非公開対応は体験談の通報にのみ行えます");
    }
    postService.hideByModeration(report.getTargetId(), adminEmail);
    report.changeStatus(ReportStatus.HIDDEN);
  }

  /** 通報対象を削除する(体験談は物理削除、コメントも物理削除。いずれも既存のService実装を再利用)。 */
  @Transactional
  public void delete(Long reportId, String adminEmail) {
    requireAdmin(adminEmail);
    var report = find(reportId);
    switch (report.getTargetType()) {
      case EXPERIENCE_POST -> postService.delete(report.getTargetId(), adminEmail);
      case COMMENT -> {
        var comment =
            comments
                .findById(report.getTargetId())
                .orElseThrow(() -> new ResourceNotFoundException("コメントが見つかりません"));
        interactionService.deleteComment(comment.getPost().getId(), comment.getId(), adminEmail);
      }
    }
    report.changeStatus(ReportStatus.DELETED);
  }

  /**
   * Spring Securityの/admin/**保護に加え、Service層でも管理者であることを確認する
   * (Controller/Serviceでも権限制御するという要件への多層防御)。
   */
  private void requireAdmin(String email) {
    var user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ForbiddenOperationException("ログインが必要です"));
    if (user.getRole() != Role.ADMIN) throw new ForbiddenOperationException("この操作には管理者権限が必要です");
  }

  private Report find(Long id) {
    if (id == null || id < 1) throw new ResourceNotFoundException("通報が見つかりません");
    return reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("通報が見つかりません"));
  }
}
