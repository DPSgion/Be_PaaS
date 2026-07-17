//package com.be_paas.modules.webhook.controller;
//
//
//import com.be_paas.modules.webhook.service.WebhookService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@Slf4j
//@RestController
//@RequestMapping("/webhooks")
//@RequiredArgsConstructor
//public class WebhookController {
//
//    private final WebhookService webhookService;
//
//    // Endpoint này sẽ được dán vào cấu hình Webhook trên GitHub
//    @PostMapping("/github/{projectId}")
//    public ResponseEntity<String> handleGitHubWebhook(
//            @PathVariable Integer projectId,
//            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
//            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
//            @RequestBody String rawPayload // Hứng Raw String để giữ nguyên vẹn 100% data gốc
//    ) {
//        log.info("Nhận Webhook từ GitHub cho Project ID: {}", projectId);
//
//        // Chỉ quan tâm đến sự kiện push code
//        if (!"push".equals(eventType)) {
//            return ResponseEntity.ok("Ignored event type: " + eventType);
//        }
//
//        if (signature == null || signature.isEmpty()) {
//            return ResponseEntity.status(401).body("Missing X-Hub-Signature-256 header");
//        }
//
//        // Đẩy xuống Service xử lý
//        webhookService.processGitHubPushEvent(projectId, signature, rawPayload);
//
//        return ResponseEntity.ok("Webhook processed successfully");
//    }
//}