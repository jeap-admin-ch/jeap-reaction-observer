package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.AllArgsConstructor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

class ReactionRecorderState {

    private final Deque<TriggerInvocationFrame> triggerInvocationFrameStack = new ArrayDeque<>();

    boolean isInsideTrigger() {
        return !triggerInvocationFrameStack.isEmpty();
    }

    Observation currentTrigger() {
        if (triggerInvocationFrameStack.isEmpty()) {
            return null;
        }
        return triggerInvocationFrameStack.peek().trigger;
    }

    void enterTrigger(Observation trigger) {
        triggerInvocationFrameStack.push(TriggerInvocationFrame.of(trigger));
    }

    /**
     * @return true if the trigger stack is empty after popping the current trigger, meaning that we are no longer
     * inside a trigger.
     */
    boolean exitTrigger() {
        if (!triggerInvocationFrameStack.isEmpty()) {
            triggerInvocationFrameStack.pop();
        }
        return triggerInvocationFrameStack.isEmpty();
    }

    public void recordAction(Observation action) {
        if (!triggerInvocationFrameStack.isEmpty()) {
            triggerInvocationFrameStack.peek().recordAction(action);
        }
    }

    public Reaction toReaction() {
        List<Observation> actions = List.of();
        if (!triggerInvocationFrameStack.isEmpty()) {
            actions = triggerInvocationFrameStack.peek().actions;
        }
        return new Reaction(currentTrigger(), actions);
    }

    @AllArgsConstructor
    private static class TriggerInvocationFrame {
        private Observation trigger;
        private List<Observation> actions;

        void recordAction(Observation action) {
            actions.add(action);
        }

        static TriggerInvocationFrame of(Observation trigger) {
            return new TriggerInvocationFrame(trigger, new ArrayList<>());
        }
    }
}
