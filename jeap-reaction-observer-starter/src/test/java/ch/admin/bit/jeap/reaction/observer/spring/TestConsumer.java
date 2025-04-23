package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.awaitility.Awaitility.await;

@Slf4j
class TestConsumer {

    private final Map<String, ReactionIdentifiedEvent> reactionIdentifiedEvents = new HashMap<>();

    @TestKafkaListener(topics = "reaction-identified")
    void onReactionIdentifiedEvent(ReactionIdentifiedEvent event) {
        log.info("Received reaction identified event: {}", event);
        reactionIdentifiedEvents.put(event.getIdentity().getIdempotenceId(), event);
    }

    ReactionIdentifiedEvent awaitReactionIdentifiedEventForReaction(Reaction reaction) {
        String key = "ri_" + reaction.id();
        log.info("Awaiting reaction identified event with idempotence id {}", key);
        await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> reactionIdentifiedEvents.containsKey(key));
        return reactionIdentifiedEvents.get(key);
    }
}
