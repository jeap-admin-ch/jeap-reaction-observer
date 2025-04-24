package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.*;

class ReactionRecorderTest {
    @Test
    void onActionPublishesReactionWithTriggerAndAction() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));
        Observation action = new Observation(ObservationType.EVENT, "actionFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.onAction(action);

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, action));
    }

    @Test
    void onActionPublishesReactionWithActionOnlyWhenNoTrigger() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation action = new Observation(ObservationType.EVENT, "actionFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onAction(action);

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(null, action));
    }

    @Test
    void onTriggerHandledPublishesReactionWithTriggerOnly() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.onTriggerHandled();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, null));
    }

    @Test
    void onTriggerHandledDoesNotPublishReactionIfAlreadyPublished() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));
        Observation action = new Observation(ObservationType.EVENT, "actionFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.onAction(action);
        recorder.onTriggerHandled();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, action));
        verify(reactionObserverService, never()).reactionObserved(new Reaction(trigger, null));
    }

    @Test
    void afterTriggerCleansUpThreadLocalState() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.afterTrigger();
        recorder.onTriggerHandled();

        verify(reactionObserverService, never()).reactionObserved(any());
    }
}
