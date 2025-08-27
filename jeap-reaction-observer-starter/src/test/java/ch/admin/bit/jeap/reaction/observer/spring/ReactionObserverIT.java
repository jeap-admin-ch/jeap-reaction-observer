package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContracts;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContracts;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.TriggerOnly;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeCreateDeclarationCommand;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        String triggerPropsHash = DigestUtils.md5Hex("topic=" + JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
        String action1PropsHash = DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
        String action2PropsHash = DigestUtils.md5Hex("topic=" + JmeCreateDeclarationCommand.TypeRef.DEFAULT_TOPIC);
        String triggerId = "event:JmeDeclarationCreatedEvent:" + triggerPropsHash;
        String actionId1 = "event:JmeSimpleTestEvent:" + action1PropsHash;
        String actionId2 = "command:JmeCreateDeclarationCommand:" + action2PropsHash;
        String reactionId = triggerId + "#" + actionIdsHash(actionId1, actionId2);
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "reaction");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event
        ReactionIdentifiedEvent eventReactionIdentified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        Reaction reaction = (Reaction) eventReactionIdentified.getPayload().getReaction();
        assertThat(reaction)
                .matches(t -> t.getTrigger().getId().equals(triggerId))
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getActions().getFirst().getType().equals("event"))
                .matches(t -> t.getActions().getFirst().getFqn().equals("JmeSimpleTestEvent"))
                .matches(t -> t.getActions().getFirst().getId().equals(actionId1))
                .matches(t -> t.getActions().get(1).getType().equals("command"))
                .matches(t -> t.getActions().get(1).getFqn().equals("JmeCreateDeclarationCommand"))
                .matches(t -> t.getActions().get(1).getId().equals(actionId2));

        // Then: The reaction observer should send the reactions observed events
        ReactionsObservedEvent eventReactionsObserved = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);
        assertThat(eventReactionsObserved.getPayload().getTimeframe().getEnd())
                .isAfter(eventReactionsObserved.getPayload().getTimeframe().getStart());
        Observation eventObservation = eventReactionsObserved.getPayload().getObservations().stream()
                .filter(o -> o.getReactionId().equals(reactionId))
                .findFirst().orElseThrow();
        assertThat(eventObservation)
                .matches(o -> o.getCount() == 1);

        ReactionsObservedEvent commandReactionsObserved = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);
        assertThat(commandReactionsObserved.getPayload().getTimeframe().getEnd())
                .isAfter(commandReactionsObserved.getPayload().getTimeframe().getStart());
        Observation commandObservation = commandReactionsObserved.getPayload().getObservations().stream()
                .filter(o -> o.getReactionId().equals(reactionId))
                .findFirst().orElseThrow();
        assertThat(commandObservation)
                .matches(o -> o.getCount() == 1);
    }

    private String actionIdsHash(String actionId1, String actionId2) {
        String actionIds = Stream.of(actionId1, actionId2)
                .sorted()
                .collect(Collectors.joining("&"));
        return DigestUtils.md5Hex(actionIds);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_triggerOnly_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be consumed as a trigger
        String propsHash = DigestUtils.md5Hex("topic=" + JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
        String triggerId = "event:JmeDeclarationCreatedEvent:" + propsHash;
        //noinspection UnnecessaryLocalVariable The reaction Id has not action part, and is therefore the same as the trigger Id
        String reactionId = triggerId;
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "triggerOnly");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event and the reaction observed event
        ReactionIdentifiedEvent reactionIdentifiedEvent = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        ReactionsObservedEvent reactionsObservedEvent = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);

        TriggerOnly reaction = (TriggerOnly) reactionIdentifiedEvent.getPayload().getReaction();
        assertThat(reaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getId().equals(triggerId))
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getTrigger().getProps().containsKey("topic"));

        Observation observation = reactionsObservedEvent.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().equals(reactionId))
                .matches(o -> o.getCount() == 1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionsObservedTwoVariants_triggerOnly_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be consumed as a trigger
        String propsHash = DigestUtils.md5Hex("topic=" + JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
        String triggerId = "event:JmeDeclarationCreatedEvent:" + propsHash;
        String triggerIdWithVariant = "event:JmeDeclarationCreatedEvent/variant1:" + propsHash;
        //noinspection UnnecessaryLocalVariable The reaction Id has not action part, and is therefore the same as the trigger Id
        String reactionId = triggerId;
        //noinspection UnnecessaryLocalVariable The reaction Id has not action part, and is therefore the same as the trigger Id
        String reactionIdWithVariant = triggerIdWithVariant;
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", "triggerOnly");
        JmeDeclarationCreatedEvent eventWithVariant = TestMessages.createJmeDeclarationCreatedEventWithVariant("test", "triggerOnly", "variant1");

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event and the reaction observed event
        ReactionIdentifiedEvent reactionIdentifiedEvent = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        ReactionsObservedEvent reactionsObservedEvent = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);

        TriggerOnly reaction = (TriggerOnly) reactionIdentifiedEvent.getPayload().getReaction();
        assertThat(reaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getId().equals(triggerId))
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent"))
                .matches(t -> t.getTrigger().getProps().containsKey("topic"));

        Observation observation = reactionsObservedEvent.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().equals(reactionId))
                .matches(o -> o.getCount() == 1);

        // When: The event is sent as byte array to avoid triggering the reaction observer for the action of sending the test event
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, eventWithVariant);

        ReactionIdentifiedEvent reactionIdentifiedEventWithVariant = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionIdWithVariant);
        ReactionsObservedEvent reactionsObservedEventWithVariant = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionIdWithVariant);

        reaction = (TriggerOnly) reactionIdentifiedEventWithVariant.getPayload().getReaction();
        assertThat(reaction)
                .isNotNull()
                .matches(t -> t.getTrigger().getId().equals(triggerIdWithVariant))
                .matches(t -> t.getTrigger().getType().equals("event"))
                .matches(t -> t.getTrigger().getFqn().equals("JmeDeclarationCreatedEvent/variant1"))
                .matches(t -> t.getTrigger().getProps().containsKey("topic"));

        observation = reactionsObservedEventWithVariant.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().equals(reactionIdWithVariant))
                .matches(o -> o.getCount() == 1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void reactionObserved_actionOnly_expectReactionIdentifiedAndObservedEvents() {
        // Given: A test event that will be sent as an action
        String propsHash = DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
        String actionId = "event:JmeSimpleTestEvent:" + propsHash;
        String reactionId = "#" + DigestUtils.md5Hex(actionId);
        JmeSimpleTestEvent event = TestMessages.createJmeSimpleTestEvent("test");

        // When: Action outside any trigger happens
        kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, event);

        // Then: The reaction observer should send the reaction identified event and the reaction observed event
        ReactionIdentifiedEvent reactionIdentifiedEvent = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        ReactionsObservedEvent reactionsObservedEvent = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);

        ActionOnly actionOnly = (ActionOnly) reactionIdentifiedEvent.getPayload().getReaction();
        assertThat(actionOnly)
                .isNotNull()
                .matches(a -> a.getAction().getId().equals(actionId))
                .matches(a -> a.getAction().getType().equals("event"))
                .matches(a -> a.getAction().getFqn().equals("JmeSimpleTestEvent"))
                .matches(a -> a.getAction().getProps().containsKey("topic"));

        Observation observation = reactionsObservedEvent.getPayload().getObservations().getFirst();
        assertThat(observation)
                .isNotNull()
                .matches(o -> o.getReactionId().equals(reactionId))
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
