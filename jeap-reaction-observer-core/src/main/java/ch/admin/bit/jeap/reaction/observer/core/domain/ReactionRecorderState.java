package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
class ReactionRecorderState {

    private static final int MAX_ACTION_COUNT = 100;
    private static boolean warningLogged = false;

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
            actions = List.copyOf(triggerInvocationFrameStack.peek().actions);
        }
        return new Reaction(currentTrigger(), actions);
    }

    @AllArgsConstructor
    private static class TriggerInvocationFrame {

        private Observation trigger;
        // Using LinkedHashSet to maintain the order of actions as they are recorded, but avoiding duplicates.
        // Duplicates might occur if the same action is recorded multiple times within the same trigger invocation,
        // for example due to events that trigger some sort of scheduler that might produce the same action many times.
        private SequencedSet<Observation> actions;

        void recordAction(Observation action) {
            if (actions.size() >= MAX_ACTION_COUNT) {
                logWarningOnce(trigger);
                return;
            }

            actions.add(action);
        }

        private static void logWarningOnce(Observation trigger) {
            if (!warningLogged) {
                log.warn("Maximum action count of {} reached for trigger '{}'. Further actions will be ignored. This is logged only once.", MAX_ACTION_COUNT, trigger);
                warningLogged = true;
            }
        }

        static TriggerInvocationFrame of(Observation trigger) {
            return new TriggerInvocationFrame(trigger, new LinkedHashSet<>());
        }
    }
}
