package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestObservationTest {

    @Test
    void ofHttpRequest_hasTypeFqnAndNoProps() {
        Observation observation = Observation.ofHttpRequest("partner-service GET /api/partners/{}");

        assertThat(observation.type()).isEqualTo(ObservationType.HTTP_REQUEST);
        assertThat(observation.fqn()).isEqualTo("partner-service GET /api/partners/{}");
        assertThat(observation.props()).isEmpty();
        assertThat(observation.id().value()).isEqualTo("http_request:partner-service GET /api/partners/{}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            svc POST /api/jobs/{}:cancel         | http_request:svc POST /api/jobs/{}%3Acancel
            svc POST /api/jobs/{}_cancel         | http_request:svc POST /api/jobs/{}_cancel
            svc GET /api/100%                    | http_request:svc GET /api/100%25
            svc GET /api/a%3Ab                   | http_request:svc GET /api/a%253Ab
            svc GET /api/a#b                     | http_request:svc GET /api/a%23b
            svc GET /api/a&b                     | http_request:svc GET /api/a%26b
            svc GET /api/café                    | http_request:svc GET /api/café
            svc GET /api/x ;v=2                  | http_request:svc GET /api/x ;v=2
            //foo.bar.admin.ch POST /{*}         | http_request://foo.bar.admin.ch POST /{*}
            //foo.bar.admin.ch:8443/svc GET /{*} | http_request://foo.bar.admin.ch%3A8443/svc GET /{*}
            """)
    void id_encodesOnlyTheCharactersReservedInIds(String fqn, String expectedId) {
        assertThat(Observation.ofHttpRequest(fqn).id().value()).isEqualTo(expectedId);
    }

    @Test
    void id_ofOperationsDifferingOnlyInSpecialCharacters_isDifferent() {
        List<String> ids = List.of(
                        "svc POST /api/jobs/{}:cancel",
                        "svc POST /api/jobs/{}_cancel",
                        "svc POST /api/jobs/{}#cancel",
                        "svc POST /api/jobs/{}&cancel",
                        "svc POST /api/jobs/{}%cancel",
                        "svc POST /api/jobs/{}%3Acancel",
                        "svc POST /api/jobs/{}écancel",
                        "a".repeat(447) + "~e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        "a".repeat(447) + "~e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b856").stream()
                .map(fqn -> Observation.ofHttpRequest(fqn).id().value())
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void id_neverContainsTheSeparatorsOfIds() {
        String id = Observation.ofHttpRequest("s:v#c GET /a&b:c#d").id().value();

        assertThat(id.substring("http_request:".length())).doesNotContain(":", "#", "&");
    }

    @Test
    void id_ofMessages_isUnchanged() {
        assertThat(Observation.ofEvent("Some:Event#1", "orders").id().value())
                .isEqualTo("event:Some_Event_1:b42330e232ad67cf441c09449b16f66e");
    }

    @Test
    void reactionId_ofMessageOnlyReaction_isUnchanged() {
        Observation trigger = Observation.ofEvent("OrderCreatedEvent", "orders");
        Observation action = Observation.ofCommand("CreateDeclarationCommand", null, "declarations");

        Reaction reaction = new Reaction(trigger, List.of(action));

        assertThat(trigger.id().value()).isEqualTo("event:OrderCreatedEvent:b42330e232ad67cf441c09449b16f66e");
        assertThat(action.id().value()).isEqualTo("command:CreateDeclarationCommand:c1a8e2a2b73e7e7e99fd5d4925e635c7");
        assertThat(reaction.id())
                .isEqualTo("event:OrderCreatedEvent:b42330e232ad67cf441c09449b16f66e#195ed867b03a5e7b827bacaf2f825374");
    }

    @Test
    void reactionId_withHttpAction_differsFromTheReactionWithoutIt() {
        Observation trigger = Observation.ofEvent("OrderCreatedEvent", "orders");
        Observation messageAction = Observation.ofCommand("CreateDeclarationCommand", null, "declarations");
        Observation httpAction = Observation.ofHttpRequest("partner-service GET /api/partners/{}");

        Reaction withHttpAction = new Reaction(trigger, List.of(messageAction, httpAction));
        Reaction withoutHttpAction = new Reaction(trigger, List.of(messageAction));

        assertThat(withHttpAction.id())
                .startsWith(trigger.id().value() + "#")
                .isNotEqualTo(withoutHttpAction.id());
    }
}
