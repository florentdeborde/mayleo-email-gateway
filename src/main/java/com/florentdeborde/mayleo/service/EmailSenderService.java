package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.exception.ExceptionCode;
import com.florentdeborde.mayleo.exception.MayleoException;
import com.florentdeborde.mayleo.metrics.MayleoMetrics;
import com.florentdeborde.mayleo.model.EmailConfig;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.repository.EmailConfigRepository;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class EmailSenderService {

    @Value("${app.mail.max-retries}")
    private int maxRetries;

    private final MailSenderFactory mailSenderFactory;
    private final EmailRequestRepository emailRequestRepository;
    private final EmailConfigRepository emailConfigRepository;
    private final MayleoMetrics metrics;

    private final Map<String, EmailConfig> configCache = new ConcurrentHashMap<>();

    public EmailSenderService(MailSenderFactory mailSenderFactory, EmailRequestRepository emailRequestRepository, EmailConfigRepository emailConfigRepository, MayleoMetrics metrics) {
        this.mailSenderFactory = mailSenderFactory;
        this.emailRequestRepository = emailRequestRepository;
        this.emailConfigRepository = emailConfigRepository;
        this.metrics = metrics;
    }

    @Async("emailTaskExecutor")
    public void sendEmail(EmailRequest emailRequest, PostcardHtml postcardHtml) {
        String clientId = emailRequest.getApiClient().getId();
        String requestId = emailRequest.getId();

        try {
            EmailConfig config = configCache.computeIfAbsent(clientId, id ->
                    emailConfigRepository.findByApiClient(emailRequest.getApiClient())
                            .orElseThrow(() -> new MayleoException(ExceptionCode.EMAIL_CONFIG_NOT_FOUND))
            );

            validateConfiguration(config, requestId);

            JavaMailSender mailSender = mailSenderFactory.getSender(clientId, config);
            MimeMessage mimeMessage = Objects.requireNonNull(mailSender).createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(config.getSenderEmail());
            helper.setTo(emailRequest.getToEmail());
            helper.setSubject(emailRequest.getSubject());
            helper.setText(postcardHtml.getHtml(), true);
            helper.addInline("postcardImage", new ClassPathResource(postcardHtml.getPostcard().getFilename()));
            mailSender.send(mimeMessage);

            log.info("[{}] Email sent successfully", requestId);
            metrics.recordEmailDelivery(MayleoMetrics.STATUS_SENT);
            updateRequestStatus(requestId, EmailRequestStatus.SENT, null);

        }catch (Exception ex) {
            log.error("[{}] Failed to send email: {}", requestId, ex.getMessage());
            metrics.recordEmailDelivery(MayleoMetrics.STATUS_FAILED);
            updateRequestStatus(requestId, EmailRequestStatus.FAILED, ex.getMessage());
        }
    }

    private void validateConfiguration(EmailConfig config, String requestId) {
        if (config.getSenderEmail() == null || config.getSenderEmail().isBlank() ||
                config.getSmtpHost() == null || config.getSmtpHost().isBlank() ||
                config.getSmtpPort() == null ||
                config.getSmtpUsername() == null || config.getSmtpUsername().isBlank() ||
                config.getSmtpPassword() == null || config.getSmtpPassword().isBlank()) {

            log.error("[{}] Email configuration is incomplete for client: {}", requestId, config.getApiClient().getName());
            throw new MayleoException(ExceptionCode.EMAIL_CONFIG_INCOMPLETE);
        }
    }

    private void updateRequestStatus(String requestId, EmailRequestStatus status, String error) {
        emailRequestRepository.findById(requestId).ifPresent(request -> {
            request.setStatus(status);
            request.setProcessedAt(Instant.now());
            if (error != null) {
                request.setErrorMessage(error);
                int nextRetry = request.getRetryCount() + 1; // retryCount is initialized with 0
                request.setRetryCount(nextRetry);
                if (nextRetry < maxRetries) {
                    request.setStatus(EmailRequestStatus.PENDING);
                }
            }
            emailRequestRepository.save(request);
        });
    }

    public void invalidateConfigCache(String clientId) {
        configCache.remove(clientId);
        mailSenderFactory.invalidateSenderCache(clientId);
    }
}
