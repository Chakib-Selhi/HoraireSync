package com.horairesync.http;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Guarantees a minimum interval between two permits.
 *
 * <p>Callers are served one at a time: {@link #acquire()} blocks until the interval since the previous permit has
 * elapsed. This is how the backend honours the {@code Crawl-delay} of a site's robots.txt.
 */
public final class MinIntervalRateLimiter {

    /** Abstraction over {@link Thread#sleep(long)} so tests run instantly. */
    @FunctionalInterface
    public interface Sleeper {
        void sleepNanos(long nanos) throws InterruptedException;
    }

    private final long intervalNanos;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private long nextPermitAt;
    private boolean firstPermit = true;

    public MinIntervalRateLimiter(Duration interval) {
        this(interval, System::nanoTime, nanos -> Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000)));
    }

    public MinIntervalRateLimiter(Duration interval, LongSupplier nanoClock, Sleeper sleeper) {
        if (interval.isNegative()) {
            throw new IllegalArgumentException("interval must not be negative");
        }
        this.intervalNanos = interval.toNanos();
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    /** Blocks until a request may be sent. */
    public synchronized void acquire() throws InterruptedException {
        long now = nanoClock.getAsLong();
        if (!firstPermit) {
            long wait = nextPermitAt - now;
            if (wait > 0) {
                sleeper.sleepNanos(wait);
                now = nextPermitAt;
            }
        }
        firstPermit = false;
        nextPermitAt = now + intervalNanos;
    }
}
