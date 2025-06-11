package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.TriggerOnly;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import lombok.Getter;
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

    @Getter
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

    public ReactionIdentifiedEvent awaitReactionIdentifiedEventForReactionId(String reactionId) {
        log.info("Waiting for reaction identified event with reactionId: {}", reactionId);
        Predicate<ReactionIdentifiedEvent> predicate = event ->
                switch (event.getPayload().getReaction()) {
                    case TriggerOnly triggerOnly -> triggerOnly.getReactionId().equals(reactionId);
                    case ActionOnly actionOnly -> actionOnly.getReactionId().equals(reactionId);
                    case Reaction reaction -> reaction.getReactionId().equals(reactionId);
                    default ->
                            throw new IllegalStateException("Unexpected reaction payload type: " + event.getPayload().getReaction());
                };

        await()
                .until(() -> reactionIdentifiedEvents.values().stream().anyMatch(predicate));

        return reactionIdentifiedEvents.values().stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow();
    }

    public List<ReactionsObservedEvent> awaitReactionsObservedEvents() {
        await()
                .until(() -> !reactionsObservedEvents.isEmpty());
        return new ArrayList<>(reactionsObservedEvents);
    }

    public ReactionsObservedEvent awaitReactionsObservedEventForReactionId(String reactionId) {
        log.info("Waiting for reactions observed event with reactionId: {}", reactionId);
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
