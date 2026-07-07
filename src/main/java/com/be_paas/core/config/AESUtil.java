package com.be_paas.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class AESUtil {

    // Lấy secret key từ application.properties (Cần 1 chuỗi 16, 24 hoặc 32 ký tự)
    @Value("${app.security.aes-key:MySuperSecretKey123!}")
    private String secretKey;

    private static final String ALGORITHM = "AES";

    public String encrypt(String rawValue) {
        if (rawValue == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedValue = cipher.doFinal(rawValue.getBytes());
            return Base64.getEncoder().encodeToString(encryptedValue);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa biến môi trường", e);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decodedValue = Base64.getDecoder().decode(encryptedValue);
            byte[] decryptedValue = cipher.doFinal(decodedValue);
            return new String(decryptedValue);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi giải mã biến môi trường", e);
        }
    }
}
