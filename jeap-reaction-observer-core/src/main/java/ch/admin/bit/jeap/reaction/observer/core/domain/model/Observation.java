package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import java.util.SortedMap;
import java.util.TreeMap;

public record Observation(ObservationType type, String fqn, SortedMap<String, String> props) {

    public static Observation ofCommand(String messageType) {
        return new Observation(ObservationType.COMMAND, messageType, new TreeMap<>());
    }

    public static Observation ofEvent(String messageType) {
        return new Observation(ObservationType.EVENT, messageType, new TreeMap<>());
    }

    ObservationId id() {
        return ObservationIds.create(type, fqn, props);
    }
}
