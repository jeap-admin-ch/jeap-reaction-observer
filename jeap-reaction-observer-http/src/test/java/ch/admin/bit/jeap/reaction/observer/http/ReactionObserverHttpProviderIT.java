package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumer and provider side of the header protocol together: the RestClient of the Spring Boot builder calls
 * a real provider that answers with the header module of this library.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=provider-app",
                "jeap.reaction.observer.model-version=2"
        })
class ReactionObserverHttpProviderIT {

    @LocalServerPort
    private int port;
    @Autowired
    private RestClient.Builder restClientBuilder;
    @Autowired
    private ObservedReactions reactions;
    @Autowired
    private MeterRegistry meterRegistry;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = restClientBuilder.clone().baseUrl("http://localhost:" + port).build();
        reactions.clear();
    }

    @Test
    void providerNamesTheOperation_fineAction() {
        reactions.inTrigger(() -> {
            assertThat(restClient.get().uri("/api/partners/42").retrieve().body(String.class)).isEqualTo("partner 42");
            restClient.post().uri("/api/partners/42/credit-reservations").retrieve().toBodilessEntity();
        });

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(
                "provider-app GET /api/partners/{}",
                "provider-app POST /api/partners/{}/credit-reservations");
        assertThat(reactions.identified().getFirst().actions()).allMatch(action -> action.type() == ObservationType.HTTP_REQUEST);
    }

    @Test
    void providerAnswersWithAnErrorAndNamesTheOperation_fineAction() {
        reactions.inTrigger(() -> {
            assertThatThrownBy(() -> restClient.get().uri("/api/conflict").retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Conflict.class);
            assertThatThrownBy(() -> restClient.get().uri("/api/failing").retrieve().toBodilessEntity())
                    .isInstanceOf(HttpServerErrorException.InternalServerError.class);
        });

        assertThat(reactions.actionFqnsOfSingleReaction())
                .describedAs("The operation is identified and took place, the reaction counts also with a status of 500")
                .containsExactly("provider-app GET /api/conflict", "provider-app GET /api/failing");
        assertThat(reactions.countsSinceLastCall()).containsValue(1);
    }

    @Test
    void noMappingForTheRequest_404WithoutHeader_coarseAction() {
        double noHeaderBefore = unidentifiedCalls("no_header");

        reactions.inTrigger(() -> {
            assertThatThrownBy(() -> restClient.get().uri("/some-service/api/unknown").retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
            assertThatThrownBy(() -> restClient.get().uri("/unknown").retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        });

        assertThat(reactions.actionFqnsOfSingleReaction())
                .containsExactly("//localhost/some-service GET /{*}", "//localhost GET /{*}");
        assertThat(unidentifiedCalls("no_header") - noHeaderBefore).isEqualTo(2);
    }

    /**
     * The client follows a redirect below the interceptor. The coarse action names the host of the request the
     * service made (localhost), not the target of the redirect (127.0.0.1).
     */
    @Test
    void redirectToATargetThatDoesNotNameTheOperation_coarseActionNamesTheHostOfTheRequest() {
        reactions.inTrigger(() ->
                assertThatThrownBy(() -> restClient.get().uri("/api/redirect").retrieve().toBodilessEntity())
                        .isInstanceOf(HttpClientErrorException.NotFound.class));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//localhost GET /{*}");
    }

    @Test
    void targetDoesNotAnswer_executionIsDiscarded() throws IOException {
        double callFailedBefore = droppedCalls("call_failed");
        double noResponseBefore = unidentifiedCalls("no_response");
        int closedPort = closedPort();

        reactions.inTrigger(() -> {
            restClient.get().uri("/api/partners/42").retrieve().toBodilessEntity();
            assertThatThrownBy(() -> restClient.get().uri("http://localhost:" + closedPort + "/api/x").retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class);
        });

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertThat(droppedCalls("call_failed") - callFailedBefore).isEqualTo(1);
        assertThat(unidentifiedCalls("no_response") - noResponseBefore).isEqualTo(1);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private double unidentifiedCalls(String reason) {
        return meterRegistry.get("jeap.reaction.observer.unidentified.http.calls").tag("reason", reason).counter().count();
    }

    private double droppedCalls(String reason) {
        return meterRegistry.get("jeap.reaction.observer.dropped.http.calls").tag("reason", reason).counter().count();
    }

    // Nested classes of a test class are not found by the component scan, so the controller is imported
    @SpringBootApplication
    @Import(PartnerController.class)
    static class ProviderApplication {

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @RestController
    static class PartnerController {

        @GetMapping("/api/partners/{id}")
        String partner(@PathVariable String id) {
            return "partner " + id;
        }

        @PostMapping("/api/partners/{id}/credit-reservations")
        void reserveCredit(@PathVariable String id) {
            // Nothing to do, the test is about the response header
        }

        @GetMapping("/api/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @GetMapping("/api/redirect")
        ResponseEntity<Void> redirect(HttpServletRequest request) {
            URI unmappedPathOnAnotherHost = URI.create("http://127.0.0.1:" + request.getServerPort() + "/unknown");
            return ResponseEntity.status(HttpStatus.FOUND).location(unmappedPathOnAnotherHost).build();
        }

        @GetMapping("/api/failing")
        void failing() {
            throw new IllegalStateException("simulated failure of the provider");
        }
    }
}
