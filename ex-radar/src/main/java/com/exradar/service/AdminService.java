package com.exradar.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;

@Service
public class AdminService {
  private final UserRepository users;
  private final ExperiencePostRepository posts;

  public AdminService(UserRepository users, ExperiencePostRepository posts) {
    this.users = users;
    this.posts = posts;
  }

  public long userCount() {
    return users.count();
  }

  public long adminCount() {
    return users.countByRole(Role.ADMIN);
  }

  public long suspendedUserCount() {
    return users.countBySuspendedTrue();
  }

  public long publishedPostCount() {
    return posts.countByStatus(com.exradar.entity.PostStatus.PUBLISHED);
  }

  public List<User> users() {
    return users.findAllByOrderByCreatedAtDesc();
  }

  public List<User> users(String filter) {
    return users().stream()
        .filter(user ->
            "admins".equals(filter) ? user.getRole() == Role.ADMIN
                : "suspended".equals(filter) ? user.isSuspended() : true)
        .toList();
  }

  @Transactional(readOnly = true)
  public User user(Long userId) {
    return find(userId);
  }

  @Transactional(readOnly = true)
  public List<com.exradar.entity.ExperiencePost> posts(Long userId) {
    return posts.findByAuthorIdOrderByCreatedAtDesc(userId);
  }

  @Transactional
  public void changeRole(String operatorEmail, Long userId, Role role) {
    User operator = find(operatorEmail);
    User target = find(userId);
    ensureNotSelf(operator, target);
    if (target.getRole() == Role.ADMIN && role != Role.ADMIN && adminCount() <= 1) {
      throw new IllegalArgumentException("最後の管理者の権限は変更できません");
    }
    target.changeRole(role);
  }

  @Transactional
  public void setSuspended(String operatorEmail, Long userId, boolean suspended) {
    User operator = find(operatorEmail);
    User target = find(userId);
    ensureNotSelf(operator, target);
    if (suspended && target.getRole() == Role.ADMIN && adminCount() <= 1) {
      throw new IllegalArgumentException("最後の管理者は停止できません");
    }
    target.setSuspended(suspended);
  }

  private User find(String email) {
    return users.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
  }

  private User find(Long id) {
    return users.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
  }

  private void ensureNotSelf(User operator, User target) {
    if (operator.getId().equals(target.getId())) {
      throw new IllegalArgumentException("自分自身の権限や利用停止状態は変更できません");
    }
  }
}
