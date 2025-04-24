package ch.admin.bit.jeap.reaction.observer.events.spring;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("jeap.reaction.observer.events")
@Validated
@Data
public class ReactionObserverKafkaConfigProperties {

    @NotEmpty
    private String reactionIdentifiedTopic;
    @NotEmpty
    private String reactionsObservedTopic;
    /**
     * Defaults to every five minutes in {@link ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler}
     */
    private int observedEventRateSeconds = 300;

    public boolean isObservedEventSchedulingEnabled() {
        return observedEventRateSeconds > 0;
    }
}
