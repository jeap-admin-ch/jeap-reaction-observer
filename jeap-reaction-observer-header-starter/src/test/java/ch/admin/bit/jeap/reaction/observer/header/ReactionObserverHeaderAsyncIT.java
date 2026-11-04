package ch.admin.bit.jeap.reaction.observer.header;

import ch.admin.bit.jeap.reaction.observer.header.support.HeaderTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_MODEL_VERSION;
import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * The header is set in {@code preHandle} of the initial {@code REQUEST} dispatch, before the handler
 * returns a {@code Callable}/{@code DeferredResult}/SSE emitter and async processing starts - so it is
 * expected on the eventual response in all three cases.
 */
@SpringBootTest(classes = HeaderTestApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ReactionObserverHeaderAsyncIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void callable_returnsExpectedHeader() {
        ResponseEntity<String> response = getWithSignal("/api/callable");

        assertThat(response.getBody()).isEqualTo("callable-result");
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/callable"));
    }

    @Test
    void deferredResult_returnsExpectedHeader() {
        ResponseEntity<String> response = getWithSignal("/api/deferred");

        assertThat(response.getBody()).isEqualTo("deferred-result");
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/deferred"));
    }

    @Test
    void sse_returnsExpectedHeader() {
        ResponseEntity<String> response = getWithSignal("/api/sse");

        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/sse"));
    }

    private ResponseEntity<String> getWithSignal(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(REACTION_MODEL_VERSION, "2");
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
