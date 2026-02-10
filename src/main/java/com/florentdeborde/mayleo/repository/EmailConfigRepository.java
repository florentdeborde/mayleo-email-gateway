package com.florentdeborde.mayleo.repository;

import com.florentdeborde.mayleo.model.EmailConfig;
import com.florentdeborde.mayleo.model.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailConfigRepository extends JpaRepository<EmailConfig, String> {

    // Retrieve the email configuration associated with a given API client
    Optional<EmailConfig> findByApiClient(ApiClient apiClient);
}
