package ch.admin.bit.jeap.reaction.observer.events.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.events.producer.KafkaEventProducer;
import ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableConfigurationProperties(ReactionObserverKafkaConfigProperties.class)
@EnableScheduling
@ConditionalOnProperty(name = "jeap.reaction.observer.enabled", havingValue = "true", matchIfMissing = true)
public class ReactionObserverEventAutoConfiguration {

    @Bean
    KafkaEventProducer kafkaEventProducingListener(
            ReactionObserverKafkaConfigProperties config,
            KafkaProperties kafkaProperties,
            KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate) {
        return new KafkaEventProducer(kafkaProperties, config, kafkaTemplate);
    }

    @Bean
    ReactionsObservedEventScheduler reactionsObservedEventScheduler(
            ReactionObserverKafkaConfigProperties props,
            ReactionObserverService reactionObserverService, KafkaEventProducer kafkaEventProducer) {
        return new ReactionsObservedEventScheduler(props, reactionObserverService, kafkaEventProducer);
    }
}
