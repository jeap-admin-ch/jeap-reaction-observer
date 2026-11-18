package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.REACTION_MODEL_VERSION_HEADER;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.coarseFqn;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.fqn;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.observationId;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.withObservation;
import static ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents.hasHttpRequestAction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Reaction model version 2: the outgoing HTTP requests of a service are part of its reactions.
 */
@SpringBootTest(properties = "jeap.reaction.observer.model-version=2")
// The message contracts of the test messages are declared once for this module, on ReactionObserverIT
@Import(HttpObservationTestConfiguration.class)
class ReactionObserverHttpIT extends ReactionKafkaTestBase {

    private static final String PARTNER_URL = "https://partner.example.org/partner-service/api/partners/42";
    private static final String PARTNER_OPERATION = fqn("partner-service", "GET", "/api/partners/{}");
    private static final String JOB_URL = "https://partner.example.org/partner-service/api/jobs/7:cancel";
    // A name with the characters that need encoding in the header and in the observation id
    private static final String JOB_OPERATION = fqn("partner-service", "GET", "/api/jobs/{}:cancel/100%/\"café\"");
    private static final String EXTERNAL_URL = "https://external.example.org/rating-service/api/ratings/42";
    private static final String EXTERNAL_COARSE_ACTION = coarseFqn("//external.example.org/rating-service", "GET");
    private static final String UNAVAILABLE_URL = "https://unavailable.example.org/api/x";

    private static final String TRIGGER_ID = "event:JmeDeclarationCreatedEvent:" +
            DigestUtils.md5Hex("topic=" + JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC);
    private static final String MESSAGE_ACTION_ID = "event:JmeSimpleTestEvent:" +
            DigestUtils.md5Hex("topic=" + JmeSimpleTestEvent.TypeRef.DEFAULT_TOPIC);

    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;
    @Autowired
    private HttpTestConsumer httpTestConsumer;
    @Autowired
    private StubbedRestClient stubbedRestClient;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void providerNamesTheOperation_fineActionsAndModelVersion2InBothEvents() {
        stubbedRestClient.server().expect(manyTimes(), requestTo(PARTNER_URL))
                .andExpect(header(REACTION_MODEL_VERSION_HEADER, "2"))
                .andRespond(withObservation(withSuccess(), PARTNER_OPERATION));
        stubbedRestClient.server().expect(manyTimes(), requestTo(JOB_URL))
                .andExpect(header(REACTION_MODEL_VERSION_HEADER, "2"))
                .andRespond(withObservation(withStatus(HttpStatus.CONFLICT), JOB_OPERATION));
        String reactionId = TRIGGER_ID + "#" + actionIdsHash(MESSAGE_ACTION_ID, observationId(PARTNER_OPERATION), observationId(JOB_OPERATION));

        sendTrigger(PARTNER_URL + " " + JOB_URL);

        ReactionIdentifiedEvent identified = reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        Reaction reaction = (Reaction) identified.getPayload().getReaction();
        assertThat(reaction.getTrigger().getId()).isEqualTo(TRIGGER_ID);
        assertThat(reaction.getActions()).extracting("type", "fqn", "id").containsExactly(
                tuple("event", "JmeSimpleTestEvent", MESSAGE_ACTION_ID),
                tuple("http_request", PARTNER_OPERATION, "http_request:partner-service GET /api/partners/{}"),
                tuple("http_request", JOB_OPERATION, "http_request:partner-service GET /api/jobs/{}%3Acancel/100%25/\"café\""));
        assertThat(reaction.getActions().get(1).getProps()).isEmpty();
        assertThat(identified.getPayload().getReactionModelVersion()).isEqualTo(2);
        assertThat(identified.getIdentity().getIdempotenceId())
                .startsWith("ri_test-service-name_" + reactionId + "_v2_")
                .matches(".*_v2_\\d{8}T\\d{6}Z");

        ReactionsObservedEvent observed = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);
        assertThat(observed.getPayload().getReactionModelVersion()).isEqualTo(2);
        Observation observation = observed.getPayload().getObservations().stream()
                .filter(o -> o.getReactionId().equals(reactionId))
                .findFirst().orElseThrow();
        assertThat(observation.getCount()).isEqualTo(1);
        assertThat(observation.getUnidentifiedActions()).isZero();
        assertThat(observation.getMinUnidentifiedActions()).isZero();
        assertThat(observation.getObservationsWithUnidentifiedActions()).isZero();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void targetAnswersWithoutNamingTheOperation_coarseAction() {
        stubbedRestClient.server().expect(manyTimes(), requestTo(EXTERNAL_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        String reactionId = TRIGGER_ID + "#" + actionIdsHash(MESSAGE_ACTION_ID, observationId(EXTERNAL_COARSE_ACTION));

        sendTrigger(EXTERNAL_URL);

        ReactionIdentifiedEvent identified = reactionEventsTestConsumer.awaitReactionIdentifiedEvent(hasHttpRequestAction(EXTERNAL_COARSE_ACTION));
        assertThat(ReactionIdentifiedEvents.reactionId(identified)).isEqualTo(reactionId);
        assertThat(ReactionIdentifiedEvents.actions(identified)).extracting("type", "fqn", "id").containsExactly(
                tuple("event", "JmeSimpleTestEvent", MESSAGE_ACTION_ID),
                tuple("http_request", "//external.example.org/rating-service GET /{*}",
                        "http_request://external.example.org/rating-service GET /{*}"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void failedCall_theReactionOfThatExecutionIsNeitherIdentifiedNorCounted() {
        stubbedRestClient.server().expect(manyTimes(), requestTo(UNAVAILABLE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        stubbedRestClient.server().expect(manyTimes(), requestTo(PARTNER_URL)).andRespond(withObservation(withSuccess(), PARTNER_OPERATION));
        stubbedRestClient.server().expect(manyTimes(), requestTo(JOB_URL)).andRespond(withObservation(withSuccess(), JOB_OPERATION));
        String reactionId = TRIGGER_ID + "#" + actionIdsHash(MESSAGE_ACTION_ID, observationId(PARTNER_OPERATION));

        // The first execution fails to reach a target, also the successful call after the failure does not count.
        // That call is to another operation than the one of the second execution: were the first execution
        // reported, it would be a reaction of its own, whether with or without the failed call.
        JmeDeclarationCreatedEvent failingTrigger = sendTrigger(UNAVAILABLE_URL + " " + JOB_URL);
        httpTestConsumer.awaitHandled(failingTrigger);
        // The second execution of the same trigger runs without a failure
        sendTrigger(PARTNER_URL);

        reactionEventsTestConsumer.awaitReactionIdentifiedEventForReactionId(reactionId);
        ReactionsObservedEvent observed = reactionEventsTestConsumer.awaitReactionsObservedEventForReactionId(reactionId);
        assertThat(reactionEventsTestConsumer.getReactionIdentifiedEvents().values())
                .describedAs("Only the reaction of the second execution is identified: the first one is reported " +
                        "neither with the failed call nor without it")
                .extracting(ReactionIdentifiedEvents::reactionId)
                .containsExactly(reactionId);
        assertThat(reactionEventsTestConsumer.awaitReactionsObservedEvents())
                .flatExtracting(event -> event.getPayload().getObservations())
                .extracting(Observation::getReactionId)
                .containsOnly(reactionId);
        assertThat(observed.getPayload().getObservations()).extracting(Observation::getCount).containsExactly(1);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void callOutsideOfATrigger_actionOnlyReaction() {
        stubbedRestClient.server().expect(manyTimes(), requestTo(PARTNER_URL)).andRespond(withObservation(withSuccess(), PARTNER_OPERATION));

        stubbedRestClient.restClient().get().uri(PARTNER_URL).retrieve().toBodilessEntity();

        ReactionIdentifiedEvent identified = reactionEventsTestConsumer.awaitReactionIdentifiedEvent(hasHttpRequestAction(PARTNER_OPERATION));
        ActionOnly actionOnly = (ActionOnly) identified.getPayload().getReaction();
        assertThat(actionOnly.getAction().getType()).isEqualTo("http_request");
        assertThat(actionOnly.getAction().getFqn()).isEqualTo(PARTNER_OPERATION);
        assertThat(actionOnly.getReactionId()).isEqualTo("#" + actionIdsHash(observationId(PARTNER_OPERATION)));
    }

    private JmeDeclarationCreatedEvent sendTrigger(String urlsToCall) {
        JmeDeclarationCreatedEvent event = TestMessages.createJmeDeclarationCreatedEvent("test", urlsToCall);
        // Sent as byte array, so that sending the trigger is not observed as an action itself
        sendTestEventWithoutRecordingAction(JmeDeclarationCreatedEvent.TypeRef.DEFAULT_TOPIC, event);
        return event;
    }

    private static String actionIdsHash(String... actionIds) {
        return DigestUtils.md5Hex(Stream.of(actionIds).sorted().collect(Collectors.joining("&")));
    }

    @SpringBootApplication
    static class TestApp {
    }
}
