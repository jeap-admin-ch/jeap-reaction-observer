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

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_reaction() {
        ArgumentCaptor<Reaction> reactionCaptor = ArgumentCaptor.forClass(Reaction.class);

        // Given: A test event that will be consumed as a trigger
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "reaction");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        await().untilAsserted(() -> verify(reactionObserverService, times(1))
                .reactionObserved(reactionCaptor.capture()));

        // Then: Verify that reactions for the event/command produced by the TestConsumer were recorded
        List<Reaction> reactions = reactionCaptor.getAllValues();

        Reaction eventReaction = reactions.stream().filter(r -> r.actions().getFirst().type() == ObservationType.EVENT)
                .findFirst().orElseThrow();
        assertThat(eventReaction.trigger())
                .isNotNull();
        assertThat(eventReaction.id())
                .matches("event:JmeDeclarationCreatedEvent:b80235cef2e8bc759eee2e284e61fbeb#736a8de447616d5950fd41f7db493282");
        assertThat(eventReaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(eventReaction.actions().getFirst())
                .isNotNull();
        assertThat(eventReaction.actions().getFirst().fqn())
                .isEqualTo("JmeSimpleTestEvent");

        Reaction commandReaction = reactions.stream().filter(r -> r.actions().get(1).type() == ObservationType.COMMAND)
                .findFirst().orElseThrow();
        assertThat(commandReaction.trigger())
                .isNotNull();
        assertThat(commandReaction.id())
                .matches("event:JmeDeclarationCreatedEvent:b80235cef2e8bc759eee2e284e61fbeb#736a8de447616d5950fd41f7db493282");
        assertThat(commandReaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(commandReaction.actions().get(1))
                .isNotNull();
        assertThat(commandReaction.actions().get(1).fqn())
                .isEqualTo("JmeCreateDeclarationCommand");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_triggerOnly() {
        ArgumentCaptor<Reaction> reactionCaptor = ArgumentCaptor.forClass(Reaction.class);

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
                .matches("event:JmeDeclarationCreatedEvent:.*");
        assertThat(reaction.trigger().fqn())
                .isEqualTo("JmeDeclarationCreatedEvent");
        assertThat(reaction.trigger().type())
                .isEqualTo(ObservationType.EVENT);
        assertThat(reaction.trigger().props())
                .containsEntry("topic", JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
        assertThat(reaction.actions())
                .isEmpty();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_actionOnly_event() {
        ArgumentCaptor<Reaction> reactionCaptor = ArgumentCaptor.forClass(Reaction.class);

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
        assertThat(reaction.actions())
                .isNotEmpty();
        assertThat(reaction.id())
                .isEqualTo("#db6a2b34d03d2408b8469c01f949d65e");
        assertThat(reaction.actions().getFirst().fqn())
                .isEqualTo("JmeSimpleTestEvent");
        assertThat(reaction.actions().getFirst().type())
                .isEqualTo(ObservationType.EVENT);
        assertThat(reaction.actions().getFirst().props())
                .containsEntry("topic", JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void onSend_actionOnly_command() {
        ArgumentCaptor<Reaction> reactionCaptor = ArgumentCaptor.forClass(Reaction.class);

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
        assertThat(reaction.actions())
                .isNotEmpty();
        assertThat(reaction.id())
                .isEqualTo("#cad415806cdb173d927dc552ca81e298");
        assertThat(reaction.actions().getFirst().fqn())
                .isEqualTo("JmeCreateDeclarationCommand");
        assertThat(reaction.actions().getFirst().type())
                .isEqualTo(ObservationType.COMMAND);
        assertThat(reaction.actions().getFirst().props())
                .containsEntry("topic", JmeCreateDeclarationCommand.TypeRef.DEFAULT_TOPIC);
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
