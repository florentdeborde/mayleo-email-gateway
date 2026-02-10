package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.model.EmailConfig;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MailSenderFactory {

    /**
     * Cache to store one JavaMailSender per client to avoid expensive object creation
     * and handshake overhead for every email sent.
     */
    private final Map<String, JavaMailSender> senderCache = new ConcurrentHashMap<>();

    public JavaMailSender getSender(String clientId, EmailConfig config) {
        // computeIfAbsent ensures thread-safety: only one sender is created per clientId
        return senderCache.computeIfAbsent(clientId, key -> {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

            switch (config.getProvider()) {
                case SMTP -> configureSmtp(mailSender, config);
                case GOOGLE -> configureGoogle(mailSender, config);
                case MICROSOFT -> configureMicrosoft(mailSender, config);
                default -> throw new IllegalArgumentException("Provider non supporté : " + config.getProvider());
            }

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", config.getSmtpTls());

            // Set timeouts to prevent the application from hanging if the SMTP server is unresponsive
            props.put("mail.smtp.connectiontimeout", "5000"); // 5s to establish connection
            props.put("mail.smtp.timeout", "5000");           // 5s to read data
            props.put("mail.smtp.writetimeout", "5000");      // 5s to send data

            return mailSender;
        });
    }

    private void configureSmtp(JavaMailSenderImpl mailSender, EmailConfig config) {
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort());
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword()); // Note: debug here to get encrypted smtp password
    }

    private void configureGoogle(JavaMailSenderImpl mailSender, EmailConfig config) {
        // TODO: Implémenter OAuth2 pour Gmail
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());
    }

    private void configureMicrosoft(JavaMailSenderImpl mailSender, EmailConfig config) {
        // TODO: Implémenter OAuth2 pour Outlook/Office365
        mailSender.setHost("smtp.office365.com");
        mailSender.setPort(587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());
    }

    public void invalidateSenderCache(String clientId) {
        senderCache.remove(clientId);
    }
}