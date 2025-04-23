package ch.admin.bit.jeap.reaction.observer.events.spring;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("jeap.reaction.observer.kafka")
@Validated
@Data
public class ReactionObserverKafkaConfigProperties {

    @NotEmpty
    private String reactionIdentifiedTopic;
}
