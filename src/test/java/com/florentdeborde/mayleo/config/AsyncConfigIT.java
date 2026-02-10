package com.florentdeborde.mayleo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("it")
@Import(AsyncConfigIT.AsyncTestService.class)
@DisplayName("Integration Test - Async Configuration")
class AsyncConfigIT {

    @Autowired
    @Qualifier("emailTaskExecutor")
    private Executor executor;

    @Autowired
    private AsyncConfigurer asyncConfigurer;

    @Autowired
    private AsyncTestService asyncTestService;

    @Test
    @DisplayName("✅ Should use the custom EmailTaskExecutor for @Async methods")
    void shouldUseCaseSpecificExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<String> future = asyncTestService.getThreadName();
        String threadName = future.get(5, TimeUnit.SECONDS);

        assertThat(threadName).startsWith("EmailTask-");
    }

    @Test
    @DisplayName("✅ AsyncExceptionHandler: Should log error without crashing via injected config")
    void shouldHandleAsyncExceptionGracefully() throws NoSuchMethodException {
        // GIVEN:
        AsyncUncaughtExceptionHandler handler = asyncConfigurer.getAsyncUncaughtExceptionHandler();
        Method method = this.getClass().getDeclaredMethod("shouldHandleAsyncExceptionGracefully");
        RuntimeException exception = new RuntimeException("Test Async Exception");

        // WHEN & THEN
        assertThat(handler).isNotNull();
        assertDoesNotThrow(() ->
                handler.handleUncaughtException(exception, method, "param1", 123)
        );
    }

    @Component
    static class AsyncTestService {
        @Async("emailTaskExecutor")
        public CompletableFuture<String> getThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}