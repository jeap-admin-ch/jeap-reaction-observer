package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@EqualsAndHashCode
@ToString
public class Observation {
    private final ObservationType type;
    private final String fqn;
    private final ObservationId id;
    private final SortedMap<String, String> props;

    public Observation(ObservationType type, String fqn, SortedMap<String, String> props) {
        this.type = type;
        this.fqn = fqn;
        this.props = props;
        this.id = ObservationIds.create(type, fqn, props);
    }

    public static Observation ofCommand(String messageType, String topicName) {
        return new Observation(ObservationType.COMMAND, messageType, topicProps(topicName));
    }

    public static Observation ofEvent(String messageType, String topicName) {
        return new Observation(ObservationType.EVENT, messageType, topicProps(topicName));
    }

    private static TreeMap<String, String> topicProps(String topicName) {
        return new TreeMap<>(Map.of("topic", topicName));
    }

    public ObservationType type() {
        return type;
    }

    public ObservationId id() {
        return id;
    }

    public String fqn() {
        return fqn;
    }

    public SortedMap<String, String> props() {
        return props;
    }
}
