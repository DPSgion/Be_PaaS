package com.be_paas.modules.auditlog.repository;

import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

    @Query("SELECT al FROM AuditLog al LEFT JOIN User u ON al.implementerId = u.id WHERE " +
            "(:term IS NULL OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))) " +
            "AND (:actionType IS NULL OR al.actionType = :actionType) " +
            "AND (:fromDate IS NULL OR al.createdAt >= :fromDate) " +
            "AND (:toDate IS NULL OR al.createdAt <= :toDate)")
    Page<AuditLog> searchAuditLogsWithFilter(
            @Param("term") String term,
            @Param("actionType") ActionType actionType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

}
