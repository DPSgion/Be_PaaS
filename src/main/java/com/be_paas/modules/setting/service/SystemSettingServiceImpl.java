package com.be_paas.modules.setting.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.setting.dto.SettingResponse;
import com.be_paas.modules.setting.entity.SystemSetting;
import com.be_paas.modules.setting.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    // BỘ NHỚ ĐỆM (CACHE) TRÊN RAM - TỐC ĐỘ O(1)
    private final Map<String, String> settingCache = new ConcurrentHashMap<>();

    /**
     * Tự động chạy ngay khi Spring Boot vừa khởi động xong.
     * Nạp toàn bộ cấu hình từ Database lên RAM.
     */
    @PostConstruct
    public void loadSettingsToCache() {
        log.info("⚙️ Bắt đầu nạp System Settings vào Cache...");
        List<SystemSetting> settings = systemSettingRepository.findAll();
        for (SystemSetting setting : settings) {
            settingCache.put(setting.getSettingKey(), setting.getSettingValue());
        }
        log.info("✅ Đã nạp thành công {} cấu hình hệ thống vào RAM.", settingCache.size());
    }

    /**
     * Hàm gọi xuyên suốt dự án. Chỉ đọc từ RAM, KHÔNG query DB.
     */
    @Override
    public String getValue(String key) {
        String value = settingCache.get(key);
        if (value == null) {
            log.warn("⚠️ Không tìm thấy cấu hình cho key: {}", key);
            return ""; // Tránh NullPointerException cho các module khác
        }
        return value;
    }

    @Override
    public List<SettingResponse> getAllSettings() {
        return systemSettingRepository.findAll().stream()
                .map(setting -> SettingResponse.builder()
                        .settingKey(setting.getSettingKey())
                        .settingValue(setting.getSettingValue())
                        .dataType(setting.getDataType())
                        .description(setting.getDescription())
                        .updatedAt(setting.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void updateSetting(String key, String newValue) {
        // 1. Cập nhật dưới Database
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy mã cấu hình: " + key));

        setting.setSettingValue(newValue);
        systemSettingRepository.save(setting);

        // 2. Ghi đè vào Cache trên RAM ngay lập tức
        settingCache.put(key, newValue);
        log.info("🔄 Đã cập nhật System Setting [{}] = {}", key, newValue);
    }
}