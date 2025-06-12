package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

public class ReactionRecorder {

    private final ReactionObserverService reactionObserverService;
    private final ThreadLocal<ReactionRecorderState> reactionRecorderState = ThreadLocal.withInitial(ReactionRecorderState::new);

    public ReactionRecorder(ReactionObserverService reactionObserverService) {
        this.reactionObserverService = reactionObserverService;
    }

    /**
     * Record an action linked to the current trigger if one has been recorded, otherwise publish an action-only
     * reaction.
     */
    public void onAction(Observation action) {
        ReactionRecorderState state = reactionRecorderState.get();
        if (state.isInsideTrigger()) {
            // When inside a trigger, record the action as part of the reaction
            state.recordAction(action);
        } else {
            // When not inside a trigger, publish an action-only reaction
            // If there is no trigger, we cannot group several actions during the execution of the trigger.
            Reaction reaction = Reaction.actionOnly(action);
            reactionObserverService.reactionObserved(reaction);
        }
    }

    /**
     * Record the occurrence of a trigger
     */
    public void onTriggerStart(Observation trigger) {
        ReactionRecorderState state = reactionRecorderState.get();
        state.enterTrigger(trigger);
    }

    /**
     * Publish the reaction at the end of the current trigger
     */
    public void onTriggerHandled() {
        ReactionRecorderState state = reactionRecorderState.get();
        if (state.isInsideTrigger()) {
            Reaction reaction = state.toReaction();
            reactionObserverService.reactionObserved(reaction);
        }
    }

    /**
     * Cleanup resources created at the start of the current trigger.
     */
    public void afterTrigger() {
        if (reactionRecorderState.get().exitTrigger()) {
            reactionRecorderState.remove();
        }
    }
}
