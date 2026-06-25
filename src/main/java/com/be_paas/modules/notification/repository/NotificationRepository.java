package com.be_paas.modules.notification.repository;

import com.be_paas.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    Page<Notification> findByUserId(Integer userId, Pageable pageable);
}
