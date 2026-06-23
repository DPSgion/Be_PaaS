package com.be_paas.modules.auditlog.service;

import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.entity.AuditLog;
import com.be_paas.modules.auditlog.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService{

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void logUserAction(Integer actorId, ActionType action, Integer targetUserId, String description) {
        AuditLog log = AuditLog.builder()
                .implementerId(actorId)
                .actionType(action)
                .targetUser(targetUserId)
                .describe(description)
                .build();
        auditLogRepository.save(log);
    }
}
