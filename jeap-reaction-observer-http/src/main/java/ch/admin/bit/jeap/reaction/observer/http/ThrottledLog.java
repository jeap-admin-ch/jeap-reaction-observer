package ch.admin.bit.jeap.reaction.observer.http;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Limits how often something is logged: at most once per interval. The log entry gets the number of
 * occurrences since the last entry.
 */
class ThrottledLog {

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final AtomicLong occurrences = new AtomicLong();
    private long lastLogged;
    private boolean neverLogged = true;

    /**
     * @param interval the interval between two log entries, zero or less logs every occurrence
     */
    ThrottledLog(Duration interval) {
        this(interval, System::nanoTime);
    }

    ThrottledLog(Duration interval, LongSupplier nanoTime) {
        this.intervalNanos = interval.toNanos();
        this.nanoTime = nanoTime;
    }

    /**
     * @param logEntry writes the log entry, gets the number of occurrences since the last entry including this one
     */
    void log(LongConsumer logEntry) {
        occurrences.incrementAndGet();
        long now = nanoTime.getAsLong();
        if (isDue(now)) {
            logEntry.accept(occurrences.getAndSet(0));
        }
    }

    private synchronized boolean isDue(long now) {
        if (neverLogged || now - lastLogged >= intervalNanos) {
            neverLogged = false;
            lastLogged = now;
            return true;
        }
        return false;
    }
}
