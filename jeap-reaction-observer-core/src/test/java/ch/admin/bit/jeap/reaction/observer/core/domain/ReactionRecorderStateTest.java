package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

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
}
