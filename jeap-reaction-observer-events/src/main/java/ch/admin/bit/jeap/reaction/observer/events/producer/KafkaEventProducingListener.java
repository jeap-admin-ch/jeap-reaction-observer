package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedMessageKey;
import ch.admin.bit.jeap.reaction.observer.events.spring.ReactionObserverKafkaConfigProperties;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;

public class KafkaEventProducingListener implements ReactionIdentifiedListener {

    private final KafkaProperties kafkaProperties;
    private final ReactionObserverKafkaConfigProperties config;
    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

    public KafkaEventProducingListener(KafkaProperties kafkaProperties, ReactionObserverKafkaConfigProperties config,
                                       KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate) {
        this.kafkaProperties = kafkaProperties;
        this.config = config;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void onReactionIdentified(Reaction reaction) {
        ReactionIdentifiedEvent event = ReactionIdentifiedEventBuilder
                .buildEvent(kafkaProperties.getSystemName(), kafkaProperties.getServiceName(), reaction);
        sendSync(event);
    }

    private void sendSync(ReactionIdentifiedEvent event) {
        try {
            AvroMessageKey key = createKey(event);
            kafkaTemplate.send(config.getReactionIdentifiedTopic(), key, event).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ReactionObserverKafkaException.producingEventFailed(e);
        } catch (ExecutionException e) {
            throw ReactionObserverKafkaException.producingEventFailed(e);
        }
    }

    private static AvroMessageKey createKey(ReactionIdentifiedEvent event) {
        return new ReactionIdentifiedMessageKey(event.getPayload().getReactionId());
    }
}
