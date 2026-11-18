package ch.admin.bit.jeap.reaction.observer.header;

import ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test support encodes the response header for stubbed providers on its own, because the encoder of the
 * header module is not part of its API. This test keeps both encodings the same, with the shared test cases
 * of docs/response-header.md (except the shortening of long FQNs, which the test support does not do).
 */
class HttpRequestObservationsHeaderValueTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "partner-service GET /api/partners/{}",
            "svc GET /api/100%",
            "svc GET /api/\"quoted\"",
            "svc GET /api/a,b",
            "svc GET /api/{*}",
            "svc GET /api/x ;v=2",
            "svc GET /api/café"
    })
    void observationHeaderValue_isEncodedLikeTheHeaderModuleDoes(String fqn) {
        assertThat(HttpRequestObservations.observationHeaderValue(fqn))
                .isEqualTo(ReactionObservationEncoder.encode(fqn));
    }
}
