package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The response header of the header module is switched off. The incoming requests are observed as triggers all
 * the same, with the name the header would have carried.
 */
@SpringBootTest(
        classes = ReactionObserverHttpTriggerIT.OrderApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=order-service",
                "jeap.reaction.observer.model-version=2",
                "jeap.reaction.observer.header.enabled=false",
                "server.tomcat.threads.max=1",
                "server.tomcat.threads.min-spare=1",
                "stock-service.url=http://localhost:1"
        })
class ReactionObserverHttpTriggerHeaderSwitchedOffIT {

    @LocalServerPort
    private int port;
    @Autowired
    private ObservedReactions reactions;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void headerSwitchedOff_noHeader_butTheTriggerWithTheSameName() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/api/orders/42", "JEAP-REACTION-MODEL-VERSION", "2");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("JEAP-REACTION-OBSERVATION")).isEmpty();
        Reaction expected = new Reaction(Observation.ofHttpRequest("order-service GET /api/orders/{}"), List.of());
        ContainerThreadProbe.assertCounted(reactions, get(ContainerThreadProbe.PATH).statusCode(), expected);
    }

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
