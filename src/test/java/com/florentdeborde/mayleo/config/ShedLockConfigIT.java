package com.florentdeborde.mayleo.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@DisplayName("Integration Test - ShedLock Configuration")
class ShedLockConfigIT {

    @Autowired(required = false)
    private LockProvider lockProvider;

    @Test
    @DisplayName("✅ ShedLock: LockProvider should be correctly initialized in context")
    void lockProvider_shouldBePresentAndCorrectType() {
        assertThat(lockProvider)
                .as("LockProvider bean was not loaded. Please check @EnableSchedulerLock configuration.")
                .isNotNull();

        assertThat(lockProvider)
                .as("LockProvider should be an instance of JdbcTemplateLockProvider")
                .isInstanceOf(JdbcTemplateLockProvider.class);
    }
}