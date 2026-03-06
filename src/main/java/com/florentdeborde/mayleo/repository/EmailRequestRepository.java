package com.florentdeborde.mayleo.repository;

import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRequestRepository extends JpaRepository<EmailRequest, String> {

        // Find top 100 requests by status, ordered by creation date (for async worker)
        List<EmailRequest> findTop100ByStatusOrderByCreatedAtAsc(EmailRequestStatus status);

        // Find stuck requests (e.g. SENDING for too long)
        List<EmailRequest> findByStatusAndProcessedAtBefore(EmailRequestStatus status, Instant cutoff);

        Optional<EmailRequest> findByApiClientAndIdempotencyKey(ApiClient client, String idempotencyKey);

        @Modifying
        @Query(value = "UPDATE email_request SET status = 'SENDING', processed_at = :now, error_message = :instanceId WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
        int lockBatchForSending(@Param("now") Instant now, @Param("instanceId") String instanceId,
                        @Param("limit") int limit);

        List<EmailRequest> findByStatusAndErrorMessage(EmailRequestStatus status, String errorMessage);

        @Modifying
        @Query("DELETE FROM EmailRequest e WHERE e.createdAt < :cutoff AND e.status = :status")
        int deleteOldRequests(@Param("cutoff") Instant cutoff, @Param("status") EmailRequestStatus status);
}
