package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import lombok.AllArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;

class ReactionRecorderState {

    private final Deque<TriggerInvocationFrame> triggerInvocationFrameStack = new ArrayDeque<>();

    Observation currentTrigger() {
        if (triggerInvocationFrameStack.isEmpty()) {
            return null;
        }
        return triggerInvocationFrameStack.peek().trigger;
    }

    public void notifyReactionPublished() {
        if (!triggerInvocationFrameStack.isEmpty()) {
            triggerInvocationFrameStack.peek().reactionPublished = true;
        }
    }

    public boolean isReactionPublished() {
        return !triggerInvocationFrameStack.isEmpty() && triggerInvocationFrameStack.peek().reactionPublished;
    }

    void enterTrigger(Observation trigger) {
        triggerInvocationFrameStack.push(TriggerInvocationFrame.of(trigger));
    }

    boolean exitTrigger() {
        if (!triggerInvocationFrameStack.isEmpty()) {
            triggerInvocationFrameStack.pop();
        }
        return triggerInvocationFrameStack.isEmpty();
    }

    @AllArgsConstructor
    private static class TriggerInvocationFrame {
        private Observation trigger;
        private boolean reactionPublished;

        static TriggerInvocationFrame of(Observation trigger) {
            return new TriggerInvocationFrame(trigger, false);
        }
    }
}
