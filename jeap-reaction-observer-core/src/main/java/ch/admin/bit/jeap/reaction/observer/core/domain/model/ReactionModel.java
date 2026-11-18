package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The reaction model a service observes by: the rules by which observed behaviour becomes observations and
 * reactions. Each version of the model states which types of observations it knows.
 * <p>
 * A service is configured with the model to use, see docs/configuration.md. The model is not simply the
 * newest one, because a Reaction Observer Service that predates a model version cannot process what that
 * version adds.
 */
public enum ReactionModel {

    /**
     * Events and commands.
     */
    V1(1, EnumSet.of(ObservationType.EVENT, ObservationType.COMMAND)),
    /**
     * Adds HTTP requests.
     */
    V2(2, EnumSet.of(ObservationType.EVENT, ObservationType.COMMAND, ObservationType.HTTP_REQUEST));

    private final int version;
    private final Set<ObservationType> observedTypes;

    ReactionModel(int version, Set<ObservationType> observedTypes) {
        this.version = version;
        this.observedTypes = observedTypes;
    }

    /**
     * @return the model of the version, empty if there is no model of that version
     */
    public static Optional<ReactionModel> ofVersion(int version) {
        return Arrays.stream(values())
                .filter(model -> model.version == version)
                .findFirst();
    }

    /**
     * @return the number of the model version, as the reaction events report it
     */
    public int version() {
        return version;
    }

    /**
     * @return whether observations of the type are part of this model
     */
    public boolean observes(ObservationType type) {
        return observedTypes.contains(type);
    }
}
