package com.be_paas.modules.notification.repository;

import com.be_paas.modules.notification.entity.Notification;
import com.be_paas.modules.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    Page<Notification> findByUserId(Integer userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Integer userId);

    // ==========================================
    // BỘ LỌC ĐỘNG (DYNAMIC FILTER)
    // ==========================================
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId " +
            "AND (:search IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:type IS NULL OR n.type = :type) " +
            "AND (:startDate IS NULL OR n.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR n.createdAt <= :endDate)")
    Page<Notification> searchNotifications(
            @Param("userId") Integer userId,
            @Param("search") String search,
            @Param("type") NotificationType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
