package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpActionObserverTest {

    private static final String PARTNER_OPERATION = "partner-service GET /api/partners/{}";
    private static final String PARTNER_HEADER = "%\"" + PARTNER_OPERATION + "\"";
    private static final URI PARTNER_URI = URI.create("https://partner.example.org/partner-service/api/partners/42");
    private static final String PARTNER_COARSE_GET = "//partner.example.org/partner-service GET /{*}";
    private static final Observation MESSAGE_ACTION = Observation.ofEvent("ActionEvent", "action-topic");
    private static final int MAX_TARGET_COUNT = 5;
    private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

    private final ObservedReactions reactions = new ObservedReactions();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final HttpActionObserver observer = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
            new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), new MicrometerHttpObservationMetrics(meterRegistry), LOG_INTERVAL);

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 302, 400, 401, 404, 409, 500, 502, 503})
    void validHeader_fineAction_whateverTheStatus(int status) {
        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, status, PARTNER_HEADER));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION);
        assertThat(reactions.countsSinceLastCall()).containsValue(1);
        assertNoMetricCounted();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 302, 400, 401, 404, 409, 499})
    void noHeader_statusBelow500_coarseAction(int status) {
        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, status, null));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET);
        assertThat(reactions.countsSinceLastCall()).containsValue(1);
        assertThat(unidentifiedCalls("no_header")).isEqualTo(1);
        assertThat(unidentifiedCalls("invalid_header")).isZero();
        assertNoActionDropped();
    }

    @ParameterizedTest
    @ValueSource(strings = {"partner-service GET /api/partners/{}", "%\"unterminated", "%\"//partner.example.org GET /{*}\"", "",
            "%\"partner-service GET /api/partners/{}%0aforged log line\""})
    void invalidHeader_statusBelow500_coarseActionNamedByTheCalledTarget(String header) {
        reactions.inTrigger(() -> observer.onResponse("post", PARTNER_URI, 200, header));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//partner.example.org/partner-service POST /{*}");
        assertThat(unidentifiedCalls("invalid_header")).isEqualTo(1);
        assertThat(unidentifiedCalls("no_header")).isZero();
        assertNoActionDropped();
    }

    @Test
    void oversizedHeader_statusBelow500_coarseAction() {
        String oversized = "%\"svc GET /" + "x".repeat(ReactionObservationHeader.MAX_FQN_LENGTH) + "\"";

        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, 200, oversized));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET);
        assertThat(unidentifiedCalls("invalid_header")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504, 599})
    void noHeader_statusFrom500_executionIsDiscarded(int status) {
        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, status, null));

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(unidentifiedCalls("no_header")).isEqualTo(1);
        assertThat(droppedCalls("call_failed")).isEqualTo(1);
    }

    @Test
    void invalidHeader_statusFrom500_executionIsDiscarded() {
        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, 503, "<html>Service Unavailable</html>"));

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(unidentifiedCalls("invalid_header")).isEqualTo(1);
        assertThat(droppedCalls("call_failed")).isEqualTo(1);
    }

    @Test
    void noResponse_executionIsDiscarded() {
        reactions.inTrigger(() -> observer.onNoResponse("GET", PARTNER_URI));

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(unidentifiedCalls("no_response")).isEqualTo(1);
        assertThat(droppedCalls("call_failed")).isEqualTo(1);
    }

    @Test
    void failedCall_discardsTheWholeReaction_alsoMessageActionsAndLaterSuccessfulCalls() {
        reactions.inTrigger(() -> {
            reactions.recorder().onAction(MESSAGE_ACTION);
            observer.onNoResponse("GET", PARTNER_URI);
            reactions.recorder().onAction(Observation.ofCommand("LaterCommand", null, "command-topic"));
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        });

        assertThat(reactions.identified())
                .describedAs("Neither the reaction with the call nor the reaction without it is reported")
                .isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(droppedCalls("call_failed"))
                .describedAs("Counts the failed call, not the actions of the discarded execution")
                .isEqualTo(1);
    }

    @Test
    void failedCall_theNextExecutionOfTheSameTriggerCountsAsUsual() {
        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, 503, null));
        reactions.inTrigger(() -> {
            reactions.recorder().onAction(MESSAGE_ACTION);
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        });

        assertThat(reactions.identified())
                .containsExactly(new Reaction(ObservedReactions.TRIGGER, List.of(MESSAGE_ACTION, Observation.ofHttpRequest(PARTNER_OPERATION))));
        assertThat(reactions.countsSinceLastCall()).containsValue(1).hasSize(1);
    }

    @Test
    void retryAfterFailedCall_executionStaysDiscarded() {
        reactions.inTrigger(() -> {
            observer.onResponse("GET", PARTNER_URI, 503, null);
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        });
        reactions.inTrigger(() -> {
            observer.onNoResponse("GET", PARTNER_URI);
            observer.onResponse("GET", PARTNER_URI, 200, null);
        });

        assertThat(reactions.identified()).isEmpty();
        assertThat(droppedCalls("call_failed")).isEqualTo(2);
    }

    @Test
    void retryAfter401WithoutHeader_coarseAndFineAction() {
        reactions.inTrigger(() -> {
            observer.onResponse("GET", PARTNER_URI, 401, null);
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET, PARTNER_OPERATION);
    }

    @Test
    void repeatedCalls_sameAction() {
        reactions.inTrigger(() -> {
            observer.onResponse("GET", PARTNER_URI, 200, null);
            observer.onResponse("GET", URI.create("https://partner.example.org/partner-service/api/other/1"), 404, null);
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
            observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET, PARTNER_OPERATION);
        assertThat(unidentifiedCalls("no_header"))
                .describedAs("The diagnosis metric counts every call")
                .isEqualTo(2);
    }

    @Test
    void sameTargetOtherMethod_otherCoarseAction() {
        reactions.inTrigger(() -> {
            observer.onResponse("GET", PARTNER_URI, 200, null);
            observer.onResponse("POST", PARTNER_URI, 200, null);
        });

        assertThat(reactions.actionFqnsOfSingleReaction())
                .containsExactly(PARTNER_COARSE_GET, "//partner.example.org/partner-service POST /{*}");
    }

    @Test
    void twoServicesBehindTheSameHost_twoCoarseActions() {
        reactions.inTrigger(() -> {
            observer.onResponse("GET", URI.create("https://gateway.example.org/service-a/api/x"), 200, null);
            observer.onResponse("GET", URI.create("https://gateway.example.org/service-b/ui-api/y"), 200, null);
            observer.onResponse("GET", URI.create("https://gateway.example.org/service-b/actuator/health"), 200, null);
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(
                "//gateway.example.org/service-a GET /{*}",
                "//gateway.example.org/service-b GET /{*}",
                "//gateway.example.org GET /{*}");
    }

    @Test
    void portIsPartOfTheTargetIfConfigured() {
        HttpActionObserver observerWithPort = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(true),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), HttpObservationMetrics.NONE, LOG_INTERVAL);

        reactions.inTrigger(() -> observerWithPort.onResponse("GET", URI.create("http://localhost:8095/jme-ros-external/api/ratings/1"), 200, null));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//localhost:8095/jme-ros-external GET /{*}");
        assertThat(reactions.identified().getFirst().actions().getFirst().id().value())
                .isEqualTo("http_request://localhost%3A8095/jme-ros-external GET /{*}");
    }

    @Test
    void outsideTrigger_actionOnlyReaction() {
        observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        observer.onResponse("GET", PARTNER_URI, 404, null);

        assertThat(reactions.identified()).containsExactly(
                Reaction.actionOnly(Observation.ofHttpRequest(PARTNER_OPERATION)),
                Reaction.actionOnly(Observation.ofHttpRequest(PARTNER_COARSE_GET)));
        assertThat(unidentifiedCalls("no_header")).isEqualTo(1);
    }

    @Test
    void outsideTrigger_failedCall_noActionOnlyReaction() {
        observer.onNoResponse("GET", PARTNER_URI);
        observer.onResponse("GET", PARTNER_URI, 500, null);

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(unidentifiedCalls("no_response")).isEqualTo(1);
        assertThat(unidentifiedCalls("no_header")).isEqualTo(1);
        assertThat(droppedCalls("call_failed")).isEqualTo(2);

        // A later call on the same thread is observed as usual
        observer.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        assertThat(reactions.identified()).hasSize(1);
    }

    @Test
    void targetLimit_noCoarseActionForFurtherTargets() {
        for (int i = 0; i < MAX_TARGET_COUNT; i++) {
            observer.onResponse("GET", URI.create("https://host-" + i + ".example.org/api/x"), 200, null);
        }
        reactions.clear();

        reactions.inTrigger(() -> {
            observer.onResponse("GET", URI.create("https://one-too-many.example.org/api/x"), 200, null);
            observer.onResponse("POST", URI.create("https://host-0.example.org/api/x"), 200, null);
            observer.onResponse("GET", URI.create("https://one-too-many.example.org/api/x"), 200, PARTNER_HEADER);
        });

        assertThat(reactions.actionFqnsOfSingleReaction())
                .describedAs("Known targets and fine actions are not affected by the limit")
                .containsExactly("//host-0.example.org POST /{*}", PARTNER_OPERATION);
        assertThat(droppedCalls("target_limit")).isEqualTo(1);
        assertThat(unidentifiedCalls("no_header"))
                .describedAs("A call over the limit counts once in each metric")
                .isEqualTo(MAX_TARGET_COUNT + 2.0);
    }

    /**
     * A target that names a different operation with every response must not create a new action - and with it
     * a new reaction - per call.
     */
    @Test
    void newOperationLimitPerTarget_targetNamingANewOperationWithEveryResponse_furtherNamesBecomeTheCoarseAction() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(3, 100);

        for (int i = 0; i < 10; i++) {
            limitedObserver.onResponse("GET", PARTNER_URI, 200, operationHeader("partner-service GET /api/operation-" + i));
        }

        assertThat(reactions.identified()).containsExactly(
                Reaction.actionOnly(Observation.ofHttpRequest("partner-service GET /api/operation-0")),
                Reaction.actionOnly(Observation.ofHttpRequest("partner-service GET /api/operation-1")),
                Reaction.actionOnly(Observation.ofHttpRequest("partner-service GET /api/operation-2")),
                Reaction.actionOnly(Observation.ofHttpRequest(PARTNER_COARSE_GET)));
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(7);
        assertThat(unidentifiedCalls("no_header") + unidentifiedCalls("invalid_header")).isZero();
        assertNoActionDropped();
    }

    @Test
    void newOperationLimitPerTarget_acceptedOperationsAndOtherTargetsAreNotAffected() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(1, 100);
        URI otherUri = URI.create("https://other.example.org/other-service/api/things/1");
        limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        reactions.clear();

        reactions.inTrigger(() -> {
            limitedObserver.onResponse("POST", PARTNER_URI, 200, operationHeader("partner-service POST /api/partners"));
            limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
            limitedObserver.onResponse("GET", otherUri, 200, operationHeader("other-service GET /api/things/{}"));
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(
                "//partner.example.org/partner-service POST /{*}", PARTNER_OPERATION, "other-service GET /api/things/{}");
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
    }

    /**
     * The name a target gives its operation does not decide which target it is counted for: a target cannot get
     * around its limit by naming operations of other components.
     */
    @Test
    void newOperationLimitPerTarget_countsByTheCalledTarget_notByTheComponentTheNameClaims() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(1, 100);

        reactions.inTrigger(() -> {
            limitedObserver.onResponse("GET", PARTNER_URI, 200, operationHeader("component-a GET /api/x"));
            limitedObserver.onResponse("GET", PARTNER_URI, 200, operationHeader("component-b GET /api/x"));
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("component-a GET /api/x", PARTNER_COARSE_GET);
    }

    /**
     * An operation that is accepted is an action already: every target may name it, also one that may not
     * contribute a new name any more.
     */
    @Test
    void acceptedOperation_isAFineActionFromEveryTarget() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(1, 100);
        URI otherUri = URI.create("https://other.example.org/partner-service/api/partners/42");
        String otherOperation = "partner-service DELETE /api/partners/{}";

        reactions.inTrigger(() -> {
            limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
            limitedObserver.onResponse("GET", otherUri, 200, PARTNER_HEADER);
            limitedObserver.onResponse("DELETE", otherUri, 200, operationHeader(otherOperation));
            limitedObserver.onResponse("DELETE", PARTNER_URI, 200, operationHeader(otherOperation));
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION, otherOperation);
        assertNoMetricCounted();
    }

    @Test
    void operationLimitInTotal_manyTargetsNamingOneOperationEach_furtherNamesBecomeCoarseActions() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(100, 2);

        reactions.inTrigger(() -> {
            for (int i = 0; i < 3; i++) {
                limitedObserver.onResponse("POST", URI.create("https://subscriber-" + i + ".example.org/api/callback"), 200,
                        operationHeader("subscriber-" + i + " POST /api/callback"));
            }
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(
                "subscriber-0 POST /api/callback", "subscriber-1 POST /api/callback", "//subscriber-2.example.org POST /{*}");
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void operationOverTheLimit_statusFrom500_executionIsDiscarded(int status) {
        HttpActionObserver limitedObserver = observerAcceptingOperations(1, 100);
        limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
        reactions.clear();

        reactions.inTrigger(() -> {
            reactions.recorder().onAction(MESSAGE_ACTION);
            limitedObserver.onResponse("GET", PARTNER_URI, status, operationHeader("partner-service GET /api/other"));
        });

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
        assertThat(droppedCalls("call_failed")).isEqualTo(1);
    }

    @Test
    void operationOverTheLimit_targetOverTheLimitToo_noAction() {
        HttpActionObserver limitedObserver = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(0), new HttpOperationRegistry(1, 100), new MicrometerHttpObservationMetrics(meterRegistry),
                LOG_INTERVAL);

        reactions.inTrigger(() -> {
            limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER);
            limitedObserver.onResponse("GET", PARTNER_URI, 200, operationHeader("partner-service GET /api/other"));
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION);
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
        assertThat(droppedCalls("target_limit"))
                .describedAs("The coarse action taking the place of the operation is subject to the target limit")
                .isEqualTo(1);
    }

    @Test
    void operationLimit_zero_noFineActions() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(0, 100);

        reactions.inTrigger(() -> limitedObserver.onResponse("GET", PARTNER_URI, 200, PARTNER_HEADER));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET);
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
    }

    @Test
    void failedCall_doesNotTakeOneOfTheTargets() {
        for (int i = 0; i < MAX_TARGET_COUNT; i++) {
            observer.onResponse("GET", URI.create("https://failing-" + i + ".example.org/api/x"), 503, null);
            observer.onNoResponse("GET", URI.create("https://unreachable-" + i + ".example.org/api/x"));
        }

        reactions.inTrigger(() -> observer.onResponse("GET", PARTNER_URI, 200, null));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET);
        assertThat(droppedCalls("target_limit")).isZero();
        assertThat(droppedCalls("call_failed")).isEqualTo(2.0 * MAX_TARGET_COUNT);
    }

    @Test
    void manyHttpActions_doNotPushOutAMessageAction_andAreCountedAsDropped() {
        reactions.inTrigger(() -> {
            for (int i = 0; i < 130; i++) {
                observer.onResponse("GET", PARTNER_URI, 200, "%\"partner-service GET /api/operation-" + i + "\"");
            }
            observer.onResponse("GET", PARTNER_URI, 200, null);
            reactions.recorder().onAction(MESSAGE_ACTION);
        });

        List<Observation> actions = reactions.identified().getFirst().actions();
        assertThat(actions).hasSize(101).last().isEqualTo(MESSAGE_ACTION);
        assertThat(droppedCalls("http_action_limit"))
                .describedAs("30 fine actions and one coarse action over the budget")
                .isEqualTo(31);
        List<Observation> messageActions = actions.stream().filter(action -> action.type() != ObservationType.HTTP_REQUEST).toList();
        assertThat(new Reaction(ObservedReactions.TRIGGER, messageActions).id())
                .describedAs("Without the HTTP actions the reaction has the id it had before HTTP requests were observed")
                .isEqualTo("event:TriggerEvent:465b6239ad71ebb43d0d44f4056b25f6#872c533230a0c81e5fb150b9e3211732");
    }

    @Test
    void uriWithoutHost_noAction() {
        reactions.inTrigger(() -> observer.onResponse("GET", URI.create("/relative/api/x"), 200, null));

        assertThat(reactions.identified()).containsExactly(new Reaction(ObservedReactions.TRIGGER, List.of()));
    }

    @Test
    void uriWithoutHost_operationNamesAreLimitedToo() {
        HttpActionObserver limitedObserver = observerAcceptingOperations(1, 100);
        URI uriWithoutHost = URI.create("/relative/api/x");

        reactions.inTrigger(() -> {
            limitedObserver.onResponse("GET", uriWithoutHost, 200, PARTNER_HEADER);
            limitedObserver.onResponse("GET", uriWithoutHost, 200, operationHeader("partner-service GET /api/other"));
        });

        assertThat(reactions.actionFqnsOfSingleReaction())
                .describedAs("Without a host there is no coarse action to take the place of the operation")
                .containsExactly(PARTNER_OPERATION);
        assertThat(unidentifiedCalls("operation_limit")).isEqualTo(1);
    }

    @Test
    void withoutMeterRegistry_observesAsUsual() {
        HttpActionObserver observerWithoutMetrics = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), HttpObservationMetrics.NONE, LOG_INTERVAL);

        reactions.inTrigger(() -> observerWithoutMetrics.onResponse("GET", PARTNER_URI, 404, null));
        reactions.inTrigger(() -> observerWithoutMetrics.onNoResponse("GET", PARTNER_URI));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_COARSE_GET);
    }

    /**
     * The interceptor runs the observer fail-safe: a failure of a metric ends the observation step. The recorder
     * has to be told about the outcome of the call before that, otherwise a failed call would leave the
     * execution counting.
     */
    @Test
    void failingMetrics_theOutcomeOfTheCallIsRecordedFirst() {
        HttpObservationMetrics failingMetrics = new HttpObservationMetrics() {
            @Override
            public void unidentifiedCall(HttpObservationMetrics.UnidentifiedCallReason reason) {
                throw new IllegalStateException("simulated failure of a metric");
            }

            @Override
            public void droppedCall(HttpObservationMetrics.DroppedCallReason reason) {
                throw new IllegalStateException("simulated failure of a metric");
            }
        };
        HttpActionObserver observerWithFailingMetrics = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), failingMetrics, LOG_INTERVAL);

        reactions.inTrigger(() -> {
            reactions.recorder().onAction(MESSAGE_ACTION);
            assertThatThrownBy(() -> observerWithFailingMetrics.onNoResponse("GET", PARTNER_URI))
                    .isInstanceOf(IllegalStateException.class);
        });
        reactions.inTrigger(() -> {
            reactions.recorder().onAction(MESSAGE_ACTION);
            assertThatThrownBy(() -> observerWithFailingMetrics.onResponse("GET", PARTNER_URI, 503, null))
                    .isInstanceOf(IllegalStateException.class);
        });
        reactions.inTrigger(() -> assertThatThrownBy(() -> observerWithFailingMetrics.onResponse("GET", PARTNER_URI, 404, null))
                .isInstanceOf(IllegalStateException.class));

        assertThat(reactions.actionFqnsOfSingleReaction())
                .describedAs("Only the execution with the answered call counts")
                .containsExactly(PARTNER_COARSE_GET);
    }

    @Test
    void metrics_allCountersAreRegisteredAtStartup() {
        assertThat(meterRegistry.getMeters()).hasSize(7);
        assertNoMetricCounted();
    }

    private void assertNoMetricCounted() {
        assertThat(unidentifiedCalls("no_header") + unidentifiedCalls("invalid_header") + unidentifiedCalls("operation_limit")
                + unidentifiedCalls("no_response")).isZero();
        assertNoActionDropped();
    }

    private void assertNoActionDropped() {
        assertThat(droppedCalls("call_failed") + droppedCalls("target_limit") + droppedCalls("http_action_limit")).isZero();
    }

    private HttpActionObserver observerAcceptingOperations(int maxNewOperationCountPerTarget, int maxOperationCount) {
        return new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false), new HttpTargetRegistry(MAX_TARGET_COUNT),
                new HttpOperationRegistry(maxNewOperationCountPerTarget, maxOperationCount),
                new MicrometerHttpObservationMetrics(meterRegistry), LOG_INTERVAL);
    }

    private static HttpOperationRegistry unlimitedOperations() {
        return new HttpOperationRegistry(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static String operationHeader(String operation) {
        return "%\"" + operation + "\"";
    }

    private double unidentifiedCalls(String reason) {
        return meterRegistry.get("jeap.reaction.observer.unidentified.http.calls").tag("reason", reason).counter().count();
    }

    private double droppedCalls(String reason) {
        return meterRegistry.get("jeap.reaction.observer.dropped.http.calls").tag("reason", reason).counter().count();
    }
}
