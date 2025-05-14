package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContracts;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContracts;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.event.identified.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.TriggerOnly;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@JeapMessageProducerContracts({JmeSimpleTestEvent.TypeRef.class, JmeCreateDeclarationCommand.TypeRef.class, JmeDeclarationCreatedEvent.TypeRef.class})
@JeapMessageConsumerContracts({JmeSimpleTestEvent.TypeRef.class, JmeCreateDeclarationCommand.TypeRef.class, JmeDeclarationCreatedEvent.TypeRef.class})
class ReactionObserverIT extends ReactionKafkaTestBase {

    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_reaction_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be consumed as a trigger, and then produces another event and a command inside
        // the trigger code path (see TestConsumer).
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "reaction");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified events
        ReactionIdentifiedEvent eventReactionIdentified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReaction("event:JmeDeclarationCreatedEvent:.*#event:JmeSimpleTestEvent:.*");
        assertThat(eventReactionIdentified.getPayload().getReactionId())
                .matches("event:JmeDeclarationCreatedEvent:.*#event:JmeSimpleTestEvent:.*");
        Reaction eventReaction = (Reaction) eventReactionIdentified.getPayload().getReaction();
        assertThat(eventReaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getAction().getType().equals("event"))
                .matches(t -> t.getAction().getFqn().equals("JmeSimpleTestEvent"));

        ReactionIdentifiedEvent commandReactionIdentified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReaction("event:JmeDeclarationCreatedEvent:.*#command:JmeCreateDeclarationCommand:.*");
        Reaction commandReaction = (Reaction) commandReactionIdentified.getPayload().getReaction();
        assertThat(commandReactionIdentified.getPayload().getReactionId())
                .matches("event:JmeDeclarationCreatedEvent:.*#command:JmeCreateDeclarationCommand:.*");
        assertThat(commandReaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getAction().getType().equals("command"))
                .matches(t -> t.getAction().getFqn().equals("JmeCreateDeclarationCommand"));

        // Then: The reaction observer should send the reactions observed events
        ReactionsObservedEvent eventReactionsObserved = reactionEventsTestConsumer.awaitReactionsObservedEventForReaction("event:JmeDeclarationCreatedEvent:.*#event:JmeSimpleTestEvent:.*");
        assertThat(eventReactionsObserved.getPayload().getTimeframe().getEnd())
                .isAfter(eventReactionsObserved.getPayload().getTimeframe().getStart());
        Observation eventObservation = eventReactionsObserved.getPayload().getObservations().stream()
                .filter(o -> o.getReactionId().matches("event:JmeDeclarationCreatedEvent:.*#event:JmeSimpleTestEvent:.*"))
                .findFirst().orElseThrow();
        assertThat(eventObservation)
                .matches(o -> o.getCount() == 1);

        ReactionsObservedEvent commandReactionsObserved = reactionEventsTestConsumer.awaitReactionsObservedEventForReaction("event:JmeDeclarationCreatedEvent:.*#command:JmeCreateDeclarationCommand:.*");
        assertThat(commandReactionsObserved.getPayload().getTimeframe().getEnd())
                .isAfter(commandReactionsObserved.getPayload().getTimeframe().getStart());
        Observation commandObservation = commandReactionsObserved.getPayload().getObservations().stream()
                .filter(o -> o.getReactionId().matches("event:JmeDeclarationCreatedEvent:.*#command:JmeCreateDeclarationCommand:.*"))
                .findFirst().orElseThrow();
        assertThat(commandObservation)
                .matches(o -> o.getCount() == 1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_triggerOnly_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be consumed as a trigger
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "triggerOnly");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event and the reaction observed event
        ReactionIdentifiedEvent reactionIdentifiedEvent = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReaction("event:JmeDeclarationCreatedEvent:.*");
        ReactionsObservedEvent reactionsObservedEvent = reactionEventsTestConsumer.awaitReactionsObservedEventForReaction("event:JmeDeclarationCreatedEvent:.*");

        TriggerOnly reaction = (TriggerOnly) reactionIdentifiedEvent.getPayload().getReaction();
        assertThat(reactionIdentifiedEvent.getPayload().getReactionId())
                .matches("event:JmeDeclarationCreatedEvent:.*");
        assertThat(reaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getTrigger().getProps().containsKey("topic"));

        Observation observation = reactionsObservedEvent.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().matches("event:JmeDeclarationCreatedEvent:.*"))
                .matches(o -> o.getCount() == 1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_actionOnly_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be sent as an action
        JmeSimpleTestEvent event = TestMessages.createJmeSimpleTestEvent("test");

        // When: Action outside any trigger happens
        kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event and the reaction observed event
        ReactionIdentifiedEvent reactionIdentifiedEvent = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReaction("#event:JmeSimpleTestEvent:.*");
        ReactionsObservedEvent reactionsObservedEvent = reactionEventsTestConsumer.awaitReactionsObservedEventForReaction("#event:JmeSimpleTestEvent:.*");

        ActionOnly actionOnly = (ActionOnly) reactionIdentifiedEvent.getPayload().getReaction();
        assertThat(reactionIdentifiedEvent.getPayload().getReactionId())
                .matches("#event:JmeSimpleTestEvent:.*");
        assertThat(actionOnly)
                .isNotNull()
                .matches(a -> a.getAction().getType().equals("event"))
                .matches(a -> a.getAction().getFqn().equals("JmeSimpleTestEvent"))
                .matches(a -> a.getAction().getProps().containsKey("topic"));

        Observation observation = reactionsObservedEvent.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().matches("#event:JmeSimpleTestEvent:.*"))
                .matches(o -> o.getCount() == 1);
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
