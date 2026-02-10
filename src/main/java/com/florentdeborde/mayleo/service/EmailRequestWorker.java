package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import com.florentdeborde.mayleo.repository.EmailRequestRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class EmailRequestWorker {

    private final EmailRequestRepository repository;
    private final EmailSenderService emailSenderService;
    private final PostcardRenderer postcardRenderer;

    public EmailRequestWorker(EmailRequestRepository repository, EmailSenderService emailSenderService, PostcardRenderer postcardRenderer) {
        this.repository = repository;
        this.emailSenderService = emailSenderService;
        this.postcardRenderer = postcardRenderer;
    }

    /**
     * Note on Scalability:
     * We are currently using ShedLock to prevent multiple instances from processing the same emails (Race Condition).
     * This ensures that only one instance handles the batch at any given time.
     *
     * FUTURE SCALABILITY (Option 3):
     * If the volume increases to millions of emails per hour, consider switching to "Optimistic Locking"
     * by assigning an 'instance_id' to a batch of rows via an atomic UPDATE, allowing all instances
     * to work simultaneously on different segments of the queue.
     */
    @Scheduled(fixedDelayString = "${app.mail.process-delay:5000}") // each 5s after last execution
    @SchedulerLock(
            name = "EmailRequestService_processPendingEmails", // Keeping same lock name for compatibility or migration? User didn't specify, maybe safe to update to Worker name but let's stick to old name or update? Better update to reflect class name but lock name is persistent. Let's update it to "EmailRequestWorker_..." for clarity if user truncates lock table, or keep it. I'll update it to match class name.
            lockAtMostFor = "2m",
            lockAtLeastFor = "100ms"
    )
    public void processPendingRequestsAutomatically() {
        List<EmailRequest> pendingRequests = repository.findTop100ByStatusOrderByCreatedAtAsc(EmailRequestStatus.PENDING);

        for (EmailRequest request : pendingRequests) {
            try {
                log.info("[{}] Dispatching email request to async sender", request.getId());

                request.setStatus(EmailRequestStatus.SENDING);
                request.setProcessedAt(Instant.now());
                repository.saveAndFlush(request);

                PostcardHtml postcardHtml = postcardRenderer.render(request, "From Mayleo");
                emailSenderService.sendEmail(request, postcardHtml);
            } catch (Exception e) {
                markAsFailed(request, e);
                log.error("[{}] Failed to send email request: {}", request.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    @SchedulerLock(
            name = "EmailRequestService_cleanupStuckRequests",
            lockAtMostFor = "5m",
            lockAtLeastFor = "1m"
    )
    public void cleanupStuckRequests() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<EmailRequest> stuckRequests = repository.findByStatusAndProcessedAtBefore(EmailRequestStatus.SENDING, cutoff);

        if (!stuckRequests.isEmpty()) {
            log.warn("[Clean Up] Found {} stuck requests in SENDING state. Resetting to PENDING.", stuckRequests.size());
            for (EmailRequest request : stuckRequests) {
                request.setStatus(EmailRequestStatus.PENDING);
                request.setRetryCount(request.getRetryCount() + 1);
                request.setErrorMessage("Self-Healing: Reset from SENDING (Stuck)");
                repository.save(request);
            }
        }
    }

    private void markAsFailed(EmailRequest request, Exception e) {
        request.setStatus(EmailRequestStatus.FAILED);
        request.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        request.setRetryCount(request.getRetryCount() + 1);
        repository.save(request);
    }
}
