package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.messaging.kafka.interceptor.JeapKafkaMessageCallback;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.events.scheduler.ReactionsObservedEventScheduler;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jeap.reaction.observer.enabled=false")
class ReactionObserverDisabledIT extends ReactionKafkaTestBase {

    @Autowired
    private TestConsumer testConsumer;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;
    @Autowired
    private ObjectProvider<ReactionObserverService> reactionObserverServiceObjectProvider;
    @Autowired
    private ObjectProvider<JeapKafkaMessageCallback> jeapKafkaMessageCallbackObjectProvider;
    @Autowired
    private ObjectProvider<ReactionsObservedEventScheduler> reactionsObservedEventSchedulerObjectProvider;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_reaction_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be consumed as a trigger, and then produces another event and a command inside
        // the trigger code path (see TestConsumer).
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "reaction");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: No beans should be created for the reaction observer, and no events should be produced.
        testConsumer.awaitDeclarationCreatedEvent(event);
        assertThat(reactionEventsTestConsumer.getReactionIdentifiedEvents())
                .isEmpty();
        assertThat(reactionObserverServiceObjectProvider)
                .isEmpty();
        assertThat(jeapKafkaMessageCallbackObjectProvider)
                .isEmpty();
        assertThat(reactionsObservedEventSchedulerObjectProvider)
                .isEmpty();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public TestConsumer testConsumer(KafkaTemplate<AvroMessageKey, AvroMessage> template) {
            return new TestConsumer(template);
        }

        @Bean
        public ReactionEventsTestConsumer reactionEventsTestConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
