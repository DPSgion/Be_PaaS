package com.be_paas.modules.setting.service;

import com.be_paas.modules.setting.dto.SettingResponse;

import java.util.List;

public interface SystemSettingService {
    String getValue(String key);
    List<SettingResponse> getAllSettings();
    void updateSetting(String key, String newValue);
}