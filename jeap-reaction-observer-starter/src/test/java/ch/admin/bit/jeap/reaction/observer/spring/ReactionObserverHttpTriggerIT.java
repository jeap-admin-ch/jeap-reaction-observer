package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reaction model version 2: an incoming HTTP request is the trigger of the reaction. The
 * {@code ReactionIdentifiedEvent} describes the reaction with this trigger, and the {@code ReactionsObservedEvent}
 * counts it under the reaction ID, which is built from the trigger's ID. The cases of the rule that decides
 * whether a request counts are tested in the HTTP module, with a servlet container.
 */
@SpringBootTest(properties = "jeap.reaction.observer.model-version=2")
// The message contracts of the test messages are declared once for this module, on ReactionObserverIT
@Import({ReactionObserverHttpTriggerIT.DeclarationController.class, ReactionEventsTestConsumer.class})
class ReactionObserverHttpTriggerIT extends ReactionKafkaTestBase {

    // The provider is the name of the application, see src/test/resources/application.yml
    private static final String OPERATION = "test POST /api/declarations/{}/submission";
    private static final String TRIGGER_ID = "http_request:" + OPERATION;
    private static final String MESSAGE_ACTION_ID = "event:JmeSimpleTestEvent:" +
            DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
    private static final String REACTION_ID = TRIGGER_ID + "#" + DigestUtils.md5Hex(MESSAGE_ACTION_ID);

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void incomingRequestThatSendsAnEvent_isIdentifiedAndCountedAsReactionOfTheRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(post("/api/declarations/42/submission")).andExpect(status().isOk());

        ReactionIdentifiedEvent identified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(REACTION_ID);
        Reaction reaction = (Reaction) identified.getPayload().getReaction();
        assertThat(reaction.getTrigger()).extracting("type", "fqn", "id")
                .containsExactly("http_request", OPERATION, TRIGGER_ID);
        assertThat(reaction.getTrigger().getProps()).isEmpty();
        assertThat(reaction.getActions()).extracting("type", "fqn", "id")
                .containsExactly(tuple("event", "JmeSimpleTestEvent", MESSAGE_ACTION_ID));
        assertThat(identified.getPayload().getReactionModelVersion()).isEqualTo(2);

        ReactionsObservedEvent observed = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(REACTION_ID);
        assertThat(observed.getPayload().getReactionModelVersion()).isEqualTo(2);
        assertThat(observed.getPayload().getObservations()).extracting(Observation::getReactionId).containsExactly(REACTION_ID);
        assertThat(observed.getPayload().getObservations()).extracting(Observation::getCount).containsExactly(1);
    }

    @SpringBootApplication
    static class TestApp {
    }

    /**
     * A provider with an operation that sends an event while it handles a request. Nested classes of a test class
     * are not found by the component scan, so the controller is imported.
     */
    @RestController
    static class DeclarationController {

        private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

        DeclarationController(KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate) {
            this.kafkaTemplate = kafkaTemplate;
        }

        @PostMapping("/api/declarations/{id}/submission")
        void submit(@PathVariable String id) {
            kafkaTemplate.send(JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC, TestMessages.createJmeSimpleTestEvent("test"));
        }
    }
}
