package com.be_paas.modules.setting.dto;

import jakarta.validation.constraints.NotBlank;

public record SettingUpdateRequest(
        @NotBlank(message = "Giá trị cài đặt không được để trống")
        String settingValue
) {}