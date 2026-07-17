//package com.be_paas.modules.webhook.service;
//
//import com.be_paas.core.exception.BusinessException;
//import com.be_paas.modules.deployment.service.DeploymentService;
//import com.be_paas.modules.project.entity.Project;
//import com.be_paas.modules.project.repository.ProjectRepository;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class WebhookServiceImpl implements WebhookService {
//
//    private final ProjectRepository projectRepository;
//    private final DeploymentService deploymentService;
//    private final ObjectMapper objectMapper;
//
//    @Override
//    public void processGitHubPushEvent(Integer projectId, String signature, String rawPayload) {
//        // 1. Tìm dự án trong DB
//        Project project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy dự án"));
//
//        // 2. Kiểm tra công tắc Auto-Deploy
//        if (project.getIsAutoDeploy() == null || !project.getIsAutoDeploy()) {
//            log.info("Project {} đang tắt Auto Deploy. Bỏ qua Webhook.", projectId);
//            return;
//        }
//
//        // 3. Xác thực chữ ký điện tử (Signature Verification)
//        String webhookSecret = project.getWebhookSecret();
//        if (webhookSecret == null || webhookSecret.isEmpty()) {
//            log.error("Project {} chưa được cấu hình Webhook Secret.", projectId);
//            throw new BusinessException(500, "Cấu hình Webhook không hợp lệ");
//        }
//
//        if (!verifySignature(rawPayload, signature, webhookSecret)) {
//            log.warn("CẢNH BÁO BẢO MẬT: Chữ ký Webhook không khớp cho Project {}", projectId);
//            throw new BusinessException(403, "Chữ ký xác thực không hợp lệ (Invalid Signature)");
//        }
//
//        // 4. Bóc tách JSON an toàn
//        try {
//            JsonNode payloadNode = objectMapper.readTree(rawPayload);
//
//            // Lấy tên nhánh vừa bị tác động (Ví dụ: "refs/heads/main")
//            String ref = payloadNode.path("ref").asText();
//            String pushedBranch = ref.replace("refs/heads/", "");
//
//            // 5. Đối chiếu nhánh
//            if (!project.getBranch().equals(pushedBranch)) {
//                log.info("Sự kiện Push ở nhánh '{}'. Dự án đang cấu hình nhánh '{}'. Bỏ qua.", pushedBranch, project.getBranch());
//                return;
//            }
//
//            // 6. TẤT CẢ ĐỀU HỢP LỆ -> KÍCH HOẠT DEPLOY
//            log.info("Tín hiệu Webhook hoàn hảo. Bắt đầu kích hoạt Auto-Deploy cho Project {}", projectId);
//
//            // Gọi hàm deploy ngầm (không cần username vì hệ thống tự kích hoạt)
//            deploymentService.deployProject(projectId, project.getUser().getUsername());
//
//        } catch (Exception e) {
//            log.error("Lỗi khi xử lý payload từ GitHub: {}", e.getMessage());
//            throw new BusinessException(400, "Không thể đọc dữ liệu Webhook");
//        }
//    }
//
//    /**
//     * Thuật toán băm HMAC SHA-256 chuẩn của GitHub
//     */
//    private boolean verifySignature(String payload, String signatureHeader, String secret) {
//        try {
//            // Chữ ký GitHub gửi lên có tiền tố "sha256="
//            if (!signatureHeader.startsWith("sha256=")) {
//                return false;
//            }
//            String expectedSignature = signatureHeader.substring(7); // Cắt bỏ chữ "sha256="
//
//            // Khởi tạo thuật toán băm
//            Mac mac = Mac.getInstance("HmacSHA256");
//            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
//            mac.init(secretKeySpec);
//
//            // Tiến hành băm payload bằng secret key
//            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
//
//            // Chuyển kết quả băm (byte[]) thành chuỗi Hex (String) để so sánh
//            StringBuilder hexString = new StringBuilder();
//            for (byte b : hashBytes) {
//                String hex = Integer.toHexString(0xff & b);
//                if (hex.length() == 1) {
//                    hexString.append('0');
//                }
//                hexString.append(hex);
//            }
//            String calculatedSignature = hexString.toString();
//
//            // So sánh (Dùng equals để an toàn)
//            return calculatedSignature.equals(expectedSignature);
//
//        } catch (Exception e) {
//            log.error("Lỗi khi chạy thuật toán băm SHA-256: {}", e.getMessage());
//            return false;
//        }
//    }
//}