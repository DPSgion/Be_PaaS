package com.be_paas.modules.project.service;

public interface OtpService {
    // Sinh mã OTP 6 số và lưu vào RAM (trả về mã OTP)
    String generateAndSaveOtp(String key);

    // Kiểm tra tính hợp lệ của OTP
    boolean validateOtp(String key, String otpCode);
}
