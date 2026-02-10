package com.florentdeborde.mayleo.repository;

import com.florentdeborde.mayleo.dto.internal.UsageStats;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.model.EmailRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<EmailRequest> findByApiClientAndIdempotencyKey(ApiClient client, String idempotencyKe);

    /**
     * Optimized query for usage stats.
     * * NOTE: Uses composite index (api_client_id, created_at).
     * PERFORMANCE TIP: This order is optimal because it first narrows down to the specific
     * client's data partition before performing a range scan on the timestamp.
     */
    @Query("""
        SELECT new com.florentdeborde.mayleo.dto.internal.UsageStats(
                COUNT(CASE WHEN e.createdAt >= :startOfDay THEN 1 END),
                COUNT(CASE WHEN e.createdAt >= :oneMinuteAgo THEN 1 END)
        )
        FROM EmailRequest e
        WHERE e.apiClient = :client
          AND e.createdAt >= :startOfDay
    """)
    UsageStats getUsageStats(
            @Param("client") ApiClient client,
            @Param("startOfDay") Instant startOfDay,
            @Param("oneMinuteAgo") Instant oneMinuteAgo
    );
}
