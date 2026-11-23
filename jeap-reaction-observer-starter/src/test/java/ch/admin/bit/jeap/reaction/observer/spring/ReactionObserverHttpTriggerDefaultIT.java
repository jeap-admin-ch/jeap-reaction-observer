package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default: reaction model version 2 is off. An incoming HTTP request is no trigger, and the event sent while
 * it is handled is a reaction without a trigger, as it has always been - a Reaction Observer Service that does
 * not support model version 2 yet can process everything it gets.
 */
@SpringBootTest
// The message contracts of the test messages are declared once for this module, on ReactionObserverIT
@Import({ReactionObserverHttpTriggerIT.DeclarationController.class, ReactionEventsTestConsumer.class})
class ReactionObserverHttpTriggerDefaultIT extends ReactionKafkaTestBase {

    private static final String MESSAGE_ACTION_ID = "event:JmeSimpleTestEvent:" +
            DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
    private static final String ACTION_ONLY_REACTION_ID = "#" + DigestUtils.md5Hex(MESSAGE_ACTION_ID);

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void modelVersion2Off_incomingRequestIsNoTrigger_theEventIsAReactionWithoutTrigger() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(post("/api/declarations/42/submission")).andExpect(status().isOk());

        ReactionIdentifiedEvent identified =
                reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(ACTION_ONLY_REACTION_ID);
        assertThat(identified.getPayload().getReaction()).isInstanceOf(ActionOnly.class);
        assertThat(identified.getPayload().getReactionModelVersion()).isEqualTo(1);
        assertThat(reactionEventsTestConsumer.getReactionIdentifiedEvents().values())
                .describedAs("No event contains an observation of the type http_request")
                .allSatisfy(event -> {
                    assertThat(ReactionIdentifiedEvents.trigger(event)).isEmpty();
                    assertThat(ReactionIdentifiedEvents.httpRequestActionFqns(event)).isEmpty();
                });
    }

    @SpringBootApplication
    static class TestApp {
    }
}
