package ch.admin.bit.jeap.reaction.observer.messaging;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class TestConsumer {

    @Autowired
    private KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

    @TestKafkaListener(topics = JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC)
    void onDeclarationCreatedEvent(JmeDeclarationCreatedEvent event) {
        log.info("Received JmeDeclarationCreatedEvent event: {}", event);

        if (event.getPayload().getMessage().contains("reaction")) {
            JmeSimpleTestEvent actionEvent = TestMessages.createJmeSimpleTestEvent("test");
            kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, actionEvent);

            JmeCreateDeclarationCommand commandAction = TestMessages.createJmeCreateDeclarationCommand("test");
            kafkaTemplate.send(JmeCreateDeclarationCommand.TypeRef.DEFAULT_TOPIC, commandAction);
        }
    }
}
