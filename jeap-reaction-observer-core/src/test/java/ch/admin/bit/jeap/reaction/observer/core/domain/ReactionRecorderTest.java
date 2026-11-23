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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        verifyNoMoreInteractions(reactionObserverService);
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
        verifyNoMoreInteractions(reactionObserverService);
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
        verifyNoMoreInteractions(reactionObserverService);
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
        verifyNoMoreInteractions(reactionObserverService);
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

        verifyNoMoreInteractions(reactionObserverService);
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
        orderVerifier.verifyNoMoreInteractions();
    }

    @Test
    void currentTriggerHasActions_tellsWhetherTheCurrentTriggerHasRecordedAnAction() {
        ReactionRecorder recorder = new ReactionRecorder(mock(ReactionObserverService.class));
        Observation trigger = Observation.ofHttpRequest("service GET /api/things");
        Observation action = Observation.ofEvent("ActionEvent", "action-topic");

        assertThat(recorder.currentTriggerHasActions()).describedAs("outside a trigger").isFalse();

        recorder.onTriggerStart(trigger);
        assertThat(recorder.currentTriggerHasActions()).describedAs("inside a trigger without actions").isFalse();
        recorder.onAction(action);
        assertThat(recorder.currentTriggerHasActions()).describedAs("inside a trigger with an action").isTrue();
        recorder.afterTrigger();

        assertThat(recorder.currentTriggerHasActions()).describedAs("after the trigger").isFalse();
    }

    @Test
    void currentTriggerHasActions_inNestedTrigger_tellsAboutTheInnerTriggerOnly() {
        ReactionRecorder recorder = new ReactionRecorder(mock(ReactionObserverService.class));
        Observation outerTrigger = Observation.ofEvent("OuterEvent", "outer-topic");
        Observation innerTrigger = Observation.ofHttpRequest("service GET /api/things");

        recorder.onTriggerStart(outerTrigger);
        recorder.onAction(Observation.ofEvent("ActionEvent", "action-topic"));
        recorder.onTriggerStart(innerTrigger);
        assertThat(recorder.currentTriggerHasActions()).describedAs("the inner trigger has no action").isFalse();
        recorder.afterTrigger();
        assertThat(recorder.currentTriggerHasActions()).describedAs("the outer trigger has an action").isTrue();
        recorder.afterTrigger();
    }

    @Test
    void abandonTrigger_publishesTheActionsAsActionOnlyReactions_andNoReactionOfTheTrigger() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation firstAction = Observation.ofEvent("FirstEvent", "action-topic");
        Observation secondAction = Observation.ofHttpRequest("other-service GET /api/things");

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(firstAction);
        recorder.onAction(secondAction);
        verifyNoInteractions(reactionObserverService);
        recorder.abandonTrigger();

        InOrder inOrder = Mockito.inOrder(reactionObserverService);
        inOrder.verify(reactionObserverService).reactionObserved(Reaction.actionOnly(firstAction));
        inOrder.verify(reactionObserverService).reactionObserved(Reaction.actionOnly(secondAction));
        inOrder.verifyNoMoreInteractions();
        assertThat(recorder.isInsideTrigger()).isFalse();
    }

    /**
     * Without a trigger every occurrence of an action is published. A trigger keeps an action once, however
     * often it occurs - but an abandoned trigger must not change what is counted.
     */
    @Test
    void abandonTrigger_actionRecordedSeveralTimes_isPublishedAsOften() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation repeatedAction = Observation.ofEvent("RepeatedEvent", "action-topic");
        Observation singleAction = Observation.ofEvent("SingleEvent", "action-topic");

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(repeatedAction);
        recorder.onAction(singleAction);
        recorder.onAction(repeatedAction);
        recorder.onAction(repeatedAction);
        recorder.abandonTrigger();

        verify(reactionObserverService, times(3)).reactionObserved(Reaction.actionOnly(repeatedAction));
        verify(reactionObserverService, times(1)).reactionObserved(Reaction.actionOnly(singleAction));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void abandonTrigger_insideAnAbandonedTrigger_keepsHowOftenAnActionWasRecorded() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation action = Observation.ofEvent("RepeatedEvent", "action-topic");

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/outer"));
        recorder.onAction(action);
        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/inner"));
        recorder.onAction(action);
        recorder.onAction(action);
        recorder.abandonTrigger();
        recorder.abandonTrigger();

        verify(reactionObserverService, times(3)).reactionObserved(Reaction.actionOnly(action));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void triggerHandled_actionRecordedSeveralTimes_isAnActionOfTheReactionOnce() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation trigger = Observation.ofHttpRequest("service GET /api/things");
        Observation action = Observation.ofEvent("RepeatedEvent", "action-topic");

        recorder.onTriggerStart(trigger);
        recorder.onAction(action);
        recorder.onAction(action);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService).reactionObserved(new Reaction(trigger, List.of(action)));
        verifyNoMoreInteractions(reactionObserverService);
    }

    /**
     * The actions of a trigger are limited per kind. An action over the limit is not kept, so it cannot be
     * published when the trigger is abandoned.
     */
    @Test
    void abandonTrigger_moreActionsThanATriggerKeeps_theActionsOverTheLimitAreNotPublished() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        int limit = ReactionRecorderState.MAX_MESSAGING_ACTION_COUNT;

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        for (int i = 0; i <= limit; i++) {
            recorder.onAction(Observation.ofEvent("Event" + i, "action-topic"));
        }
        recorder.abandonTrigger();

        verify(reactionObserverService, times(limit)).reactionObserved(any());
        verify(reactionObserverService, never())
                .reactionObserved(Reaction.actionOnly(Observation.ofEvent("Event" + limit, "action-topic")));
    }

    @Test
    void abandonTrigger_withoutActions_publishesNothing() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.abandonTrigger();
        recorder.abandonTrigger();

        verifyNoInteractions(reactionObserverService);
        assertThat(recorder.isInsideTrigger()).isFalse();
    }

    @Test
    void abandonTrigger_insideAnotherTrigger_theActionsBecomeActionsOfTheOuterTrigger() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation outerTrigger = Observation.ofEvent("OuterEvent", "outer-topic");
        Observation outerAction = Observation.ofEvent("OuterAction", "action-topic");
        Observation innerAction = Observation.ofEvent("InnerAction", "action-topic");

        recorder.onTriggerStart(outerTrigger);
        recorder.onAction(outerAction);
        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(innerAction);
        recorder.abandonTrigger();
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService).reactionObserved(new Reaction(outerTrigger, List.of(outerAction, innerAction)));
        verifyNoMoreInteractions(reactionObserverService);
        assertThat(recorder.isInsideTrigger()).isFalse();
    }

    /**
     * Without the abandoned trigger, the failed action would have discarded the execution of the outer trigger.
     */
    @Test
    void abandonTrigger_afterAFailedAction_insideAnotherTrigger_discardsTheExecutionOfTheOuterTrigger() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        recorder.onTriggerStart(Observation.ofEvent("OuterEvent", "outer-topic"));
        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(Observation.ofEvent("InnerAction", "action-topic"));
        recorder.onActionFailed();
        recorder.abandonTrigger();
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verifyNoInteractions(reactionObserverService);
    }

    /**
     * Without the abandoned trigger, the action would have been published right away, and the failed action
     * would not have been published.
     */
    @Test
    void abandonTrigger_afterAFailedAction_publishesTheActionsRecorded() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);
        Observation action = Observation.ofEvent("ActionEvent", "action-topic");

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(action);
        recorder.onActionFailed();
        recorder.abandonTrigger();

        verify(reactionObserverService).reactionObserved(Reaction.actionOnly(action));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void abandonTrigger_publishingFails_theTriggerIsEndedAllTheSame() {
        ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
        doThrow(new IllegalStateException("test")).when(reactionObserverService).reactionObserved(any());
        ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

        recorder.onTriggerStart(Observation.ofHttpRequest("service GET /api/things"));
        recorder.onAction(Observation.ofEvent("ActionEvent", "action-topic"));

        assertThatThrownBy(recorder::abandonTrigger).isInstanceOf(IllegalStateException.class);
        assertThat(recorder.isInsideTrigger()).isFalse();
    }
}
