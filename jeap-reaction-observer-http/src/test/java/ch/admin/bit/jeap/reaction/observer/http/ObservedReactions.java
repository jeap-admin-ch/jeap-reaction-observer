package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Test fixture: a real recorder and observer service, keeping the reactions the service would report.
 */
class ObservedReactions implements ReactionIdentifiedListener {

    static final Observation TRIGGER = Observation.ofEvent("TriggerEvent", "trigger-topic");

    private final List<Reaction> identified = new CopyOnWriteArrayList<>();
    private final ReactionObserverService reactionObserverService = new ReactionObserverService(this);
    private final ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

    @Override
    public void onReactionIdentified(Reaction reaction) {
        identified.add(reaction);
    }

    ReactionRecorder recorder() {
        return recorder;
    }

    /**
     * Runs the code as the handling of a trigger that completes successfully.
     */
    void inTrigger(Runnable handling) {
        recorder.onTriggerStart(TRIGGER);
        try {
            handling.run();
            recorder.onTriggerHandled();
        } finally {
            recorder.afterTrigger();
        }
    }

    /**
     * @return the reactions identified so far, in the order of their identification
     */
    List<Reaction> identified() {
        return List.copyOf(identified);
    }

    /**
     * @return the FQNs of the actions of the only reaction identified so far
     */
    List<String> actionFqnsOfSingleReaction() {
        if (identified.size() != 1) {
            throw new AssertionError("Expected exactly one identified reaction, but got: " + identified);
        }
        return identified.getFirst().actions().stream().map(Observation::fqn).toList();
    }

    /**
     * @return how often each reaction has been counted since the last call, by reaction id
     */
    Map<String, Integer> countsSinceLastCall() {
        return reactionObserverService.getAndClearCountByReactionId().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }

    /**
     * Forgets the reactions identified so far and the counts. The observer service still knows which reactions
     * it has identified: such a reaction is not identified again. A full reset would need a new recorder, but
     * a Spring context holds on to the one it got as a bean.
     */
    void clear() {
        identified.clear();
        reactionObserverService.getAndClearCountByReactionId();
    }
}
