package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.spring.HttpObservationTestConfiguration.StubbedRestClient;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.reaction.observer.test.TestMessages;
import ch.admin.bit.jme.declaration.JmeDeclarationCreatedEvent;
import ch.admin.bit.jme.test.JmeSimpleTestEvent;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.REACTION_MODEL_VERSION_HEADER;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.fqn;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.withObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The default: reaction model version 2 is off. Towards the Reaction Observer Service the library behaves as it
 * did before HTTP requests could be observed - a service that does not support model version 2 yet can
 * process everything it gets.
 */
@SpringBootTest
// The message contracts of the test messages are declared once for this module, on ReactionObserverIT
@Import(HttpObservationTestConfiguration.class)
class ReactionObserverHttpDefaultIT extends ReactionKafkaTestBase {

    private static final String PARTNER_URL = "https://partner.example.org/partner-service/api/partners/42";

    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;
    @Autowired
    private HttpTestConsumer httpTestConsumer;
    @Autowired
    private StubbedRestClient stubbedRestClient;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void modelVersion2Off_noRequestSignal_noHttpRequestActions_modelVersion1() {
        // The provider would name the operation, but it is not asked to
        stubbedRestClient.server().expect(times(2), requestTo(PARTNER_URL))
                .andExpect(headerDoesNotExist(REACTION_MODEL_VERSION_HEADER))
                .andRespond(withObservation(withSuccess(), fqn("partner-service", "GET", "/api/partners/{}")));
        String triggerId = "event:JmeDeclarationCreatedEvent:" + DigestUtils.md5Hex("topic=" + JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
        String messageActionId = "event:JmeSimpleTestEvent:" + DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);
        // The id of the reaction as it has always been: the HTTP request is not part of it
        String reactionId = triggerId + "#" + DigestUtils.md5Hex(messageActionId);

        // A call outside of a trigger: it must not become an action-only reaction (checked at the end)
        stubbedRestClient.restClient().get().uri(PARTNER_URL).retrieve().toBodilessEntity();
        // A call inside of a trigger
        JmeDeclarationCreatedEvent trigger = TestMessages.createJmeDeclarationCreatedEvent("test", PARTNER_URL);
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, trigger);
        httpTestConsumer.awaitHandled(trigger);

        ReactionIdentifiedEvent identified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        stubbedRestClient.server().verify();
        Reaction reaction = (Reaction) identified.getPayload().getReaction();
        assertThat(reaction.getActions()).extracting("type", "id")
                .containsExactly(tuple("event", messageActionId));
        assertThat(identified.getPayload().getReactionModelVersion()).isEqualTo(1);
        assertThat(identified.getIdentity().getIdempotenceId())
                .startsWith("ri_test-service-name_" + reactionId + "_v1_")
                .matches(".*_v1_\\d{8}T\\d{6}Z");

        ReactionsObservedEvent observed = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);
        assertThat(observed.getPayload().getReactionModelVersion()).isEqualTo(1);
        assertThat(observed.getPayload().getObservations()).extracting(Observation::getReactionId).containsExactly(reactionId);
        assertThat(observed.getPayload().getObservations()).extracting(Observation::getCount).containsExactly(1);

        assertThat(reactionEventsTestConsumer.getReactionIdentifiedEvents().values())
                .describedAs("No event contains an observation of the type http_request, and there is no action-only reaction")
                .allSatisfy(event -> {
                    assertThat(ReactionIdentifiedEvents.httpRequestActionFqns(event)).isEmpty();
                    assertThat(ReactionIdentifiedEvents.trigger(event)).isPresent()
                            .get().extracting("type").isEqualTo("event");
                });
    }

    @SpringBootApplication
    static class TestApp {
    }
}
