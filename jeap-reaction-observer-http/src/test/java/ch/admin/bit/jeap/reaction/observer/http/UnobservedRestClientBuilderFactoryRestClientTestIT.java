package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A service tests a component that calls another service with {@code @RestClientTest}. That slice only loads
 * the auto-configurations registered for it, so the factory for clients without observation has to be one of
 * them: otherwise the test of a component that uses the factory does not start. As the factory lets Spring
 * Boot configure its builders, their clients are bound to the mock server of the slice like all others.
 */
@RestClientTest
class UnobservedRestClientBuilderFactoryRestClientTestIT {

    private static final String URL = "https://documents.example.org/api/documents/42";

    @Autowired
    private RestClient uploadClient;
    @Autowired
    private MockRestServiceServer server;

    @Test
    void componentUsingTheFactory_isTestableInTheRestClientTestSlice() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThat(uploadClient.get().uri(URL).retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);

        server.verify();
    }

    @SpringBootConfiguration
    static class TestApplication {

        @Bean
        RestClient uploadClient(UnobservedRestClientBuilderFactory unobservedRestClientBuilderFactory) {
            return unobservedRestClientBuilderFactory.create().build();
        }
    }
}
