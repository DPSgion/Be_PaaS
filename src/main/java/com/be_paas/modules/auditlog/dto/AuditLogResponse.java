package com.be_paas.modules.auditlog.dto;


import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record AuditLogResponse(

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp,

        String actor,
        String actionType,
        String target,
        String description

) {
}
