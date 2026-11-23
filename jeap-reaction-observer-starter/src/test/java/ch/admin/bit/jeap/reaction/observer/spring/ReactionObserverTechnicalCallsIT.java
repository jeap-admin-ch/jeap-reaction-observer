package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Observation;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents;
import ch.admin.bit.jeap.reaction.observer.test.ReactionKafkaTestBase;
import ch.admin.bit.jeap.security.resource.introspection.DefaultJeapTokenIntrospectorFactory;
import ch.admin.bit.jeap.security.resource.introspection.JeapTokenIntrospector;
import ch.admin.bit.jeap.security.resource.introspection.JeapTokenIntrospectorConfiguration;
import ch.admin.bit.jeap.security.restclient.JeapOAuth2RestClientBuilderFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.REACTION_MODEL_VERSION_HEADER;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.REACTION_OBSERVATION_HEADER;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.fqn;
import static ch.admin.bit.jeap.reaction.observer.test.HttpRequestObservations.observationHeaderValue;
import static ch.admin.bit.jeap.reaction.observer.test.ReactionIdentifiedEvents.hasHttpRequestAction;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The technical calls of the jEAP security starters are no actions of a service: the token request of an OAuth2
 * client and the token introspection of a resource server. Observed, the token request would add a second
 * variant with the token endpoint to every reaction with a protected call, and the introspection would show up
 * as a reaction without a trigger.
 */
@SpringBootTest(properties = {
        "jeap.reaction.observer.model-version=2",
        "spring.security.oauth2.client.registration.test-client.client-id=client-id",
        "spring.security.oauth2.client.registration.test-client.client-secret=client-secret",
        "spring.security.oauth2.client.registration.test-client.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.test-client.provider=mock-provider",
        "spring.security.oauth2.client.registration.token-test-client.client-id=token-test-client-id",
        "spring.security.oauth2.client.registration.token-test-client.client-secret=client-secret",
        "spring.security.oauth2.client.registration.token-test-client.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.token-test-client.provider=mock-provider",
        "spring.security.oauth2.client.provider.mock-provider.token-uri=${wiremock.server.baseUrl}" + ReactionObserverTechnicalCallsIT.TOKEN_PATH
})
@EnableWireMock
class ReactionObserverTechnicalCallsIT extends ReactionKafkaTestBase {

    static final String TOKEN_PATH = "/oauth/token";
    private static final String INTROSPECTION_PATH = "/oauth/introspect";
    private static final String RESOURCE_PATH = "/resource-service/api/resources/1";
    private static final String RESOURCE_OPERATION = fqn("resource-service", "GET", "/api/resources/{}");
    private static final String COARSE_ACTION_PREFIX = "//";

    @InjectWireMock
    private WireMockServer wireMock;
    @Autowired
    private JeapOAuth2RestClientBuilderFactory jeapOAuth2RestClientBuilderFactory;
    @Autowired
    private RestClient.Builder restClientBuilder;
    @Autowired
    private ReactionEventsTestConsumer reactionEventsTestConsumer;

    /**
     * Uses a client registration of its own: the token of a registration is stored for the whole context, and a
     * test that ran before with the same registration would leave no token request to verify.
     */
    @Test
    void tokenRequestOfAnOAuth2Client_isNoAction() {
        stubTokenEndpoint();
        stubOperation(RESOURCE_PATH, RESOURCE_OPERATION);
        RestClient oauth2RestClient = jeapOAuth2RestClientBuilderFactory.createForClientRegistryId("token-test-client").build();

        oauth2RestClient.get().uri(wireMock.baseUrl() + RESOURCE_PATH).retrieve().toBodilessEntity();

        // The protected call is observed, it carries the access token and the request signal
        wireMock.verify(1, getRequestedFor(urlEqualTo(RESOURCE_PATH))
                .withHeader("Authorization", equalTo("Bearer the-access-token"))
                .withHeader(REACTION_MODEL_VERSION_HEADER, equalTo("2")));
        // The token request took place, without the request signal
        wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)).withoutHeader(REACTION_MODEL_VERSION_HEADER));
        assertThat(actionsOfAllReactionsIdentified("after-token-request"))
                .contains(RESOURCE_OPERATION)
                .describedAs("Observed, the token request would be a coarse action: the token endpoint does not name its operation")
                .noneMatch(action -> action.startsWith(COARSE_ACTION_PREFIX));
    }

    /**
     * The provider names its operation also in a client error: the protected call is a fine action, and the
     * reaction is reported.
     */
    @Test
    void clientErrorOfAProtectedCall_isAFineAction() {
        String path = "/resource-service/api/missing-resources/1";
        String operation = fqn("resource-service", "GET", "/api/missing-resources/{}");
        stubTokenEndpoint();
        wireMock.stubFor(get(path).willReturn(notFound().withHeader(REACTION_OBSERVATION_HEADER, observationHeaderValue(operation))));
        RestClient oauth2RestClient = jeapOAuth2RestClientBuilderFactory.createForClientRegistryId("test-client").build();
        String url = wireMock.baseUrl() + path;

        assertThatThrownBy(oauth2RestClient.get().uri(url).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        wireMock.verify(1, getRequestedFor(urlEqualTo(path)).withHeader(REACTION_MODEL_VERSION_HEADER, equalTo("2")));
        reactionEventsTestConsumer.awaitReactionIdentifiedEvent(hasHttpRequestAction(operation));
    }

    /**
     * The provider names its operation also in a server error: the protected call is a fine action, the
     * execution is not discarded, and the reaction is reported.
     */
    @Test
    void serverErrorWithObservationHeaderOfAProtectedCall_isAFineAction() {
        String path = "/resource-service/api/failing-resources/1";
        String operation = fqn("resource-service", "GET", "/api/failing-resources/{}");
        stubTokenEndpoint();
        wireMock.stubFor(get(path).willReturn(serverError().withHeader(REACTION_OBSERVATION_HEADER, observationHeaderValue(operation))));
        RestClient oauth2RestClient = jeapOAuth2RestClientBuilderFactory.createForClientRegistryId("test-client").build();
        String url = wireMock.baseUrl() + path;

        assertThatThrownBy(oauth2RestClient.get().uri(url).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.InternalServerError.class);

        wireMock.verify(1, getRequestedFor(urlEqualTo(path)).withHeader(REACTION_MODEL_VERSION_HEADER, equalTo("2")));
        reactionEventsTestConsumer.awaitReactionIdentifiedEvent(hasHttpRequestAction(operation));
    }

    @Test
    void tokenIntrospectionOfAResourceServer_isNoAction() {
        wireMock.stubFor(post(INTROSPECTION_PATH).willReturn(okJson("""
                {"active": true, "sub": "test-subject"}
                """)));
        JeapTokenIntrospector tokenIntrospector = new DefaultJeapTokenIntrospectorFactory(Optional.empty())
                .create(new JeapTokenIntrospectorConfiguration(wireMock.baseUrl(), wireMock.baseUrl() + INTROSPECTION_PATH,
                        "client-id", "client-secret", Duration.ofSeconds(5), Duration.ofSeconds(5)));

        Map<String, Object> attributes = tokenIntrospector.introspect("some-token");

        assertThat(attributes).containsEntry("sub", "test-subject");
        wireMock.verify(1, postRequestedFor(urlEqualTo(INTROSPECTION_PATH)).withoutHeader(REACTION_MODEL_VERSION_HEADER));
        assertThat(actionsOfAllReactionsIdentified("after-introspection"))
                .describedAs("Observed, the introspection would be a coarse action: the endpoint does not name its operation")
                .noneMatch(action -> action.startsWith(COARSE_ACTION_PREFIX));
    }

    /**
     * Makes a last observed call and waits for its reaction, so that every reaction identified before has
     * arrived, too.
     *
     * @param lastOperation a name for the last call that no other test uses: each reaction is reported once
     */
    private List<String> actionsOfAllReactionsIdentified(String lastOperation) {
        String lastPath = "/last-service/api/" + lastOperation;
        String lastOperationFqn = fqn("last-service", "GET", "/api/" + lastOperation);
        stubOperation(lastPath, lastOperationFqn);
        restClientBuilder.build().get().uri(wireMock.baseUrl() + lastPath).retrieve().toBodilessEntity();
        reactionEventsTestConsumer.awaitReactionIdentifiedEvent(hasHttpRequestAction(lastOperationFqn));
        return reactionEventsTestConsumer.getReactionIdentifiedEvents().values().stream()
                .map(ReactionIdentifiedEvents::actions)
                .flatMap(List::stream)
                .map(Observation::getFqn)
                .toList();
    }

    private void stubTokenEndpoint() {
        wireMock.stubFor(post(TOKEN_PATH).willReturn(okJson("""
                {"access_token": "the-access-token", "token_type": "Bearer", "expires_in": 300}
                """)));
    }

    private void stubOperation(String path, String operation) {
        wireMock.stubFor(get(path).willReturn(ok().withHeader(REACTION_OBSERVATION_HEADER, observationHeaderValue(operation))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ReactionEventsTestConsumer reactionEventsTestConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
