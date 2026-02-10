package com.florentdeborde.mayleo.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit Test - MayleoMetrics")
class MayleoMetricsTest {

    private MeterRegistry registry;
    private MayleoMetrics mayleoMetrics;

    @BeforeEach
    void setUp() {
        // We use SimpleMeterRegistry (an in-memory implementation) instead of a Mockito mock.
        // This avoids NullPointerExceptions when the Micrometer Builder attempts to register
        // meters, as mocks return null by default for internal builder calls.
        registry = new SimpleMeterRegistry();
        mayleoMetrics = new MayleoMetrics(registry);
    }

    @Test
    @DisplayName("✅ recordApiRequest: Should increment counter with correct tags")
    void recordApiRequest_ShouldWork() {
        // WHEN: Record an API request for a specific client and outcome
        mayleoMetrics.recordApiRequest("TEST_CLIENT", MayleoMetrics.OUTCOME_ACCEPTED);

        // THEN: Retrieve the counter from the registry using its name and tags to verify the increment
        double count = registry.get("mayleo.api.requests")
                .tag("client", "TEST_CLIENT")
                .tag("outcome", MayleoMetrics.OUTCOME_ACCEPTED)
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("✅ recordEmailDelivery: Should increment delivery counter")
    void recordEmailDelivery_ShouldWork() {
        // WHEN: Record a successful email delivery
        mayleoMetrics.recordEmailDelivery(MayleoMetrics.STATUS_SENT);

        // THEN: Verify that the specific 'delivery' counter was created and incremented
        double count = registry.get("mayleo.emails.delivery")
                .tag("status", MayleoMetrics.STATUS_SENT)
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }
}