package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record Observation(ObservationType type, String fqn, SortedMap<String, String> props) {

    public static Observation ofCommand(String messageType, String topicName) {
        return new Observation(ObservationType.COMMAND, messageType, topicProps(topicName));
    }

    public static Observation ofEvent(String messageType, String topicName) {
        return new Observation(ObservationType.EVENT, messageType, topicProps(topicName));
    }

    private static TreeMap<String, String> topicProps(String topicName) {
        return new TreeMap<>(Map.of("topic", topicName));
    }

    ObservationId id() {
        return ObservationIds.create(type, fqn, props);
    }
}
