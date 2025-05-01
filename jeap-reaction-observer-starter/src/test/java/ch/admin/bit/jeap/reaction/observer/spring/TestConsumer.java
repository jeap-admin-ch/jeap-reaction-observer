package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.awaitility.Awaitility.await;

@Slf4j
@RequiredArgsConstructor
class TestConsumer {

    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    private final List<JmeDeclarationCreatedEvent> consumedEvents = new ArrayList<>();

    @KafkaListener(topics = JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC)
    void onDeclarationCreatedEvent(JmeDeclarationCreatedEvent event) {
        if (event.getPayload().getMessage().contains("reaction")) {
            JmeSimpleTestEvent actionEvent = TestMessages.createJmeSimpleTestEvent("test");
            kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, actionEvent);

            JmeCreateDeclarationCommand commandAction = TestMessages.createJmeCreateDeclarationCommand("test");
            kafkaTemplate.send(JmeCreateDeclarationCommand.TypeRef.DEFAULT_TOPIC, commandAction);
        }

        consumedEvents.add(event);
    }

    void awaitDeclarationCreatedEvent(JmeDeclarationCreatedEvent event) {
        await().until(() -> consumedEvents.stream()
                .anyMatch(e -> e.getIdentity().getId().equals(event.getIdentity().getId())));
    }
}
