package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionRecorderStateTest {

    @Test
    void recordAction() {
        ReactionRecorderState state = new ReactionRecorderState();

        state.enterTrigger(Observation.ofEvent("test", "test"));

        state.recordAction(Observation.ofEvent("action", "action"));
        state.recordAction(Observation.ofEvent("action", "action"));
        state.recordAction(Observation.ofEvent("action2", "action2"));

        Reaction reaction = state.toReaction();

        assertThat(reaction.actions())
                .describedAs("Duplicate actions should not be recorded twice")
                .containsExactly(
                        Observation.ofEvent("action", "action"),
                        Observation.ofEvent("action2", "action2")
                );
    }

    @Test
    void recordAction_recordsAtMost100Actions() {
        ReactionRecorderState state = new ReactionRecorderState();

        state.enterTrigger(Observation.ofEvent("test", "test"));
        for (int i = 0; i < 150; i++) {
            state.recordAction(Observation.ofEvent("action" + i, "action" + i));
        }

        Reaction reaction = state.toReaction();
        assertThat(reaction.actions())
                .describedAs("Should record at most 100 actions")
                .hasSize(100);
    }

    @Test
    void recordAction_reportsWhetherTheActionWasRecorded() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("test", "test"));

        for (int i = 0; i < ReactionRecorderState.MAX_MESSAGING_ACTION_COUNT; i++) {
            assertThat(state.recordAction(Observation.ofEvent("action" + i, "topic"))).isTrue();
        }

        assertThat(state.recordAction(Observation.ofEvent("one-too-many", "topic"))).isFalse();
        assertThat(state.recordAction(Observation.ofEvent("action0", "topic")))
                .describedAs("An action already recorded is not ignored, it is part of the reaction")
                .isTrue();
    }

    /**
     * The same action recorded again is not counted again: repeating one action, for example the same HTTP
     * request in a loop, must not use up the budget of its kind.
     */
    @Test
    void recordAction_duplicatesDoNotUseUpTheBudget() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("test", "test"));

        for (int i = 0; i < 500; i++) {
            assertThat(state.recordAction(Observation.ofEvent("repeated", "topic"))).isTrue();
            assertThat(state.recordAction(Observation.ofCommand("repeated", null, "topic"))).isTrue();
            assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/repeated"))).isTrue();
        }

        // Events and commands share the messaging budget: two of its places are taken, one of the HTTP budget
        for (int i = 0; i < ReactionRecorderState.MAX_MESSAGING_ACTION_COUNT - 2; i++) {
            assertThat(state.recordAction(Observation.ofEvent("action" + i, "topic"))).isTrue();
        }
        for (int i = 0; i < ReactionRecorderState.MAX_HTTP_ACTION_COUNT - 1; i++) {
            assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/" + i))).isTrue();
        }
        assertThat(state.recordAction(Observation.ofEvent("one-too-many", "topic"))).isFalse();
        assertThat(state.recordAction(Observation.ofCommand("one-too-many", null, "topic"))).isFalse();
        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/one-too-many"))).isFalse();
        assertThat(state.toReaction().actions())
                .hasSize(ReactionRecorderState.MAX_MESSAGING_ACTION_COUNT + ReactionRecorderState.MAX_HTTP_ACTION_COUNT);
    }

    /**
     * Every trigger has budgets of its own: a nested trigger starts with full budgets, and what it records
     * does not count for the outer trigger.
     */
    @Test
    void recordAction_nestedTriggerHasBudgetsOfItsOwn() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("outer", "test"));
        for (int i = 0; i < ReactionRecorderState.MAX_HTTP_ACTION_COUNT; i++) {
            assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/outer/" + i))).isTrue();
        }

        state.enterTrigger(Observation.ofEvent("inner", "test"));
        for (int i = 0; i < ReactionRecorderState.MAX_HTTP_ACTION_COUNT; i++) {
            assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/inner/" + i))).isTrue();
        }
        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/inner/one-too-many"))).isFalse();
        state.exitTrigger();

        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/outer/one-too-many"))).isFalse();
        assertThat(state.recordAction(Observation.ofEvent("message", "topic"))).isTrue();
        assertThat(state.toReaction().actions()).hasSize(ReactionRecorderState.MAX_HTTP_ACTION_COUNT + 1);
    }

    @Test
    void recordAction_httpActionsHaveTheirOwnBudget() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("test", "test"));

        for (int i = 0; i < ReactionRecorderState.MAX_HTTP_ACTION_COUNT; i++) {
            assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/" + i))).isTrue();
        }
        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/one-too-many"))).isFalse();
        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/0")))
                .describedAs("An action already recorded is not ignored, it is part of the reaction")
                .isTrue();

        assertThat(state.toReaction().actions())
                .hasSize(ReactionRecorderState.MAX_HTTP_ACTION_COUNT)
                .allMatch(action -> action.type() == ObservationType.HTTP_REQUEST);
    }

    @Test
    void recordAction_manyHttpActionsDoNotPushOutMessageActions() {
        Observation trigger = Observation.ofEvent("test", "test");
        Observation messageBefore = Observation.ofEvent("before", "topic");
        Observation messageAfter = Observation.ofCommand("after", null, "topic");
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(trigger);

        state.recordAction(messageBefore);
        for (int i = 0; i < 150; i++) {
            state.recordAction(Observation.ofHttpRequest("svc GET /api/" + i));
        }
        assertThat(state.recordAction(messageAfter))
                .describedAs("A message sent after many HTTP requests is still recorded")
                .isTrue();

        Reaction reaction = state.toReaction();
        List<Observation> messageActions = reaction.actions().stream()
                .filter(action -> action.type() != ObservationType.HTTP_REQUEST)
                .toList();
        assertThat(messageActions).containsExactly(messageBefore, messageAfter);
        assertThat(new Reaction(trigger, messageActions).id())
                .describedAs("Without its HTTP actions the reaction has the id it had before HTTP requests were recorded")
                .isEqualTo("event:test:0477eae416b3805819c11c90b2c2883b#290ad4def848c5bf5ddb9c051db9c06e");
    }

    @Test
    void recordAction_manyMessageActionsDoNotPushOutHttpActions() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("test", "test"));
        for (int i = 0; i < 150; i++) {
            state.recordAction(Observation.ofEvent("action" + i, "topic"));
        }

        assertThat(state.recordAction(Observation.ofHttpRequest("svc GET /api/x"))).isTrue();

        assertThat(state.toReaction().actions()).hasSize(ReactionRecorderState.MAX_MESSAGING_ACTION_COUNT + 1);
    }

    @Test
    void discardCurrentExecution_marksOnlyTheCurrentTrigger() {
        ReactionRecorderState state = new ReactionRecorderState();
        state.enterTrigger(Observation.ofEvent("outer", "test"));
        state.enterTrigger(Observation.ofEvent("inner", "test"));

        state.discardCurrentExecution();

        assertThat(state.isCurrentExecutionDiscarded()).isTrue();
        state.exitTrigger();
        assertThat(state.isCurrentExecutionDiscarded()).isFalse();
    }

    @Test
    void discardCurrentExecution_outsideTrigger_doesNothing() {
        ReactionRecorderState state = new ReactionRecorderState();

        state.discardCurrentExecution();
        state.enterTrigger(Observation.ofEvent("test", "test"));

        assertThat(state.isCurrentExecutionDiscarded()).isFalse();
    }
}
