package ch.admin.bit.jeap.reaction.observer.http;

import java.time.Duration;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Limits how often something is logged: at most once per interval. The log entry gets the number of
 * occurrences since the last entry.
 */
class ThrottledLog {

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private long occurrences;
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
        long occurrencesToLog = countOccurrence();
        if (occurrencesToLog > 0) {
            logEntry.accept(occurrencesToLog);
        }
    }

    /**
     * Reads the time, counts an occurrence and decides whether a log entry is due, in one step: an occurrence is
     * counted for exactly one log entry, no log entry is due without an occurrence, and no occurrence is judged
     * by a time that lies before the one of the last log entry.
     * <p>
     * Without an interval every occurrence is due, whatever the time.
     *
     * @return the number of occurrences to log now, 0 if no log entry is due
     */
    private synchronized long countOccurrence() {
        long now = nanoTime.getAsLong();
        occurrences++;
        if (intervalNanos > 0 && !neverLogged && now - lastLogged < intervalNanos) {
            return 0;
        }
        neverLogged = false;
        lastLogged = now;
        long occurrencesToLog = occurrences;
        occurrences = 0;
        return occurrencesToLog;
    }
}
