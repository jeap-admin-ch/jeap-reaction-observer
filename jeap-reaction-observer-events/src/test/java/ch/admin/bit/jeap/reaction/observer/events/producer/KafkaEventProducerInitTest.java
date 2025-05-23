package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.reaction.observer.events.spring.ReactionObserverKafkaConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventProducerInitTest {

    @Mock
    KafkaProperties kafkaProperties;

    @Mock
    ReactionObserverKafkaConfigProperties config;

    @Mock
    KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

    @InjectMocks
    KafkaEventProducer producer;

    @Test
    void init_doesNotThrowWhenSystemAndServiceNamePresent() {
        when(kafkaProperties.getSystemName()).thenReturn("system");
        when(kafkaProperties.getServiceName()).thenReturn("service");

        assertDoesNotThrow(() -> producer.init());
    }

    @Test
    void init_throwsWhenSystemNameMissing() {
        when(kafkaProperties.getSystemName()).thenReturn("");

        assertThrows(IllegalArgumentException.class, () -> producer.init());
    }

    @Test
    void init_throwsWhenServiceNameMissing() {
        when(kafkaProperties.getSystemName()).thenReturn("system");
        when(kafkaProperties.getServiceName()).thenReturn("");

        assertThrows(IllegalArgumentException.class, () -> producer.init());
    }
}
