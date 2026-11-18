package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionModelTest {

    @Test
    void v1_observesMessagesOnly() {
        assertThat(ReactionModel.V1.version()).isEqualTo(1);
        assertThat(ReactionModel.V1.observes(ObservationType.EVENT)).isTrue();
        assertThat(ReactionModel.V1.observes(ObservationType.COMMAND)).isTrue();
        assertThat(ReactionModel.V1.observes(ObservationType.HTTP_REQUEST)).isFalse();
    }

    @Test
    void v2_addsHttpRequests() {
        assertThat(ReactionModel.V2.version()).isEqualTo(2);
        assertThat(ObservationType.values()).allMatch(ReactionModel.V2::observes);
    }

    @ParameterizedTest
    @EnumSource(ReactionModel.class)
    void ofVersion_findsTheModelOfTheVersion(ReactionModel model) {
        assertThat(ReactionModel.ofVersion(model.version())).contains(model);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 3})
    void ofVersion_noModelOfTheVersion_isEmpty(int version) {
        assertThat(ReactionModel.ofVersion(version)).isEmpty();
    }

    /**
     * A model version only adds to what the version before it observes (an additive model version).
     */
    @Test
    void everyVersion_observesWhatTheVersionBeforeItObserves() {
        ReactionModel[] models = ReactionModel.values();
        for (int i = 1; i < models.length; i++) {
            ReactionModel before = models[i - 1];
            ReactionModel model = models[i];
            assertThat(model.version()).isEqualTo(before.version() + 1);
            assertThat(ObservationType.values()).filteredOn(before::observes).allMatch(model::observes);
        }
    }
}
