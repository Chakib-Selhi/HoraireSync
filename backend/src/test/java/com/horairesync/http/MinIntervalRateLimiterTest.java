package com.horairesync.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MinIntervalRateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);
    private final List<Long> sleeps = new ArrayList<>();
    private final MinIntervalRateLimiter limiter = new MinIntervalRateLimiter(Duration.ofSeconds(10), now::get,
            nanos -> {
                sleeps.add(nanos);
                now.addAndGet(nanos);
            });

    @Test
    void firstPermitIsImmediate() throws InterruptedException {
        limiter.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void secondPermitWaitsForTheRemainingInterval() throws InterruptedException {
        limiter.acquire();
        now.addAndGet(Duration.ofSeconds(3).toNanos());

        limiter.acquire();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(7).toNanos());
    }

    @Test
    void noWaitWhenIntervalAlreadyElapsed() throws InterruptedException {
        limiter.acquire();
        now.addAndGet(Duration.ofSeconds(15).toNanos());

        limiter.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void consecutivePermitsAreSpacedEvenly() throws InterruptedException {
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(10).toNanos(), Duration.ofSeconds(10).toNanos());
    }

    @Test
    void rejectsNegativeInterval() {
        assertThatThrownBy(() -> new MinIntervalRateLimiter(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
