package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
class ReactionRecorderState {

    /**
     * Maximum number of messaging actions (events and commands) recorded per trigger.
     */
    static final int MAX_MESSAGING_ACTION_COUNT = 100;
    /**
     * Maximum number of HTTP request actions recorded per trigger.
     */
    static final int MAX_HTTP_ACTION_COUNT = 100;
    private static final Set<ActionBudget> WARNINGS_LOGGED = ConcurrentHashMap.newKeySet();

    private final Deque<TriggerInvocationFrame> triggerInvocationFrameStack = new ArrayDeque<>();

    Observation currentTrigger() {
        if (triggerInvocationFrameStack.isEmpty()) {
            return null;
        }
        return triggerInvocationFrameStack.peek().trigger;
    }

    void enterTrigger(Observation trigger) {
        triggerInvocationFrameStack.push(new TriggerInvocationFrame(trigger));
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

    /**
     * @return false if the action was ignored because the budget of the current trigger for this kind of action is
     * used up, true otherwise
     */
    public boolean recordAction(Observation action) {
        if (triggerInvocationFrameStack.isEmpty()) {
            return true;
        }
        return triggerInvocationFrameStack.peek().recordAction(action);
    }

    /**
     * Marks the execution of the current trigger as discarded. The mark stays until the trigger is exited.
     */
    void discardCurrentExecution() {
        if (!triggerInvocationFrameStack.isEmpty()) {
            triggerInvocationFrameStack.peek().discarded = true;
        }
    }

    boolean isCurrentExecutionDiscarded() {
        return !triggerInvocationFrameStack.isEmpty() && triggerInvocationFrameStack.peek().discarded;
    }

    public Reaction toReaction() {
        List<Observation> actions = List.of();
        if (!triggerInvocationFrameStack.isEmpty()) {
            actions = List.copyOf(triggerInvocationFrameStack.peek().actions);
        }
        return new Reaction(currentTrigger(), actions);
    }

    /**
     * The actions of a trigger are limited per kind of interface. Each kind has a budget of its own, so that many
     * actions of one kind never push an action of another kind out of a reaction.
     */
    private enum ActionBudget {
        MESSAGING("messaging", MAX_MESSAGING_ACTION_COUNT),
        HTTP("HTTP request", MAX_HTTP_ACTION_COUNT);

        private final String description;
        private final int maxActionCount;

        ActionBudget(String description, int maxActionCount) {
            this.description = description;
            this.maxActionCount = maxActionCount;
        }

        /**
         * A new observation type does not compile until it is assigned to a budget here.
         */
        static ActionBudget of(ObservationType type) {
            return switch (type) {
                case EVENT, COMMAND -> MESSAGING;
                case HTTP_REQUEST -> HTTP;
            };
        }

        void logWarningOnce(Observation trigger) {
            if (WARNINGS_LOGGED.add(this)) {
                log.warn("Maximum {} action count of {} reached for trigger '{}'. Further {} actions will be ignored. " +
                        "This is logged only once.", description, maxActionCount, trigger, description);
            }
        }
    }

    private static class TriggerInvocationFrame {

        private final Observation trigger;
        // Using LinkedHashSet to maintain the order of actions as they are recorded, but avoiding duplicates.
        // Duplicates might occur if the same action is recorded multiple times within the same trigger invocation,
        // for example due to events that trigger some sort of scheduler that might produce the same action many times.
        private final SequencedSet<Observation> actions = new LinkedHashSet<>();
        private final Map<ActionBudget, Integer> actionCounts = new EnumMap<>(ActionBudget.class);
        private boolean discarded = false;

        TriggerInvocationFrame(Observation trigger) {
            this.trigger = trigger;
        }

        boolean recordAction(Observation action) {
            if (actions.contains(action)) {
                return true;
            }
            ActionBudget budget = ActionBudget.of(action.type());
            int actionCount = actionCounts.getOrDefault(budget, 0);
            if (actionCount >= budget.maxActionCount) {
                budget.logWarningOnce(trigger);
                return false;
            }
            actions.add(action);
            actionCounts.put(budget, actionCount + 1);
            return true;
        }
    }
}
