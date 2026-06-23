package com.be_paas.modules.auditlog.repository;

import com.be_paas.modules.auditlog.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {

}
