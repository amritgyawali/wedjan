package com.wedjan.api.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancellationPolicyEngineTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");
    private final CancellationPolicyEngine policies = new CancellationPolicyEngine(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void exactPolicyBoundariesAreInclusiveAndGapFree() {
        assertRates("FLEXIBLE", new int[][] {{30,100},{29,50},{14,50},{13,0},{7,0},{6,0}});
        assertRates("MODERATE", new int[][] {{60,100},{59,50},{30,50},{29,0},{14,0},{13,0}});
        assertRates("STRICT", new int[][] {{90,100},{89,25},{45,25},{44,0},{30,0},{29,0}});
    }

    @Test
    void refundPropertiesHoldForAllPoliciesDaysAndCentValues() {
        for (String policy : new String[] {"FLEXIBLE", "MODERATE", "STRICT"}) {
            int previous = 0;
            for (int days = -30; days <= 365; days++) {
                int rate = policies.refundPercent(policy, days);
                assertThat(rate).isBetween(0, 100).isGreaterThanOrEqualTo(previous);
                previous = rate;
                for (long cents : new long[] {0, 1, 99, 100, 101, 9_999, 1_000_000_001L}) {
                    var result = policies.calculate(UUID.randomUUID(), policy,
                            LocalDate.of(2026, 7, 15).plusDays(days), "UTC", cents, false);
                    assertThat(result.refundableCents()).isBetween(0L, cents);
                    assertThat(result.refundableCents() + result.nonRefundableCents()).isEqualTo(cents);
                    assertThat(result.refundableCents()).isEqualTo(Math.floorDiv(cents * rate, 100));
                }
            }
        }
    }

    @Test
    void vendorFaultAlwaysReturnsOneHundredPercent() {
        var result = policies.calculate(UUID.randomUUID(), "STRICT",
                LocalDate.of(2026, 7, 16), "UTC", 123_457, true);
        assertThat(result.refundPercent()).isEqualTo(100);
        assertThat(result.refundableCents()).isEqualTo(123_457);
        assertThat(result.vendorPenalty()).isTrue();
    }

    private void assertRates(String policy, int[][] examples) {
        for (int[] example : examples) assertThat(policies.refundPercent(policy, example[0]))
                .as("%s at %s days", policy, example[0]).isEqualTo(example[1]);
    }
}
