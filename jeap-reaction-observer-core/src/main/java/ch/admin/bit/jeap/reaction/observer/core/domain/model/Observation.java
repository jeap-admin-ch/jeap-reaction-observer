package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record Observation(ObservationId id, ObservationType type,
                          String fqn,
                          SortedMap<String, String> props) {

    public Observation(ObservationType type, String fqn, SortedMap<String, String> props) {
        this(ObservationIds.create(type, fqn, props), type, fqn, props);
    }

    public static Observation ofCommand(String messageType, String variant, String topicName) {
        return new Observation(ObservationType.COMMAND, fqn(messageType, variant), topicProps(topicName));
    }

    public static Observation ofEvent(String messageType, String variant, String topicName) {
        return new Observation(ObservationType.EVENT, fqn(messageType, variant), topicProps(topicName));
    }

    public static Observation ofEvent(String messageType, String topicName) {
        return ofEvent(messageType, null, topicName);
    }

    private static TreeMap<String, String> topicProps(String topicName) {
        return new TreeMap<>(Map.of("topic", topicName));
    }

    private static String fqn(String messageType, String variant) {
        return variant == null || variant.isBlank() ? messageType : messageType + "/" + sanitizeVariant(variant);
    }

    private static String sanitizeVariant(String input) {
        return input.replace("/", "_");
    }

}
