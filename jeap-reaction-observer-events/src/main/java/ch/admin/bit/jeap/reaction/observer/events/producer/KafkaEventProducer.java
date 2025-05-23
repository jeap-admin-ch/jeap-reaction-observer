package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedMessageKey;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.events.spring.ReactionObserverKafkaConfigProperties;
import com.fasterxml.uuid.Generators;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class KafkaEventProducer implements ReactionIdentifiedListener {

    private final KafkaProperties kafkaProperties;
    private final ReactionObserverKafkaConfigProperties config;
    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    private final UUID serviceInstanceIdentifier = Generators.timeBasedEpochGenerator().generate();

    public KafkaEventProducer(KafkaProperties kafkaProperties, ReactionObserverKafkaConfigProperties config,
                              KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate) {
        this.kafkaProperties = kafkaProperties;
        this.config = config;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void onReactionIdentified(Reaction reaction) {
        ReactionIdentifiedEvent event = ReactionIdentifiedEventBuilder
                .buildEvent(kafkaProperties.getSystemName(), kafkaProperties.getServiceName(), reaction);
        sendSync(config.getReactionIdentifiedTopic(), createKey(reaction), event);
    }

    public void sendReactionObservedEvent(Map<String, AtomicInteger> countByReactionId, Instant from, Instant to) {
        ReactionsObservedEvent event = new ReactionsObservedEventBuilder(kafkaProperties.getSystemName(), kafkaProperties.getServiceName())
                .serviceInstanceIdentifier(serviceInstanceIdentifier)
                .countByReactionId(countByReactionId)
                .timeframe(from, to)
                .build();
        sendSync(config.getReactionsObservedTopic(), null, event);
    }

    private void sendSync(String topic, AvroMessageKey key, AvroMessage message) {
        try {
            kafkaTemplate.send(topic, key, message).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ReactionObserverKafkaException.producingEventFailed(e);
        } catch (ExecutionException e) {
            throw ReactionObserverKafkaException.producingEventFailed(e);
        }
    }

    private static AvroMessageKey createKey(Reaction reaction) {
        return new ReactionIdentifiedMessageKey(reaction.id());
    }
}
