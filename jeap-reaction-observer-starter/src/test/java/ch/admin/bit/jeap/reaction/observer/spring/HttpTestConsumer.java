package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;

/**
 * Reacts to a declaration created event by sending an event and by calling the URLs listed in the message of the
 * consumed event, separated by spaces. A failing call does not fail the handling of the event.
 */
@Slf4j
@RequiredArgsConstructor
class HttpTestConsumer {

    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    private final RestClient restClient;
    private final List<String> handledEventIds = new CopyOnWriteArrayList<>();

    @KafkaListener(topics = JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC)
    void onDeclarationCreatedEvent(JmeDeclarationCreatedEvent event) {
        kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, TestMessages.createJmeSimpleTestEvent("test"));
        for (String url : event.getPayload().getMessage().split(" ")) {
            call(url);
        }
        handledEventIds.add(event.getIdentity().getId());
    }

    private void call(String url) {
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.info("Call to {} failed: {}", url, e.getMessage());
        }
    }

    void awaitHandled(JmeDeclarationCreatedEvent event) {
        await().until(() -> handledEventIds.contains(event.getIdentity().getId()));
    }
}
