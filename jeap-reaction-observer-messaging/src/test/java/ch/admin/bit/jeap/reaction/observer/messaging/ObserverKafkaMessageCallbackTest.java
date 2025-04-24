package ch.admin.bit.jeap.reaction.observer.messaging;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContracts;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContracts;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@JeapMessageProducerContracts({JmeSimpleTestEvent.TypeRef.class, JmeCreateDeclarationCommand.TypeRef.class})
@JeapMessageConsumerContracts({JmeSimpleTestEvent.TypeRef.class, JmeCreateDeclarationCommand.TypeRef.class})
class ObserverKafkaMessageCallbackTest extends ReactionKafkaTestBase {

    @MockitoBean
    private ReactionObserverService reactionObserverService;
    @Captor
    private ArgumentCaptor<Reaction> reactionCaptor;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_reaction() {
        // Given: A test event that will be consumed as a trigger
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "reaction");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        await().untilAsserted(() -> verify(reactionObserverService, times(2))
                .reactionObserved(reactionCaptor.capture()));

        // Then: Verify that reactions for the event/command produced by the TestConsumer were recorded
        List<Reaction> reactions = reactionCaptor.getAllValues();

        Reaction eventReaction = reactions.stream().filter(r -> r.action().type() == ObservationType.EVENT)
                .findFirst().orElseThrow();
        assertThat(eventReaction.trigger())
                .isNotNull();
        assertThat(eventReaction.id())
                .isEqualTo("event:JmeDeclarationCreatedEvent#event:JmeSimpleTestEvent");
        assertThat(eventReaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(eventReaction.action())
                .isNotNull();
        assertThat(eventReaction.action().fqn())
                .isEqualTo("JmeSimpleTestEvent");

        Reaction commandReaction = reactions.stream().filter(r -> r.action().type() == ObservationType.COMMAND)
                .findFirst().orElseThrow();
        assertThat(commandReaction.trigger())
                .isNotNull();
        assertThat(commandReaction.id())
                .isEqualTo("event:JmeDeclarationCreatedEvent#command:JmeCreateDeclarationCommand");
        assertThat(commandReaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(commandReaction.action())
                .isNotNull();
        assertThat(commandReaction.action().fqn())
                .isEqualTo("JmeCreateDeclarationCommand");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_triggerOnly() {
        // Given: A test event that will be consumed as a trigger
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "test");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        await().untilAsserted(() -> verify(reactionObserverService)
                .reactionObserved(reactionCaptor.capture()));

        // Then: Verify that a trigger-only reaction was recorded
        Reaction reaction = reactionCaptor.getValue();
        assertThat(reaction.trigger())
                .isNotNull();
        assertThat(reaction.id())
                .isEqualTo("event:JmeDeclarationCreatedEvent");
        assertThat(reaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(reaction.trigger().type())
                .isEqualTo(ObservationType.EVENT);
        assertThat(reaction.trigger().props())
                .isEmpty();
        assertThat(reaction.action())
                .isNull();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_actionOnly_event() {
        // Given: A test event that will be sent as an action
        JmeSimpleTestEvent event = TestMessages.createJmeSimpleTestEvent("test");

        // When: Action outside any trigger happens
        kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: Verify that a trigger-only reaction was recorded
        verify(reactionObserverService)
                .reactionObserved(reactionCaptor.capture());
        Reaction reaction = reactionCaptor.getValue();

        assertThat(reaction.trigger())
                .isNull();
        assertThat(reaction.action())
                .isNotNull();
        assertThat(reaction.id())
                .isEqualTo("#event:JmeSimpleTestEvent");
        assertThat(reaction.action().fqn())
                .isEqualTo("JmeSimpleTestEvent");
        assertThat(reaction.action().type())
                .isEqualTo(ObservationType.EVENT);
        assertThat(reaction.action().props())
                .isEmpty();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_actionOnly_command() {
        // Given: A test event that will be sent as an action
        JmeCreateDeclarationCommand command = TestMessages.createJmeCreateDeclarationCommand("test");

        // When: Action outside any trigger happens
        kafkaTemplate.send(JmeCreateDeclarationCommand.TypeRef.DEFAULT_TOPIC, command);

        // Then: Verify that a trigger-only reaction was recorded
        verify(reactionObserverService)
                .reactionObserved(reactionCaptor.capture());
        Reaction reaction = reactionCaptor.getValue();

        assertThat(reaction.trigger())
                .isNull();
        assertThat(reaction.action())
                .isNotNull();
        assertThat(reaction.id())
                .isEqualTo("#command:JmeCreateDeclarationCommand");
        assertThat(reaction.action().fqn())
                .isEqualTo("JmeCreateDeclarationCommand");
        assertThat(reaction.action().type())
                .isEqualTo(ObservationType.COMMAND);
        assertThat(reaction.action().props())
                .isEmpty();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ReactionRecorder reactionRecorder(ReactionObserverService reactionObserverService) {
            return new ReactionRecorder(reactionObserverService);
        }

        @Bean
        TestConsumer testConsumer() {
            return new TestConsumer();
        }

    }

    @SpringBootApplication
    static class TestApp {
    }
}
