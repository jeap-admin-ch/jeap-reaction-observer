package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionsObservedEventBuilderTest {

    private static final Instant FROM = Instant.parse("2026-10-26T04:10:00Z");
    private static final Instant TO = Instant.parse("2026-10-26T04:15:00Z");

    @Test
    void build_reportsTheReactionModelVersion() {
        assertThat(builder(ReactionModel.V1).build().getPayload().getReactionModelVersion()).isEqualTo(1);
        assertThat(builder(ReactionModel.V2).build().getPayload().getReactionModelVersion()).isEqualTo(2);
    }

    @Test
    void build_neverSetsTheCountersOfUnidentifiedActions() {
        ReactionsObservedEvent event = builder(ReactionModel.V2).build();

        Observation observation = event.getPayload().getObservations().getFirst();
        assertThat(observation.getReactionId()).isEqualTo("reaction-id");
        assertThat(observation.getCount()).isEqualTo(3);
        assertThat(observation.getObservationsWithUnidentifiedActions()).isZero();
        assertThat(observation.getMinUnidentifiedActions()).isZero();
        assertThat(observation.getUnidentifiedActions()).isZero();
    }

    private static ReactionsObservedEventBuilder builder(ReactionModel reactionModel) {
        return new ReactionsObservedEventBuilder("test-system", "test-service", reactionModel.version())
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of("reaction-id", new AtomicInteger(3)))
                .timeframe(FROM, TO);
    }
}
