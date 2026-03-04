package com.florentdeborde.mayleo.service;

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
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.time.Instant;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

@Service
@Slf4j
public class EmailRequestService {

    private final EmailRequestRepository repository;

    private final EmailConfigRepository emailConfigRepository;

    private final MayleoMetrics metrics;

    private final Map<String, Bucket> rpmBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> dailyBuckets = new ConcurrentHashMap<>();

    public EmailRequestService(EmailRequestRepository repository, EmailConfigRepository emailConfigRepository,
            MayleoMetrics metrics) {
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
        if (idempotencyKey == null)
            return Optional.empty();

        return repository.findByApiClientAndIdempotencyKey(apiClient, idempotencyKey)
                .map(EmailRequest::getId);
    }

    private void validateRpmLimitAndDailyQuota(ApiClient apiClient) {
        Bucket dailyBucket = resolveDailyBucket(apiClient);
        Bucket rpmBucket = resolveRpmBucket(apiClient);

        if (!rpmBucket.tryConsume(1)) {
            metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_RPM);
            throw new MayleoException(ExceptionCode.RPM_LIMIT_EXCEEDED);
        }

        if (!dailyBucket.tryConsume(1)) {
            metrics.recordApiRequest(apiClient.getName(), MayleoMetrics.OUTCOME_ERR_DAILY_QUOTA);
            throw new MayleoException(ExceptionCode.DAILY_QUOTA_EXCEEDED);
        }
    }

    private Bucket resolveRpmBucket(ApiClient apiClient) {
        return rpmBuckets.computeIfAbsent(apiClient.getId(), id -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(apiClient.getRpmLimit())
                        .refillIntervally(apiClient.getRpmLimit(), Duration.ofMinutes(1))
                        .build())
                .build());
    }

    private Bucket resolveDailyBucket(ApiClient apiClient) {
        return dailyBuckets.computeIfAbsent(apiClient.getId(), id -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(apiClient.getDailyQuota())
                        .refillIntervally(apiClient.getDailyQuota(), Duration.ofDays(1))
                        .build())
                .build());
    }

    private EmailRequest buildEmailRequest(EmailRequestDto dto, ApiClient apiClient, EmailConfig emailConfig,
            String idempotencyKey) {
        String preparedSubject = fallback(dto.getSubject(), emailConfig.getDefaultSubject());
        String preparedMessage = fallback(dto.getMessage(), emailConfig.getDefaultMessage());

        // Sanitization (Anti-XSS - 100% removal)
        preparedSubject = cleanHtml(preparedSubject);
        preparedMessage = cleanHtml(preparedMessage);

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

    public static String cleanHtml(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, Safelist.none());
    }

    private String fallback(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
