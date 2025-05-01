package ch.admin.bit.jeap.reaction.observer.events.spring;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("jeap.reaction.observer.events")
@Validated
@Data
public class ReactionObserverKafkaConfigProperties implements EnvironmentAware {

    private String reactionIdentifiedTopic;

    private String reactionsObservedTopic;

    /**
     * Defaults to every five minutes in {@link ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler}
     */
    private int observedEventRateSeconds = 300;

    private Environment environment;

    public boolean isObservedEventSchedulingEnabled() {
        return observedEventRateSeconds > 0;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (environment.getProperty("jeap.reaction.observer.enabled", Boolean.class, true)) {
            if (reactionIdentifiedTopic == null || reactionIdentifiedTopic.isEmpty()) {
                throw new IllegalArgumentException("jeap.reaction.observer.events.reaction-identified-topic must not be empty");
            }
            if (reactionsObservedTopic == null || reactionsObservedTopic.isEmpty()) {
                throw new IllegalArgumentException("jeap.reaction.observer.events.reactions-observed-topic must not be empty");
            }
        }
    }
}
