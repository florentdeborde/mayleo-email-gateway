package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.UsageStats;
import com.florentdeborde.mayleo.exception.ExceptionCode;
import com.florentdeborde.mayleo.exception.MayleoException;
import com.florentdeborde.mayleo.dto.request.EmailRequestDto;
import com.florentdeborde.mayleo.metrics.MayleoMetrics;
import com.florentdeborde.mayleo.model.*;
import com.florentdeborde.mayleo.repository.EmailConfigRepository;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
public class EmailRequestService {

    private final EmailRequestRepository repository;

    private final EmailConfigRepository emailConfigRepository;

    private final MayleoMetrics metrics;

    public EmailRequestService(EmailRequestRepository repository , EmailConfigRepository emailConfigRepository, MayleoMetrics metrics) {
        this.repository = repository;
        this.emailConfigRepository = emailConfigRepository;
        this.metrics = metrics;
    }

    public String createEmailRequest(ApiClient apiClient, EmailRequestDto dto, String idempotencyKey) {
        Optional<String> existingId = findExistingId(apiClient, idempotencyKey);
        if (existingId.isPresent()) {
            return existingId.get();
        }

        metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_RECEIVED);

        validateRpmLimitAndDailyQuota(apiClient);

        EmailConfig emailConfig = emailConfigRepository.findByApiClient(apiClient)
                .orElseGet(() -> {
                    metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_CONFIG_NOT_FOUND);
                    throw new MayleoException(ExceptionCode.EMAIL_CONFIG_NOT_FOUND);
                });

        try {
            EmailRequest emailRequest = buildEmailRequest(dto, apiClient, emailConfig, idempotencyKey);
            EmailRequest savedEmailRequest = repository.save(emailRequest);

            metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ACCEPTED);
            return savedEmailRequest.getId();
        } catch (DataIntegrityViolationException e) {
            return findExistingId(apiClient, idempotencyKey).orElseThrow(() -> e);
        }
    }

    private Optional<String> findExistingId(ApiClient apiClient, String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();

        return repository.findByApiClientAndIdempotencyKey(apiClient, idempotencyKey)
                .map(EmailRequest::getId);
    }

    private void validateRpmLimitAndDailyQuota(ApiClient apiClient) {
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES);
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();

        UsageStats stats = repository.getUsageStats(apiClient, startOfDay, oneMinuteAgo);

        if (stats.rpmUsage() >= apiClient.getRpmLimit()) {
            metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_RPM);
            throw new MayleoException(ExceptionCode.RPM_LIMIT_EXCEEDED);
        }

        if (stats.dailyUsage() >= apiClient.getDailyQuota()) {
            metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_DAILY_QUOTA);
            throw new MayleoException(ExceptionCode.DAILY_QUOTA_EXCEEDED);
        }
    }

    private EmailRequest buildEmailRequest(EmailRequestDto dto, ApiClient apiClient, EmailConfig emailConfig, String idempotencyKey) {
        String preparedSubject = fallback(dto.getSubject(), emailConfig.getDefaultSubject());
        String preparedMessage = fallback(dto.getMessage(), emailConfig.getDefaultMessage());

        // Sanitization (Anti-XSS)
        preparedSubject = escapeHtml(preparedSubject);
        preparedMessage = escapeHtml(preparedMessage);

        return EmailRequest.builder()
                .id(UUID.randomUUID().toString())
                .apiClient(apiClient)
                .toEmail(dto.getToEmail())
                .subject(preparedSubject)
                .message(preparedMessage)
                .imageSource(dto.getImageSource())
                .imagePath(dto.getImagePath())
                .langCode(Objects.nonNull(dto.getLangCode()) ? dto.getLangCode() : emailConfig.getDefaultLanguage())
                .createdAt(Instant.now())
                .status(EmailRequestStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    public static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }

    private String fallback(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
