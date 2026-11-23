package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReactionIdentifiedEventBuilderTest {

    private static final Instant IDENTIFICATION_START = Instant.parse("2026-10-26T04:12:33.987Z");
    private static final UUID SERVICE_INSTANCE = UUID.fromString("019a1f3c-5b7e-7c41-9d2a-6f0e8b4c1a55");
    private static final UUID OTHER_SERVICE_INSTANCE = UUID.fromString("019a1f3c-5b7e-7d93-b1c6-2a7f9e0d3b84");
    private static final Observation TRIGGER = Observation.ofEvent("OrderCreatedEvent", "orders");
    private static final Observation MESSAGE_ACTION = Observation.ofCommand("CreateDeclarationCommand", null, "declarations");
    private static final Observation HTTP_ACTION = Observation.ofHttpRequest("partner-service POST /api/jobs/{}:cancel");

    @Test
    void buildEvent_reportsTheReactionModelVersion() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        assertThat(buildEvent(reaction, ReactionModel.V1.version()).getPayload().getReactionModelVersion()).isEqualTo(1);
        assertThat(buildEvent(reaction, ReactionModel.V2.version()).getPayload().getReactionModelVersion()).isEqualTo(2);
    }

    @Test
    void buildEvent_idempotenceIdContainsServiceInstanceModelVersionAndIdentificationStart() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        ReactionIdentifiedEvent event = buildEvent(reaction, ReactionModel.V2.version());

        assertThat(event.getIdentity().getIdempotenceId())
                .isEqualTo("ri_test-service_" + SERVICE_INSTANCE + "_" + reaction.id() + "_v2_20261026T041233Z");
    }

    @Test
    void createIdempotenceId_differsByModelVersionAndIdentificationStart() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        String id = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", SERVICE_INSTANCE, reaction.id(), 1, IDENTIFICATION_START);
        String idOtherVersion = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", SERVICE_INSTANCE, reaction.id(), 2, IDENTIFICATION_START);
        String idOtherIdentificationStart = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", SERVICE_INSTANCE, reaction.id(), 1, IDENTIFICATION_START.plusSeconds(1));

        assertThat(List.of(id, idOtherVersion, idOtherIdentificationStart)).doesNotHaveDuplicates();
    }

    /**
     * The identification start is formatted to whole seconds: it does not tell apart two instances that start
     * within the same second, the instance identifier does.
     */
    @Test
    void createIdempotenceId_differsForInstancesStartedWithinTheSameSecond() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));
        Instant sameSecond = IDENTIFICATION_START.plusMillis(1);

        String id = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", SERVICE_INSTANCE, reaction.id(), 2, IDENTIFICATION_START);
        String idSameInstance = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", SERVICE_INSTANCE, reaction.id(), 2, sameSecond);
        String idOtherInstance = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", OTHER_SERVICE_INSTANCE, reaction.id(), 2, sameSecond);

        assertThat(idSameInstance).describedAs("the same second gives the same id").isEqualTo(id);
        assertThat(idOtherInstance).describedAs("another instance gives another id").isNotEqualTo(id);
    }

    @Test
    void buildEvent_withoutServiceInstanceIdentifier_fails() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        assertThatNullPointerException()
                .isThrownBy(() -> ReactionIdentifiedEventBuilder.buildEvent("test-system", "test-service", null,
                        reaction, 2, IDENTIFICATION_START))
                .withMessageContaining("serviceInstanceIdentifier");
    }

    @Test
    void buildEvent_withoutIdentificationStart_fails() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        assertThatNullPointerException()
                .isThrownBy(() -> ReactionIdentifiedEventBuilder.buildEvent("test-system", "test-service",
                        SERVICE_INSTANCE, reaction, 2, null))
                .withMessageContaining("identificationStart");
    }

    @Test
    void buildEvent_httpRequestAction_isReportedWithTypeFqnAndEncodedId() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION, HTTP_ACTION));

        ReactionIdentifiedEvent event = buildEvent(reaction, ReactionModel.V2.version());

        var reactionOnEvent = (ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction) event.getPayload().getReaction();
        assertThat(reactionOnEvent.getReactionId()).isEqualTo(reaction.id());
        var httpAction = reactionOnEvent.getActions().get(1);
        assertThat(httpAction.getType()).isEqualTo("http_request");
        assertThat(httpAction.getFqn()).isEqualTo("partner-service POST /api/jobs/{}:cancel");
        assertThat(httpAction.getId()).isEqualTo("http_request:partner-service POST /api/jobs/{}%3Acancel");
        assertThat(httpAction.getProps()).isEmpty();
    }

    @Test
    void buildEvent_actionOnlyHttpRequest() {
        Reaction reaction = Reaction.actionOnly(Observation.ofHttpRequest("//foo.bar.admin.ch POST /{*}"));

        ReactionIdentifiedEvent event = buildEvent(reaction, ReactionModel.V2.version());

        ActionOnly actionOnly = (ActionOnly) event.getPayload().getReaction();
        assertThat(actionOnly.getAction().getType()).isEqualTo("http_request");
        assertThat(actionOnly.getAction().getFqn()).isEqualTo("//foo.bar.admin.ch POST /{*}");
    }

    private static ReactionIdentifiedEvent buildEvent(Reaction reaction, int reactionModelVersion) {
        return ReactionIdentifiedEventBuilder.buildEvent("test-system", "test-service", SERVICE_INSTANCE, reaction, reactionModelVersion, IDENTIFICATION_START);
    }
}
