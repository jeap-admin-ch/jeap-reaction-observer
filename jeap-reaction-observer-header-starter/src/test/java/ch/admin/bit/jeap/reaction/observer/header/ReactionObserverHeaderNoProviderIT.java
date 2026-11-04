package ch.admin.bit.jeap.reaction.observer.header;

import ch.admin.bit.jeap.reaction.observer.header.support.HeaderTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_MODEL_VERSION;
import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Without a provider name the module never sets its own header value; a downstream value copied by a BFF
 * must still not reach the caller.
 */
@SpringBootTest(classes = HeaderTestApplication.class, webEnvironment = RANDOM_PORT,
        properties = "spring.application.name=")
@AutoConfigureTestRestTemplate
class ReactionObserverHeaderNoProviderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void bffPassthrough_noOwnValue_downstreamValueIsDropped() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(REACTION_MODEL_VERSION, "2");

        ResponseEntity<String> response =
                restTemplate.exchange("/api/bff", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getBody()).isEqualTo("bff-result");
        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }
}
