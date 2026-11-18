package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    @Test
    void log_systemClockByDefault() {
        ThrottledLog systemClockLog = new ThrottledLog(Duration.ofHours(1));

        systemClockLog.log(entries::add);
        systemClockLog.log(entries::add);

        assertThat(entries).containsExactly(1L);
    }
}
