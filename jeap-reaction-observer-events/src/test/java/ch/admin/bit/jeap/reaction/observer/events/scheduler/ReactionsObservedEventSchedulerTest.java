package ch.admin.bit.jeap.reaction.observer.events.scheduler;

import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "jeap.reaction.observer.events.observed-event-rate-seconds=1")
class ReactionsObservedEventSchedulerTest extends KafkaIntegrationTestBase {

    @Autowired
    private ReactionEventsTestConsumer testConsumer;
    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;
    @MockitoBean
    private ReactionObserverService reactionObserverServiceMock;

    @Test
    void testSchedulerProducesEvent() {
        // Make sure the task is scheduled as expected
        FixedRateTask fixedRateTask = getReactionsObservedEventSchedulerTask();
        assertThat(fixedRateTask.getIntervalDuration())
                .isPositive();

        // Mock the reaction counts
        Map<String, AtomicInteger> mockCounts = Map.of("reaction1", new AtomicInteger(5));
        when(reactionObserverServiceMock.getAndClearCountByReactionId())
                .thenReturn(mockCounts);

        // Wait for at least one event to be produced
        List<ReactionsObservedEvent> events = testConsumer.awaitReactionsObservedEvents();

        events.forEach(event -> {
            assertThat(event.getPublisher().getService())
                    .isEqualTo("test-service-name");
            assertThat(event.getPublisher().getSystem())
                    .isEqualTo("test-system-name");
            assertThat(event.getPayload().getTimeframe().getStart())
                    .isBefore(event.getPayload().getTimeframe().getEnd());
        });

        ReactionsObservedEvent eventForReaction = testConsumer.awaitReactionsObservedEventForReactionId("reaction1");
        assertThat(eventForReaction.getPayload().getObservations())
                .hasSize(1)
                .extracting(Observation::getReactionId, Observation::getCount)
                .containsExactly(tuple("reaction1", 5));
    }

    private FixedRateTask getReactionsObservedEventSchedulerTask() {
        return scheduledTaskHolder.getScheduledTasks()
                .stream()
                .map(ScheduledTask::getTask)
                .filter(FixedRateTask.class::isInstance)
                .map(FixedRateTask.class::cast)
                .filter(task -> task.toString().contains(ReactionsObservedEventScheduler.class.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No scheduled task found for the ReactionsObservedEventScheduler"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public ReactionEventsTestConsumer testConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
