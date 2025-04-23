package ch.admin.bit.jeap.reaction.observer.events.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.events.producer.KafkaEventProducingListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@EnableConfigurationProperties(ReactionObserverKafkaConfigProperties.class)
public class ReactionObserverEventConfiguration {

    @Bean
    KafkaEventProducingListener kafkaEventProducingListener(
            ReactionObserverKafkaConfigProperties config,
            KafkaProperties kafkaProperties,
            KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate) {
        return new KafkaEventProducingListener(kafkaProperties, config, kafkaTemplate);
    }
}
