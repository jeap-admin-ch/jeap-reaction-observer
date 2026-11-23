package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: the calls of declarative HTTP service clients must show up as actions, otherwise reactions
 * miss actions. For clients declared with {@code @ImportHttpServices} (HTTP service groups) Spring Boot builds
 * the RestClients itself, the application never touches the RestClient.Builder. They are observed because
 * Spring Boot applies the RestClientCustomizers to them, too - this test fails if a Spring Boot version ever
 * stops doing so.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.application.name=provider-app",
                "jeap.reaction.observer.model-version=2",
                // The test is about the calling side: the requests the provider receives are not observed as triggers
                "spring.autoconfigure.exclude=ch.admin.bit.jeap.reaction.observer.http.ReactionObserverHttpTriggerAutoConfiguration"
        })
class ReactionObserverHttpServiceGroupIT {

    private static final int PORT = freePort();

    @Autowired
    private PartnerClient partnerClient;
    @Autowired
    private ObservedReactions reactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("spring.http.serviceclient.partner.base-url", () -> "http://localhost:" + PORT);
    }

    @BeforeEach
    void setUp() {
        reactions.clear();
    }

    @Test
    void httpServiceClientOfAGroup_isObserved() {
        reactions.inTrigger(() -> assertThat(partnerClient.partner("42")).isEqualTo("partner 42"));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("provider-app GET /api/partners/{}");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @HttpExchange("/api/partners")
    interface PartnerClient {

        @GetExchange("/{id}")
        String partner(@PathVariable String id);
    }

    // Nested classes of a test class are not found by the component scan, so the controller is imported
    @SpringBootApplication
    @Import(ReactionObserverHttpProviderIT.PartnerController.class)
    @ImportHttpServices(group = "partner", types = PartnerClient.class)
    static class ServiceGroupApplication {

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }
    }
}
