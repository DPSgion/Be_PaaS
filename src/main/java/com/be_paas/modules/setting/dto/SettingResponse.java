package com.be_paas.modules.setting.dto;

import com.be_paas.modules.setting.entity.SettingDataType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SettingResponse(
        String settingKey,
        String settingValue,
        SettingDataType dataType,
        String description,
        LocalDateTime updatedAt
) {}