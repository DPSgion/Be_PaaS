package com.be_paas.modules.mail.service;

import java.util.Map;

public interface MailService {
    void sendHtmlMail(String to, String subject, String templateName, Map<String, Object> variables);
}
