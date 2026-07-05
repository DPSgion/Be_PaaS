package com.be_paas.modules.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService{
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Async("mailTaskExecutor")
    @Override
    public void sendHtmlMail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true = cho phép gửi nội dung HTML và đính kèm file
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 1. Nạp dữ liệu (biến) vào Context của Thymeleaf
            Context context = new Context();
            context.setVariables(variables);

            // 2. Render file HTML + Dữ liệu thành chuỗi text HTML
            String htmlContent = templateEngine.process(templateName, context);

            // 3. Setup thông tin người nhận
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = xác nhận đây là HTML

            // 4. Bóp cò gửi đi
            mailSender.send(message);
            log.info("Đã gửi mail thành công đến: {}", to);

        } catch (MessagingException e) {
            log.error("Lỗi khi gửi mail HTML đến {}: {}", to, e.getMessage());
        }
    }
}
