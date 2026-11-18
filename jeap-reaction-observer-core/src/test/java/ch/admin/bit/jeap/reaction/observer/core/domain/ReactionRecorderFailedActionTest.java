package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ReactionRecorderFailedActionTest {

    private static final Observation TRIGGER = Observation.ofEvent("TriggerEvent", "trigger-topic");
    private static final Observation MESSAGE_ACTION = Observation.ofEvent("ActionEvent", "action-topic");
    private static final Observation HTTP_ACTION = Observation.ofHttpRequest("partner-service GET /api/partners/{}");

    private final ReactionObserverService reactionObserverService = mock(ReactionObserverService.class);
    private final ReactionRecorder recorder = new ReactionRecorder(reactionObserverService);

    @Test
    void onActionFailed_insideTrigger_noReactionIsPublished() {
        recorder.onTriggerStart(TRIGGER);
        recorder.onActionFailed();
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verifyNoInteractions(reactionObserverService);
    }

    @Test
    void onActionFailed_discardsTheActionsRecordedBeforeAndAfter() {
        recorder.onTriggerStart(TRIGGER);
        recorder.onAction(MESSAGE_ACTION);
        recorder.onActionFailed();
        // A later successful action, for example the retry of the failed HTTP request, does not undo the mark
        recorder.onAction(HTTP_ACTION);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verifyNoInteractions(reactionObserverService);
    }

    @Test
    void onActionFailed_theNextExecutionOfTheSameTriggerIsPublished() {
        recorder.onTriggerStart(TRIGGER);
        recorder.onActionFailed();
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        recorder.onTriggerStart(TRIGGER);
        recorder.onAction(MESSAGE_ACTION);
        recorder.onAction(HTTP_ACTION);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService).reactionObserved(new Reaction(TRIGGER, List.of(MESSAGE_ACTION, HTTP_ACTION)));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void onActionFailed_inNestedTrigger_discardsOnlyTheInnerExecution() {
        Observation innerTrigger = Observation.ofEvent("InnerEvent", "inner-topic");

        recorder.onTriggerStart(TRIGGER);
        recorder.onAction(MESSAGE_ACTION);
        recorder.onTriggerStart(innerTrigger);
        recorder.onActionFailed();
        recorder.onTriggerHandled();
        recorder.afterTrigger();
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService).reactionObserved(new Reaction(TRIGGER, List.of(MESSAGE_ACTION)));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void onActionFailed_outsideTrigger_publishesNothingAndDoesNotAffectLaterActionsOrTriggers() {
        recorder.onActionFailed();
        verifyNoInteractions(reactionObserverService);

        recorder.onAction(HTTP_ACTION);
        recorder.onTriggerStart(TRIGGER);
        recorder.onTriggerHandled();
        recorder.afterTrigger();

        verify(reactionObserverService).reactionObserved(Reaction.actionOnly(HTTP_ACTION));
        verify(reactionObserverService).reactionObserved(new Reaction(TRIGGER, List.of()));
        verifyNoMoreInteractions(reactionObserverService);
    }

    @Test
    void recordAction_reportsWhetherTheActionWasRecorded() {
        assertThat(recorder.recordAction(HTTP_ACTION))
                .describedAs("Outside a trigger an action always becomes an action-only reaction")
                .isTrue();

        recorder.onTriggerStart(TRIGGER);
        for (int i = 0; i < ReactionRecorderState.MAX_HTTP_ACTION_COUNT; i++) {
            assertThat(recorder.recordAction(Observation.ofHttpRequest("svc GET /api/" + i))).isTrue();
        }
        assertThat(recorder.recordAction(Observation.ofHttpRequest("svc GET /api/one-too-many"))).isFalse();
        assertThat(recorder.recordAction(MESSAGE_ACTION)).isTrue();
        recorder.afterTrigger();
    }

    /**
     * A thread holds recorder state only while it handles a trigger. Actions and failed actions outside of a
     * trigger - as on every worker thread making an HTTP call - must not leave state behind.
     */
    @Test
    void threadHoldsStateOnlyWhileInsideATrigger() {
        recorder.onAction(HTTP_ACTION);
        recorder.recordAction(MESSAGE_ACTION);
        recorder.onActionFailed();
        recorder.onTriggerHandled();
        recorder.afterTrigger();
        assertThat(recorder.isInsideTrigger()).isFalse();

        recorder.onTriggerStart(TRIGGER);
        assertThat(recorder.isInsideTrigger()).isTrue();
        recorder.onTriggerStart(Observation.ofEvent("InnerEvent", "inner-topic"));
        recorder.afterTrigger();
        assertThat(recorder.isInsideTrigger())
                .describedAs("Still inside the outer trigger")
                .isTrue();
        recorder.afterTrigger();
        assertThat(recorder.isInsideTrigger()).isFalse();
    }

    @Test
    void recordAction_recordsLikeOnAction() {
        recorder.onTriggerStart(TRIGGER);
        recorder.onAction(MESSAGE_ACTION);
        recorder.recordAction(HTTP_ACTION);
        recorder.onTriggerHandled();
        recorder.afterTrigger();
        recorder.recordAction(HTTP_ACTION);

        verify(reactionObserverService).reactionObserved(new Reaction(TRIGGER, List.of(MESSAGE_ACTION, HTTP_ACTION)));
        verify(reactionObserverService).reactionObserved(Reaction.actionOnly(HTTP_ACTION));
        verifyNoMoreInteractions(reactionObserverService);
    }
}
