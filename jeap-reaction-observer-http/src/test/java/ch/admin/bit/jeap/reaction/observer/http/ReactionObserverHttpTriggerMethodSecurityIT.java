package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Method security rejects a request inside the handler invocation, after the trigger has been started: the
 * exception leaves the dispatcher servlet unhandled, and the security filter chain turns it into the response.
 * Such a request has caused no action and does not count.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=order-service",
                "jeap.reaction.observer.model-version=2",
                "server.tomcat.threads.max=1",
                "server.tomcat.threads.min-spare=1"
        })
class ReactionObserverHttpTriggerMethodSecurityIT {

    private static final Observation ORDER_EXPORTED = Observation.ofEvent("OrderExported", "order-topic");

    @LocalServerPort
    private int port;
    @Autowired
    private ObservedReactions reactions;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        reactions.clear();
    }

    @Test
    void requestRejectedByMethodSecurity_403_doesNotCount() throws IOException, InterruptedException {
        assertThat(get("/api/admin/orders")).isEqualTo(403);

        ContainerThreadProbe.assertCounted(reactions, get(ContainerThreadProbe.PATH));
    }

    /**
     * The exception of the method security leaves Spring MVC, a filter of Spring Security turns it into the 403
     * afterwards. For the observation it is an exception nobody handled: the request does not count, though it
     * is answered with a 4xx and has caused an action.
     */
    @Test
    void requestRejectedByMethodSecurityAfterAnAction_403_doesNotCount() throws IOException, InterruptedException {
        assertThat(get("/api/orders/admin-export")).isEqualTo(403);

        ContainerThreadProbe.assertCounted(reactions, get(ContainerThreadProbe.PATH));
    }

    @Test
    void requestPermittedByMethodSecurity_counts() throws IOException, InterruptedException {
        assertThat(get("/api/orders")).isEqualTo(200);

        ContainerThreadProbe.assertCounted(reactions, get(ContainerThreadProbe.PATH),
                new Reaction(Observation.ofHttpRequest("order-service GET /api/orders"), List.of(ORDER_EXPORTED)));
    }

    private int get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // Nested classes of a test class are not found by the component scan, so the controller is imported
    @SpringBootApplication
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({SecuredOrderController.class, AdminExport.class})
    static class SecuredApplication {

        /**
         * Every request passes the filter chain as an anonymous user, access is decided at the methods only.
         */
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }

        @Bean
        FilterRegistrationBean<Filter> probeFilter(ReactionRecorder reactionRecorder) {
            return ContainerThreadProbe.filter(reactionRecorder);
        }
    }

    @Service
    static class AdminExport {

        @PreAuthorize("hasRole('ADMIN')")
        public void run() {
            // Never reached: the test calls it as an anonymous user
        }
    }

    @RestController
    static class SecuredOrderController {

        private final ReactionRecorder reactionRecorder;
        private final AdminExport adminExport;

        SecuredOrderController(ReactionRecorder reactionRecorder, AdminExport adminExport) {
            this.reactionRecorder = reactionRecorder;
            this.adminExport = adminExport;
        }

        @GetMapping("/api/orders/admin-export")
        void exportThenDenied() {
            reactionRecorder.onAction(ORDER_EXPORTED);
            adminExport.run();
        }

        @GetMapping("/api/admin/orders")
        @PreAuthorize("hasRole('ADMIN')")
        void exportAsAdmin() {
            reactionRecorder.onAction(ORDER_EXPORTED);
        }

        @GetMapping("/api/orders")
        @PreAuthorize("permitAll()")
        void export() {
            reactionRecorder.onAction(ORDER_EXPORTED);
        }
    }
}
