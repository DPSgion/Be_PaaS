package com.be_paas.modules.auditlog.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.dto.AuditLogResponse;
import com.be_paas.modules.auditlog.entity.ActionType;

public interface AuditLogService {

    PageResponse<AuditLogResponse> getAuditLogs(
            String term, String actionType, String fromDate, String toDate, int page, int size
    );

    public void logUserAction(Integer actorId, ActionType action, Integer targetUserId, String description);

    void logProjectAction(Integer actorId, ActionType action, Integer targetProjectId, String description);
}
