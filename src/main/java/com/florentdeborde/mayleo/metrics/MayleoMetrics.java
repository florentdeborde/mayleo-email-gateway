package com.florentdeborde.mayleo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MayleoMetrics {

    private final MeterRegistry registry;

    public static final String OUTCOME_RECEIVED = "received";
    public static final String OUTCOME_ACCEPTED = "accepted";
    public static final String OUTCOME_ERR_RPM = "err_rpm";
    public static final String OUTCOME_ERR_DAILY_QUOTA = "err_daily_quota";
    public static final String OUTCOME_ERR_CONFIG_NOT_FOUND = "err_config_not_found";

    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    public MayleoMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordApiRequest(String clientName, String outcome) {
        Counter.builder("mayleo.api.requests")
                .description("Tracking API calls from clients")
                .tag("client", clientName)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordEmailDelivery(String status) {
        Counter.builder("mayleo.emails.delivery")
                .description("Tracking actual email delivery status")
                .tag("status", status)
                .register(registry)
                .increment();
    }
}