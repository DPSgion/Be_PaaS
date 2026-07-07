package com.be_paas.modules.project.dto;

public record EnvVarResponse(
        Integer id,
        String keyName,
        String value,
        Boolean isSecret
) {}