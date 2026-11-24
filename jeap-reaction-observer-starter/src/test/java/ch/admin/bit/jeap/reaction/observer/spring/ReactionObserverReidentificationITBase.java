package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The reidentification does not depend on the reaction model version a service observes by: the test runs once
 * per version. The reidentification period of the tests is the shortest one, 2 seconds.
 */
abstract class ReactionObserverReidentificationITBase extends ReactionKafkaTestBase {

    static final String SHORTEST_REIDENTIFICATION_PERIOD = "jeap.reaction.observer.reidentification-period=2s";

    @Autowired
    private ReactionObserverService reactionObserverService;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;
    @Autowired
    private MeterRegistry meterRegistry;

    abstract int reactionModelVersion();

    @Test
    void reactionOccurringBeyondThePeriod_isIdentifiedAgainWithAnIdempotenceIdOfItsOwn() {
        // A reaction of this test only: the topics of the tests are read by all tests of the module
        Reaction reaction = new Reaction(Observation.ofEvent(getClass().getSimpleName() + "Event", "topic"), null);

        await().pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            reactionObserverService.reactionObserved(reaction);
            assertThat(identificationsOf(reaction)).hasSizeGreaterThanOrEqualTo(2);
        });

        Map<String, ReactionIdentifiedEvent> identifications = identificationsOf(reaction);
        assertThat(identifications.values())
                .allSatisfy(identification -> assertThat(identification.getPayload().getReactionModelVersion()).isEqualTo(reactionModelVersion()));
        // The idempotence ids differ in the identification start at their end only: same instance, reaction and model version
        List<String> idempotenceIdsWithoutIdentificationStart = identifications.keySet().stream()
                .map(idempotenceId -> idempotenceId.substring(0, idempotenceId.lastIndexOf('_')))
                .distinct()
                .toList();
        assertThat(idempotenceIdsWithoutIdentificationStart).singleElement().asString()
                .endsWith("_" + reaction.id() + "_v" + reactionModelVersion());
        // The executions are counted all along, none of them is dropped when a period ends
        ReactionsObservedEvent observed = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reaction.id());
        assertThat(observed.getPayload().getReactionModelVersion()).isEqualTo(reactionModelVersion());
        assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions").counters())
                .extracting(Counter::count).containsOnly(0.0);
        assertThat(meterRegistry.get("jeap.reaction.observer.identified.reactions").gauge().value()).isLessThanOrEqualTo(1);
    }

    /**
     * @return the reports of the reaction by their idempotence id
     */
    private Map<String, ReactionIdentifiedEvent> identificationsOf(Reaction reaction) {
        return reactionEventsTestConsumer.getReactionIdentifiedEvents().entrySet().stream()
                .filter(identification -> ReactionIdentifiedEvents.reactionId(identification.getValue()).equals(reaction.id()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Configuration(proxyBeanMethods = false)
    static class ReidentificationTestConfiguration {

        @Bean
        ReactionEventsTestConsumer reactionEventsTestConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }
}
