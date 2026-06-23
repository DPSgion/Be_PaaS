package com.be_paas.modules.auditlog.service;

import com.be_paas.modules.auditlog.entity.ActionType;

public interface AuditLogService {

    public void logUserAction(Integer actorId, ActionType action, Integer targetUserId, String description);

}
