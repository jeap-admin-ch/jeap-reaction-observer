package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionIdentifiedEventBuilderTest {

    private static final Instant IDENTIFICATION_START = Instant.parse("2026-10-26T04:12:33.987Z");
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
    void buildEvent_idempotenceIdContainsModelVersionAndIdentificationStart() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        ReactionIdentifiedEvent event = buildEvent(reaction, ReactionModel.V2.version());

        assertThat(event.getIdentity().getIdempotenceId())
                .isEqualTo("ri_test-service_" + reaction.id() + "_v2_20261026T041233Z");
    }

    @Test
    void buildEvent_idempotenceIdDiffersByModelVersionAndIdentificationStart() {
        Reaction reaction = new Reaction(TRIGGER, List.of(MESSAGE_ACTION));

        String id = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", reaction.id(), 1, IDENTIFICATION_START);
        String idOtherVersion = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", reaction.id(), 2, IDENTIFICATION_START);
        String idOtherIdentificationStart = ReactionIdentifiedEventBuilder.createIdempotenceId("svc", reaction.id(), 1, IDENTIFICATION_START.plusSeconds(1));

        assertThat(List.of(id, idOtherVersion, idOtherIdentificationStart)).doesNotHaveDuplicates();
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
        return ReactionIdentifiedEventBuilder.buildEvent("test-system", "test-service", reaction, reactionModelVersion, IDENTIFICATION_START);
    }
}
