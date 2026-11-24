package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The limit of identified reactions with the reaction model version of a service that does not choose one, 1:
 * the Reaction Observer Service only gets counts of reactions that were identified to it.
 */
@SpringBootTest(properties = {
        "jeap.reaction.observer.model-version=1",
        // The test reports thousands of reactions: no log entry for each of them
        "logging.level.ch.admin.bit.jeap.reaction.observer=WARN"})
class ReactionObserverReactionLimitIT extends ReactionKafkaTestBase {

    private static final String FQN_PREFIX = "ReactionLimitTestEvent";
    private static final Duration ALL_REACTIONS_REPORTED_TIMEOUT = Duration.ofMinutes(2);

    @Autowired
    private ReactionObserverService reactionObserverService;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void reactionOverTheLimit_isNeitherIdentifiedNorCounted() {
        List<Reaction> reactionsWithinTheLimit = IntStream.range(0, ReactionObserverService.MAX_REACTION_COUNT)
                .mapToObj(ReactionObserverReactionLimitIT::reaction)
                .toList();
        Set<String> reactionIdsWithinTheLimit = reactionsWithinTheLimit.stream().map(Reaction::id).collect(Collectors.toSet());
        Reaction reactionOverTheLimit = reaction(ReactionObserverService.MAX_REACTION_COUNT);

        reactionsWithinTheLimit.forEach(reactionObserverService::reactionObserved);
        reactionObserverService.reactionObserved(reactionOverTheLimit);
        // The limit is reached: an identified reaction is still counted
        reactionObserverService.reactionObserved(reactionsWithinTheLimit.getFirst());

        await().atMost(ALL_REACTIONS_REPORTED_TIMEOUT).untilAsserted(() ->
                assertThat(identifiedReactionIdsOfThisTest()).hasSameSizeAs(reactionIdsWithinTheLimit));
        await().atMost(ALL_REACTIONS_REPORTED_TIMEOUT).untilAsserted(() ->
                assertThat(countsOfThisTest()).hasSameSizeAs(reactionIdsWithinTheLimit));
        assertThat(identifiedReactionIdsOfThisTest()).isEqualTo(reactionIdsWithinTheLimit);
        assertThat(countsOfThisTest().keySet()).isEqualTo(reactionIdsWithinTheLimit);
        assertThat(countsOfThisTest())
                .containsEntry(reactionsWithinTheLimit.getFirst().id(), 2)
                .containsEntry(reactionsWithinTheLimit.getLast().id(), 1);
        assertThat(meterRegistry.get("jeap.reaction.observer.identified.reactions").gauge().value())
                .isEqualTo(ReactionObserverService.MAX_REACTION_COUNT);
        assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions").tag("reason", "reaction_limit").counter().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions").tag("reason", "not_identified").counter().count())
                .isZero();
    }

    private static Reaction reaction(int number) {
        return new Reaction(ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation.ofEvent(FQN_PREFIX + number, "topic"), null);
    }

    private Set<String> identifiedReactionIdsOfThisTest() {
        return reactionEventsTestConsumer.getReactionIdentifiedEvents().values().stream()
                .map(ReactionIdentifiedEvents::reactionId)
                .filter(ReactionObserverReactionLimitIT::isOfThisTest)
                .collect(Collectors.toSet());
    }

    /**
     * @return how often each reaction of this test was counted, summed up over the events
     */
    private Map<String, Integer> countsOfThisTest() {
        return reactionEventsTestConsumer.awaitReactionsObservedEvents().stream()
                .map(ReactionsObservedEvent::getPayload)
                .flatMap(payload -> payload.getObservations().stream())
                .filter(observation -> isOfThisTest(observation.getReactionId()))
                .collect(Collectors.toMap(Observation::getReactionId, Observation::getCount, Integer::sum));
    }

    private static boolean isOfThisTest(String reactionId) {
        return reactionId.startsWith("event:" + FQN_PREFIX);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ReactionEventsTestConsumer reactionEventsTestConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
