package ch.admin.bit.jeap.reaction.observer.messaging.spring;

import ch.admin.bit.jeap.messaging.kafka.KafkaConfiguration;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.messaging.ObserverKafkaMessageCallback;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

@AutoConfiguration(before = KafkaConfiguration.class)
public class MessagingObserverAutoConfiguration {

    /**
     * @param reactionRecorder the reaction recorder - this is a lazy dependency to avoid circular dependencies, the
     *                         kafka infrastructure uses the callback, and the callback uses the reaction recorder,
     *                         which is using the kafka infrastructure to send events.
     */
    @Bean
    public ObserverKafkaMessageCallback jeapKafkaMessageCallback(@Lazy ReactionRecorder reactionRecorder) {
        return new ObserverKafkaMessageCallback(reactionRecorder);
    }
}
