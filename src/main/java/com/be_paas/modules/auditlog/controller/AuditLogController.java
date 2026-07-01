package com.be_paas.modules.auditlog.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.dto.AuditLogResponse;
import com.be_paas.modules.auditlog.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping({"", "/"})
    public PageResponse<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false, name = "user") String search,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return auditLogService.getAuditLogs(search, actionType, fromDate, toDate, page, size);
    }

}
