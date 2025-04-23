package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import java.util.SortedMap;

public record Observation(String type, String fqn, SortedMap<String, String> props) {

    ObservationId id() {
        return ObservationIds.create(type, fqn, props);
    }

}
