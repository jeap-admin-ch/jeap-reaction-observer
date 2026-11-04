package ch.admin.bit.jeap.reaction.observer.header;

import ch.admin.bit.jeap.reaction.observer.header.support.HeaderTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_MODEL_VERSION;
import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * End-to-end: a request with the {@code JEAP-REACTION-MODEL-VERSION} signal gets the
 * {@code JEAP-REACTION-OBSERVATION} header, a request without it does not - including on error responses.
 * Runs against a real embedded container (not MockMvc) so that container-level behaviour around error
 * dispatch handling is exercised for real, not assumed.
 */
@SpringBootTest(classes = HeaderTestApplication.class, webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ReactionObserverHeaderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebMvcEndpointHandlerMapping actuatorHandlerMapping;

    @Test
    void withSignal_controllerMethod_returnsExpectedHeader() {
        ResponseEntity<String> response = getWithSignal("/api/items/42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/items/{}"));
    }

    @Test
    void withoutSignal_controllerMethod_noHeader() {
        ResponseEntity<String> response = getWithoutSignal("/api/items/42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }

    @Test
    void wildcardPathVariable_isNormalizedToStarPlaceholder() {
        ResponseEntity<String> response = getWithSignal("/api/wild/a/b/c");

        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/wild/{*}"));
    }

    @Test
    void trailingSlashInPattern_isDropped() {
        ResponseEntity<String> response = getWithSignal("/api/trailing/");

        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/trailing"));
    }

    @Test
    void regexPathVariable_isNormalizedToEmptyPlaceholder() {
        ResponseEntity<String> response = getWithSignal("/api/regex/123");

        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/regex/{}"));
    }

    @Test
    void functionalEndpoint_returnsExpectedHeader() {
        ResponseEntity<String> response = getWithSignal("/functional/7");

        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /functional/{}"));
    }

    @Test
    void staticResource_noHeaderEvenWithSignal() {
        ResponseEntity<String> response = getWithSignal("/robots.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }

    @Test
    void actuatorEndpoint_outsideInterceptorHandlerMapping_noHeaderEvenWithSignal() {
        // WebMvcEndpointHandlerMapping does not receive InterceptorRegistry interceptors.
        // This verifies the mapping boundary, not OperationNameResolver's package-prefix safety net.
        ResponseEntity<String> response = getWithSignal("/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator"})
    void actuatorHandler_isExcludedByOperationNameResolver(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        ServletRequestPathUtils.parseAndCache(request);

        HandlerExecutionChain chain = actuatorHandlerMapping.getHandler(request);

        assertThat(chain).isNotNull();
        assertThat(chain.getHandler()).isInstanceOf(HandlerMethod.class);
        assertThat(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).isEqualTo(path);
        assertThat(new OperationNameResolver("test-app").resolve(request, chain.getHandler())).isEmpty();
    }

    @Test
    void springdocEndpoint_noHeaderEvenWithSignal() {
        ResponseEntity<String> response = getWithSignal("/v3/api-docs");

        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }

    @Test
    void undefinedPath_noMatchingHandler_noHeader() {
        // Documented limit: preHandle never runs when no handler matches at all.
        ResponseEntity<String> response = getWithSignal("/no/such/path");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().get(REACTION_OBSERVATION)).isNull();
    }

    @Test
    void controllerAdviceError_stillReturnsHeader() {
        ResponseEntity<String> response = getWithSignal("/api/advice-error");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/advice-error"));
    }

    @Test
    void responseStatusException_stillReturnsHeader() {
        ResponseEntity<String> response = getWithSignal("/api/response-status-error");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/response-status-error"));
    }

    @Test
    void unhandledException_errorDispatch_stillReturnsHeader() {
        ResponseEntity<String> response = getWithSignal("/api/unhandled");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getFirst(REACTION_OBSERVATION))
                .isEqualTo(ReactionObservationEncoder.encode("test-app GET /api/unhandled"));
    }

    @Test
    void varyHeader_isAddedForAnObservableOperation_evenWithoutTheSignal() {
        // The response depends on the signal for every observable operation, whether or not this
        // particular request happened to carry it - so Vary is added either way.
        ResponseEntity<String> response = getWithoutSignal("/api/items/1");

        assertThat(response.getHeaders().get("Vary")).contains(REACTION_MODEL_VERSION);
    }

    @Test
    void bffPassthrough_ownValue_isNotOverwrittenByADownstreamValue() {
        ResponseEntity<String> response = getWithSignal("/api/bff");

        assertThat(response.getHeaders().get(REACTION_OBSERVATION))
                .containsExactly(ReactionObservationEncoder.encode("test-app GET /api/bff"));
    }

    private ResponseEntity<String> getWithSignal(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(REACTION_MODEL_VERSION, "2");
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> getWithoutSignal(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }
}
