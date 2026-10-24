package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.altair.EmailRequestAltairDto;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AltairEmailRequestService.class);

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
            log.info("[Altair] list page={} size={} filter=status:{}", page, size, s);
            result = repository.findByStatus(s, pageable);
        } else if (dateRange != null && !dateRange.isBlank()) {
            Instant[] range = resolveDateRange(dateRange);
            log.info("[Altair] list page={} size={} filter=dateRange:{} [{} → {}]", page, size, dateRange, range[0], range[1]);
            result = repository.findByCreatedAtBetween(range[0], range[1], pageable);
        } else {
            log.info("[Altair] list page={} size={} filter=none", page, size);
            result = repository.findAll(pageable);
        }
        log.info("[Altair] list → {} results (total={})", result.getNumberOfElements(), result.getTotalElements());
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
        log.info("[Altair] retryBatch requested for {} id(s): {}", ids.size(), ids);
        int retried = 0;
        for (String id : ids) {
            var opt = repository.findById(id);
            if (opt.isEmpty()) {
                log.warn("[Altair] retryBatch — id not found: {}", id);
                continue;
            }
            EmailRequest req = opt.get();
            if (req.getStatus() == EmailRequestStatus.FAILED) {
                req.setStatus(EmailRequestStatus.PENDING);
                req.setProcessedAt(null);
                repository.save(req);
                retried++;
            } else {
                log.debug("[Altair] retryBatch — skipped {} (status={})", id, req.getStatus());
            }
        }
        log.info("[Altair] retryBatch done — {}/{} reset to PENDING", retried, ids.size());
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
