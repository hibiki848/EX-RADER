package com.exradar.repository;

import com.exradar.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

  long countByRecipientIdAndReadFlagFalse(Long recipientId);

  void deleteByRecipientId(Long recipientId);
}
