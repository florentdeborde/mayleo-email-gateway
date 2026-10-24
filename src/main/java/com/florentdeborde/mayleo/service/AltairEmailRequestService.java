package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.altair.EmailRequestAltairDto;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class AltairEmailRequestService {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private final EmailRequestRepository repository;

    public AltairEmailRequestService(EmailRequestRepository repository) {
        this.repository = repository;
    }

    public Page<EmailRequestAltairDto> list(int page, int size, String status, String dateRange) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<EmailRequest> result;
        if (status != null && !status.isBlank()) {
            EmailRequestStatus s = EmailRequestStatus.valueOf(status.toUpperCase());
            result = repository.findByStatus(s, pageable);
        } else if (dateRange != null && !dateRange.isBlank()) {
            Instant[] range = resolveDateRange(dateRange);
            result = repository.findByCreatedAtBetween(range[0], range[1], pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return result.map(this::toDto);
    }

    public EmailRequestAltairDto getById(String id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailRequest not found: " + id));
    }

    @Transactional
    public void retryBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            repository.findById(id).ifPresent(req -> {
                if (req.getStatus() == EmailRequestStatus.FAILED) {
                    req.setStatus(EmailRequestStatus.PENDING);
                    req.setProcessedAt(null);
                    repository.save(req);
                }
            });
        }
    }

    private Instant[] resolveDateRange(String range) {
        LocalDate today = LocalDate.now(UTC);
        Instant from = switch (range.toUpperCase()) {
            case "TODAY"  -> today.atStartOfDay(UTC).toInstant();
            case "WEEK"   -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(UTC).toInstant();
            case "MONTH"  -> today.withDayOfMonth(1).atStartOfDay(UTC).toInstant();
            default       -> Instant.EPOCH;
        };
        return new Instant[]{ from, Instant.now() };
    }

    private EmailRequestAltairDto toDto(EmailRequest r) {
        return EmailRequestAltairDto.builder()
                .id(r.getId())
                .toEmail(r.getToEmail())
                .subject(r.getSubject())
                .langCode(r.getLangCode())
                .status(r.getStatus().name())
                .retryCount(r.getRetryCount())
                .apiClientName(r.getApiClient().getName())
                .createdAt(r.getCreatedAt())
                .processedAt(r.getProcessedAt())
                .errorMessage(r.getErrorMessage())
                .build();
    }
}
