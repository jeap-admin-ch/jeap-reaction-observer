package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

import java.util.Optional;

public class ReactionRecorder {

    private final ReactionObserverService reactionObserverService;
    /**
     * The state of the thread, present exactly while the thread is inside a trigger: created when the outermost
     * trigger starts, removed when it ends. A thread that is not handling a trigger holds no state.
     */
    private final ThreadLocal<ReactionRecorderState> reactionRecorderState = new ThreadLocal<>();

    public ReactionRecorder(ReactionObserverService reactionObserverService) {
        this.reactionObserverService = reactionObserverService;
    }

    /**
     * Record an action linked to the current trigger if one has been recorded, otherwise publish an action-only
     * reaction.
     */
    public void onAction(Observation action) {
        recordAction(action);
    }

    /**
     * Does the same as {@link #onAction(Observation)} and tells whether the action was recorded.
     *
     * @return false if the action was ignored because the budget of the current trigger for this kind of action
     * (message or HTTP request) is used up, true otherwise
     */
    public boolean recordAction(Observation action) {
        // When inside a trigger, record the action as part of the reaction
        return currentTriggerExecution()
                .map(state -> state.recordAction(action))
                .orElseGet(() -> publishActionOnlyReaction(action));
    }

    /**
     * When not inside a trigger, publish an action-only reaction.
     * If there is no trigger, we cannot group several actions during the execution of the trigger.
     */
    private boolean publishActionOnlyReaction(Observation action) {
        reactionObserverService.reactionObserved(Reaction.actionOnly(action));
        return true;
    }

    /**
     * Record that an action of the current trigger failed in a way that leaves it unknown what the action was, for
     * example an HTTP request without a response. The execution of the current trigger is discarded as a whole:
     * {@link #onTriggerHandled()} will not publish a reaction for it, neither with nor without the actions
     * recorded before or after. A reaction describes how a service reacts in normal operation - an execution
     * disturbed by an operational failure would be reported as a reaction of its own otherwise.
     * <p>
     * The mark applies to the current trigger only (not to an outer trigger when triggers are nested) and lasts
     * until {@link #afterTrigger()}. Does nothing outside a trigger, where the failed action simply is not
     * published as an action-only reaction.
     */
    public void onActionFailed() {
        currentTriggerExecution().ifPresent(ReactionRecorderState::discardCurrentExecution);
    }

    /**
     * Record the occurrence of a trigger
     */
    public void onTriggerStart(Observation trigger) {
        ReactionRecorderState state = reactionRecorderState.get();
        if (state == null) {
            state = new ReactionRecorderState();
            reactionRecorderState.set(state);
        }
        state.enterTrigger(trigger);
    }

    /**
     * Publish the reaction at the end of the current trigger, unless its execution has been discarded, see
     * {@link #onActionFailed()}.
     */
    public void onTriggerHandled() {
        currentTriggerExecution()
                .filter(state -> !state.isCurrentExecutionDiscarded())
                .map(ReactionRecorderState::toReaction)
                .ifPresent(reactionObserverService::reactionObserved);
    }

    /**
     * Cleanup resources created at the start of the current trigger.
     */
    public void afterTrigger() {
        ReactionRecorderState state = reactionRecorderState.get();
        if (state != null && state.exitTrigger()) {
            // The outermost trigger has ended: the thread holds no state any more
            reactionRecorderState.remove();
        }
    }

    /**
     * @return whether the current thread is handling a trigger
     */
    boolean isInsideTrigger() {
        return currentTriggerExecution().isPresent();
    }

    /**
     * @return the state of the trigger execution the current thread is in, empty outside of a trigger
     */
    private Optional<ReactionRecorderState> currentTriggerExecution() {
        return Optional.ofNullable(reactionRecorderState.get());
    }
}
