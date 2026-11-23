package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ThrottledLogTest {

    private final AtomicLong nanoTime = new AtomicLong(-5_000_000_000L);
    private final List<Long> entries = new ArrayList<>();
    private final ThrottledLog throttledLog = new ThrottledLog(Duration.ofMinutes(1), nanoTime::get);

    @Test
    void log_firstOccurrenceIsLogged() {
        throttledLog.log(entries::add);

        assertThat(entries).containsExactly(1L);
    }

    @Test
    void log_atMostOncePerInterval_withTheNumberOfOccurrences() {
        throttledLog.log(entries::add);
        nanoTime.addAndGet(Duration.ofSeconds(59).toNanos());
        throttledLog.log(entries::add);
        throttledLog.log(entries::add);
        assertThat(entries).containsExactly(1L);

        nanoTime.addAndGet(Duration.ofSeconds(1).toNanos());
        throttledLog.log(entries::add);

        assertThat(entries).containsExactly(1L, 3L);
    }

    @Test
    void log_intervalZero_everyOccurrenceIsLogged() {
        ThrottledLog unthrottledLog = new ThrottledLog(Duration.ZERO, nanoTime::get);

        unthrottledLog.log(entries::add);
        unthrottledLog.log(entries::add);
        unthrottledLog.log(entries::add);

        assertThat(entries).containsExactly(1L, 1L, 1L);
    }

    /**
     * Without an interval every occurrence is logged, whatever the time: also if it lies before the time of the
     * last log entry.
     */
    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void log_intervalZeroOrLess_everyOccurrenceIsLogged_alsoWithATimeBeforeTheLastLogEntry(long intervalNanos) {
        ThrottledLog unthrottledLog = new ThrottledLog(Duration.ofNanos(intervalNanos), nanoTime::get);

        unthrottledLog.log(entries::add);
        nanoTime.addAndGet(-2);
        unthrottledLog.log(entries::add);

        assertThat(entries).containsExactly(1L, 1L);
    }

    /**
     * A second occurrence is logged completely while the first one reads the time. Each log entry has to tell
     * of one occurrence, not one of two and the other of none.
     */
    @Test
    void log_intervalZero_overlappingOccurrences_eachLogEntryCountsOne() {
        AtomicBoolean overlapped = new AtomicBoolean();
        AtomicReference<ThrottledLog> unthrottledLog = new AtomicReference<>();
        unthrottledLog.set(new ThrottledLog(Duration.ZERO, () -> {
            if (overlapped.compareAndSet(false, true)) {
                unthrottledLog.get().log(entries::add);
            }
            return nanoTime.get();
        }));

        unthrottledLog.get().log(entries::add);

        assertThat(entries).containsExactly(1L, 1L);
    }

    /**
     * A second occurrence is logged while the first one reads the time, and a third one after the interval:
     * every occurrence is counted for exactly one log entry, and no log entry tells of no occurrence.
     */
    @Test
    void log_overlappingOccurrences_everyOccurrenceIsCountedOnce() {
        AtomicBoolean overlapped = new AtomicBoolean();
        AtomicReference<ThrottledLog> overlappingLog = new AtomicReference<>();
        overlappingLog.set(new ThrottledLog(Duration.ofMinutes(1), () -> {
            if (overlapped.compareAndSet(false, true)) {
                overlappingLog.get().log(entries::add);
            }
            return nanoTime.get();
        }));

        overlappingLog.get().log(entries::add);
        nanoTime.addAndGet(Duration.ofMinutes(1).toNanos());
        overlappingLog.get().log(entries::add);

        assertThat(entries).containsExactly(1L, 2L);
    }

    @Test
    void log_systemClockByDefault() {
        ThrottledLog systemClockLog = new ThrottledLog(Duration.ofHours(1));

        systemClockLog.log(entries::add);
        systemClockLog.log(entries::add);

        assertThat(entries).containsExactly(1L);
    }
}
