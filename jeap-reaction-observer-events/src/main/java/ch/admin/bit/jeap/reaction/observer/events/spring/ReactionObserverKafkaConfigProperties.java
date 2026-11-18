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
     * Defaults to every five minutes in {@link ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler}
     */
    private int observedEventRateSeconds = 300;

    public boolean isObservedEventSchedulingEnabled() {
        return observedEventRateSeconds > 0;
    }

    /**
     * The topics are required whenever these properties exist. They only exist in an active Reaction Observer:
     * the auto-configuration that enables them carries the condition on the switch.
     */
    @PostConstruct
    public void validate() {
        if (reactionIdentifiedTopic == null || reactionIdentifiedTopic.isEmpty()) {
            throw new IllegalArgumentException("jeap.reaction.observer.events.reaction-identified-topic must not be empty");
        }
        if (reactionsObservedTopic == null || reactionsObservedTopic.isEmpty()) {
            throw new IllegalArgumentException("jeap.reaction.observer.events.reactions-observed-topic must not be empty");
        }
    }
}
