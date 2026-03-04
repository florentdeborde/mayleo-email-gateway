package com.florentdeborde.mayleo.repository;

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

}
