package com.be_paas.modules.setting.controller;

import com.be_paas.modules.setting.dto.SettingResponse;
import com.be_paas.modules.setting.dto.SettingUpdateRequest;
import com.be_paas.modules.setting.service.SystemSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    // Chỉ Admin mới được xem và sửa
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
    @GetMapping
    public ResponseEntity<List<SettingResponse>> getAllSettings() {
        return ResponseEntity.ok(systemSettingService.getAllSettings());
    }

    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN')")
    @PutMapping("/{key}")
    public ResponseEntity<String> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody SettingUpdateRequest request) {

        systemSettingService.updateSetting(key, request.settingValue());
        return ResponseEntity.ok("Cập nhật cấu hình thành công");
    }
}