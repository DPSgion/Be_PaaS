package com.be_paas.modules.project.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpServiceImpl implements OtpService {

    // Lưu trữ trên RAM. Dùng ConcurrentHashMap để an toàn khi nhiều luồng truy cập cùng lúc.
    private final Map<String, OtpDetails> otpCache = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    // Thời gian sống của OTP (5 phút)
    private static final int OTP_VALID_DURATION_MINUTES = 5;

    // Record nội bộ để gom nhóm Dữ liệu OTP và Hạn sử dụng
    private record OtpDetails(String code, LocalDateTime expiryTime) {}

    @Override
    public String generateAndSaveOtp(String key) {
        // Sinh mã 6 số ngẫu nhiên chuẩn bảo mật
        int otpNum = 100000 + random.nextInt(900000);
        String otpCode = String.valueOf(otpNum);

        // Đặt hạn sử dụng
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(OTP_VALID_DURATION_MINUTES);

        // Lưu vào RAM
        otpCache.put(key, new OtpDetails(otpCode, expiryTime));

        // Dọn rác ngầm: Xóa các mã quá hạn cũ để giải phóng RAM
        cleanupExpiredOtps();

        return otpCode;
    }

    @Override
    public boolean validateOtp(String key, String otpCode) {
        OtpDetails details = otpCache.get(key);

        if (details == null) {
            return false; // Không tồn tại mã
        }

        if (LocalDateTime.now().isAfter(details.expiryTime())) {
            otpCache.remove(key); // Mã đã hết hạn -> Xóa luôn
            return false;
        }

        if (details.code().equals(otpCode)) {
            otpCache.remove(key); // Nhập đúng -> Hủy mã ngay lập tức để chống dùng lại (Replay Attack)
            return true;
        }

        return false; // Nhập sai
    }

    private void cleanupExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpCache.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiryTime()));
    }
}
