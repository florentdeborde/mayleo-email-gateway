package com.florentdeborde.mayleo.repository;

import com.florentdeborde.mayleo.model.StorageConfig;
import com.florentdeborde.mayleo.model.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageConfigRepository extends JpaRepository<StorageConfig, String> {

    Optional<StorageConfig> findByApiClient(ApiClient apiClient);

    Optional<StorageConfig> findByApiClientAndEnabledTrue(ApiClient apiClient);
}
