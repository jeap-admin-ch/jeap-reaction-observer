package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
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
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, List.of(action)));
    }

    @Test
    void onActionPublishesReactionWithTriggerAndActions() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));
        Observation action1 = new Observation(ObservationType.EVENT, "actionFqn1", new TreeMap<>(Map.of("key", "value")));
        Observation action2 = new Observation(ObservationType.EVENT, "actionFqn2", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.onAction(action1);
        recorder.onAction(action2);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, List.of(action1, action2)));
    }

    @Test
    void onActionPublishesReactionWithActionOnlyWhenNoTrigger() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation action1 = new Observation(ObservationType.EVENT, "actionFqn1", new TreeMap<>(Map.of("key", "value")));
        Observation action2 = new Observation(ObservationType.EVENT, "actionFqn2", new TreeMap<>(Map.of("key", "value")));

        recorder.onAction(action1);
        recorder.onAction(action2);

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(null, List.of(action1)));
        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(null, List.of(action2)));
    }

    @Test
    void onTriggerHandledPublishesReactionWithTriggerOnly() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation trigger = new Observation(ObservationType.EVENT, "triggerFqn", new TreeMap<>(Map.of("key", "value")));

        recorder.onTriggerStart(trigger);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, List.of()));
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
        recorder.afterTrigger();

        verify(reactionObserverService, times(1)).reactionObserved(new Reaction(trigger, List.of(action)));
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
        recorder.afterTrigger();

        verify(reactionObserverService, never()).reactionObserved(any());
    }

    @Test
    void reentrantTriggerIsHandledCorrectly() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation outerTrigger = new Observation(ObservationType.EVENT, "outerTriggerFqn", new TreeMap<>());
        Observation innerTrigger = new Observation(ObservationType.EVENT, "innerTriggerFqn", new TreeMap<>());

        // Start the outer trigger, invoke
        recorder.onTriggerStart(outerTrigger);

        // Start the inner trigger (nested)
        recorder.onTriggerStart(innerTrigger);

        // Handle the inner trigger
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        // Handle the outer trigger
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        // Verify that the inner trigger was published first
        verify(reactionObserverService).reactionObserved(new Reaction(innerTrigger, null));

        // Verify that the outer trigger was published after the inner trigger
        verify(reactionObserverService).reactionObserved(new Reaction(outerTrigger, null));
    }

    @Test
    void reentrantTriggerIsHandledCorrectly_withActions() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        Observation outerTrigger = new Observation(ObservationType.EVENT, "outerTrigger", new TreeMap<>());
        Observation outerActionBefore = new Observation(ObservationType.EVENT, "outerActionBefore", new TreeMap<>());
        Observation outerActionAfter = new Observation(ObservationType.EVENT, "outerActionAfter", new TreeMap<>());
        Observation innerTrigger = new Observation(ObservationType.EVENT, "innerTrigger", new TreeMap<>());
        Observation innerAction = new Observation(ObservationType.EVENT, "innerAction", new TreeMap<>());
        Observation nonTriggerAction = new Observation(ObservationType.EVENT, "nonTriggerAction", new TreeMap<>());

        // Record an action outside any trigger context
        recorder.onAction(nonTriggerAction);

        // Start the outer trigger, invoke the action before entering the inner trigger
        recorder.onTriggerStart(outerTrigger);
        recorder.onAction(outerActionBefore);

        // Start the inner trigger (nested)
        recorder.onTriggerStart(innerTrigger);
        recorder.onAction(innerAction);

        // Exit the inner trigger
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        // Invoke action in context of the outer trigger and exit the trigger
        recorder.onAction(outerActionAfter);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        // Record an action outside any trigger context after all triggers again
        recorder.onAction(nonTriggerAction);

        // Verify the reaction observations triggered by the actions
        InOrder orderVerifier = Mockito.inOrder(reactionObserverService);
        orderVerifier.verify(reactionObserverService).reactionObserved(new Reaction(null, List.of(nonTriggerAction)));
        orderVerifier.verify(reactionObserverService).reactionObserved(new Reaction(innerTrigger, List.of(innerAction)));
        orderVerifier.verify(reactionObserverService).reactionObserved(new Reaction(outerTrigger, List.of(outerActionBefore, outerActionAfter)));
        orderVerifier.verify(reactionObserverService).reactionObserved(new Reaction(null, List.of(nonTriggerAction)));
    }
}
