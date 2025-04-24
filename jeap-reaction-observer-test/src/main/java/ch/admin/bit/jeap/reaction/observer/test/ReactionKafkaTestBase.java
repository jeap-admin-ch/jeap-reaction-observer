package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.kafka.properties.KafkaProperties;
import ch.admin.bit.jeap.messaging.kafka.serde.KafkaAvroSerdeProvider;
import ch.admin.bit.jeap.messaging.kafka.spring.JeapKafkaBeanNames;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Duration;
import java.util.HashMap;

@SpringBootTest
public abstract class ReactionKafkaTestBase extends KafkaIntegrationTestBase {

    @Autowired
    private KafkaProperties kafkaProperties;
    @Autowired
    private ApplicationContext applicationContext;

    @BeforeAll
    static void beforeAll() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
    }

    protected void sendTestEventWithoutRecordingAction(String topic, AvroMessage event) {
        String serdeProviderBeanName = new JeapKafkaBeanNames(kafkaProperties.getDefaultClusterName())
                .getKafkaAvroSerdeProviderBeanName(kafkaProperties.getDefaultClusterName());
        KafkaAvroSerdeProvider kafkaAvroSerdeProvider = (KafkaAvroSerdeProvider) applicationContext.getBean(serdeProviderBeanName);
        byte[] bytes = kafkaAvroSerdeProvider.getValueSerializer().serialize(topic, event);
        createByteArrayKafkaTemplate().send(topic, bytes);
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<byte[], byte[]> createByteArrayKafkaTemplate() {
        ProducerFactory<byte[], byte[]> producerFactory = applicationContext.getBean(ProducerFactory.class);
        var producerFactoryConfig = new HashMap<>(producerFactory.getConfigurationProperties());
        producerFactoryConfig.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerFactoryConfig, this::byteArraySerializer, this::byteArraySerializer));
    }

    private Serializer<byte[]> byteArraySerializer() {
        return new ByteArraySerializer();
    }
}
