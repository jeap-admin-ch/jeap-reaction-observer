package ch.admin.bit.jeap.reaction.observer.events.spring;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("jeap.reaction.observer.events")
@Validated
@Data
public class ReactionObserverKafkaConfigProperties {

    private String reactionIdentifiedTopic;

    private String reactionsObservedTopic;

    /**
     * The interval in seconds at which the
     * {@link ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler} publishes the
     * counts, five minutes by default. At least 1: the publication of the counts cannot be switched off.
     * Intervals of seconds only make sense in tests and demos: in operation, choose minutes.
     */
    private int observedEventRateSeconds = 300;

    /**
     * The topics are required whenever these properties exist. They only exist in an active Reaction Observer:
     * the auto-configuration that enables them carries the condition on the switch. An interval of less than a
     * second fails the start here, with the name of the property: the scheduler would fail it later, without.
     */
    @PostConstruct
    public void validate() {
        if (reactionIdentifiedTopic == null || reactionIdentifiedTopic.isEmpty()) {
            throw new IllegalArgumentException("jeap.reaction.observer.events.reaction-identified-topic must not be empty");
        }
        if (reactionsObservedTopic == null || reactionsObservedTopic.isEmpty()) {
            throw new IllegalArgumentException("jeap.reaction.observer.events.reactions-observed-topic must not be empty");
        }
        if (observedEventRateSeconds < 1) {
            throw new IllegalArgumentException("jeap.reaction.observer.events.observed-event-rate-seconds must be at least 1, but is " +
                    observedEventRateSeconds);
        }
    }
}
