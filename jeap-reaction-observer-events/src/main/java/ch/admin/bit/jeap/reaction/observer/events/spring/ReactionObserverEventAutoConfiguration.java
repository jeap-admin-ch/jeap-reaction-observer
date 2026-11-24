package ch.admin.bit.jeap.reaction.observer.events.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.configuration.ConditionalOnReactionObserverEnabled;
import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.events.producer.KafkaEventProducer;
import ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableConfigurationProperties(ReactionObserverKafkaConfigProperties.class)
@EnableScheduling
@ConditionalOnReactionObserverEnabled
public class ReactionObserverEventAutoConfiguration {

    @Bean
    KafkaEventProducer kafkaEventProducingListener(
            ReactionObserverKafkaConfigProperties config,
            KafkaProperties kafkaProperties,
            KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate,
            Environment environment) {
        // The events report the version of the reaction model the service is configured to observe by
        return new KafkaEventProducer(kafkaProperties, config, kafkaTemplate, ReactionObserverProperties.of(environment).reactionModel());
    }

    @Bean
    ReactionsObservedEventScheduler reactionsObservedEventScheduler(
            ReactionObserverService reactionObserverService, KafkaEventProducer kafkaEventProducer) {
        return new ReactionsObservedEventScheduler(reactionObserverService, kafkaEventProducer);
    }
}
