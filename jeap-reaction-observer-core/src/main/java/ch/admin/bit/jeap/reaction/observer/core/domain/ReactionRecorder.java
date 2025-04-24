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
     * Publish a reaction (either including the trigger if one has been recorded, or an action-only reaction).
     */
    public void onAction(Observation action) {
        ReactionRecorderState state = reactionRecorderState.get();
        Observation trigger = state.getTrigger();
        Reaction reaction = new Reaction(trigger, action);
        reactionObserverService.reactionObserved(reaction);
        state.setReactionPublished(true);
    }

    /**
     * Record the occurrence of a trigger
     */
    public void onTriggerStart(Observation trigger) {
        ReactionRecorderState state = reactionRecorderState.get();
        state.setTrigger(trigger);
    }

    /**
     * Publish the current trigger unless it has already been published by an action occuring in the meantime
     */
    public void onTriggerHandled() {
        ReactionRecorderState state = reactionRecorderState.get();
        if (state.getTrigger() != null && !state.isReactionPublished()) {
            Reaction reaction = new Reaction(state.getTrigger(), null);
            reactionObserverService.reactionObserved(reaction);
            state.setReactionPublished(true);
        }
    }

    /**
     * Cleanup resources created at the start of the current trigger.
     */
    public void afterTrigger() {
        reactionRecorderState.remove();
    }
}
