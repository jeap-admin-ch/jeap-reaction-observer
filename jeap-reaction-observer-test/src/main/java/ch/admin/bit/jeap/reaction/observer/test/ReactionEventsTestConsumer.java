package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

@Slf4j
public class ReactionEventsTestConsumer {

    static {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
    }

    private final Map<String, ReactionIdentifiedEvent> reactionIdentifiedEvents = new HashMap<>();
    private final List<ReactionsObservedEvent> reactionsObservedEvents = new ArrayList<>();

    @TestKafkaListener(topics = "reaction-identified")
    void onReactionIdentifiedEvent(ReactionIdentifiedEvent event) {
        log.info("Received reaction identified event: {}", event);
        reactionIdentifiedEvents.put(event.getIdentity().getIdempotenceId(), event);
    }

    @TestKafkaListener(topics = "reactions-observed")
    void onReactionIdentifiedEvent(ReactionsObservedEvent event) {
        log.info("Received reactions observed event: {}", event);
        reactionsObservedEvents.add(event);
    }

    public ReactionIdentifiedEvent awaitReactionIdentifiedEventForReaction(String reactionId) {
        String key = "ri_" + reactionId;
        log.info("Awaiting reaction identified event with idempotence id {}", key);
        await()
                .until(() -> reactionIdentifiedEvents.containsKey(key));
        return reactionIdentifiedEvents.get(key);
    }

    public List<ReactionsObservedEvent> awaitReactionsObservedEvents() {
        await()
                .until(() -> !reactionsObservedEvents.isEmpty());
        return new ArrayList<>(reactionsObservedEvents);
    }

    public ReactionsObservedEvent awaitReactionsObservedEventForReaction(String reactionId) {
        Predicate<ReactionsObservedEvent> predicate = event ->
                event.getPayload().getObservations().stream().anyMatch(observation ->
                        observation.getReactionId().equals(reactionId));

        await()
                .until(() -> reactionsObservedEvents.stream().anyMatch(predicate));

        return reactionsObservedEvents.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow();
    }
}
