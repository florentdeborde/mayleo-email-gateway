package com.florentdeborde.mayleo.repository;

import com.florentdeborde.mayleo.model.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, String> {

    Optional<ApiClient> findByApiKey(String apiKey);

    @Query("SELECT a FROM ApiClient a LEFT JOIN FETCH a.allowedDomains WHERE a.apiKey = :apiKey")
    Optional<ApiClient> findByApiKeyWithDomains(@Param("apiKey") String apiKey);
}
