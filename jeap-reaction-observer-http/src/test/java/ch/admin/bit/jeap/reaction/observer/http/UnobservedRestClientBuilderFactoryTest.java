package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UnobservedRestClientBuilderFactoryTest {

    private static final String MODEL_VERSION_2 = "jeap.reaction.observer.model-version=2";
    private static final String URL = "https://partner.example.org/api/partners/42";
    private static final String MODEL_VERSION_HEADER = "JEAP-REACTION-MODEL-VERSION";
    private static final String OBSERVATION_HEADER_VALUE = "%\"partner-service GET /api/partners/{}\"";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                    ReactionObserverHttpAutoConfiguration.class, UnobservedRestClientBuilderAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void unobservedBuilder_requestIsNoAction() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            RestClient.Builder builder = context.getBean(UnobservedRestClientBuilderFactory.class).create();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(headerDoesNotExist(MODEL_VERSION_HEADER))
                    .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", OBSERVATION_HEADER_VALUE));

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> builder.build().get().uri(URL).retrieve().toBodilessEntity());

            server.verify();
            assertThat(reactions.actionFqnsOfSingleReaction()).isEmpty();
        });
    }

    /**
     * What the factory is for. Spring buffers the request body of a client that has interceptors: it reads the
     * whole body into memory before it asks the request factory for the request. Without interceptors the
     * request is there first, and the body is written to it.
     */
    @Test
    void unobservedBuilder_requestBodyIsNotBuffered() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            RestClient.Builder unobserved = context.getBean(UnobservedRestClientBuilderFactory.class).create();
            RestClient.Builder observed = context.getBean(RestClient.Builder.class);

            assertThat(isBodyReadBeforeTheRequestIsCreated(unobserved)).isFalse();
            assertThat(isBodyReadBeforeTheRequestIsCreated(observed)).isTrue();
        });
    }

    @Test
    void unobservedBuilder_otherCustomizersAreApplied() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            RestClient.Builder builder = context.getBean(UnobservedRestClientBuilderFactory.class).create();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header(TestConfiguration.OTHER_HEADER, "set"))
                    .andRespond(withSuccess());

            builder.build().get().uri(URL).retrieve().toBodilessEntity();

            server.verify();
        });
    }

    @Test
    void builderOfSpringBoot_isStillObservedAfterAnUnobservedBuilder() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            context.getBean(UnobservedRestClientBuilderFactory.class).create();

            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header(MODEL_VERSION_HEADER, "2"))
                    .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", OBSERVATION_HEADER_VALUE));

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> builder.build().get().uri(URL).retrieve().toBodilessEntity());

            server.verify();
            assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("partner-service GET /api/partners/{}");
        });
    }

    /**
     * The way to an HTTP interface client without observation, see docs/http-requests.md.
     */
    @Test
    void httpInterfaceClientOnAnUnobservedRestClient_requestIsNoAction() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            RestClient.Builder builder = context.getBean(UnobservedRestClientBuilderFactory.class).create()
                    .baseUrl("https://partner.example.org");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(headerDoesNotExist(MODEL_VERSION_HEADER))
                    .andRespond(withSuccess());
            PartnerClient partnerClient = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                    .build().createClient(PartnerClient.class);

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(partnerClient::partner);

            server.verify();
            assertThat(reactions.actionFqnsOfSingleReaction()).isEmpty();
        });
    }

    /**
     * A service that uses the factory has to start whatever the configuration of the Reaction Observer is.
     */
    @ParameterizedTest
    @ValueSource(strings = {"jeap.reaction.observer.model-version=1", "jeap.reaction.observer.enabled=false"})
    void httpRequestsNotObserved_factoryCreatesTheBuilderOfSpringBoot(String property) {
        contextRunner.withPropertyValues(property).run(context -> {
            assertThat(context).doesNotHaveBean(ReactionObservingClientHttpRequestInterceptor.class);

            RestClient.Builder builder = context.getBean(UnobservedRestClientBuilderFactory.class).create();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header(TestConfiguration.OTHER_HEADER, "set"))
                    .andRespond(withSuccess());

            builder.build().get().uri(URL).retrieve().toBodilessEntity();

            server.verify();
        });
    }

    /**
     * With a builder bean of its own, a service replaces the one of Spring Boot. The factory must not hand out
     * that bean: it would be observed, and every caller would change the same builder.
     */
    @Test
    void serviceWithItsOwnBuilderBean_factoryCreatesNewUnobservedBuilders() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).withUserConfiguration(OwnBuilderConfiguration.class).run(context -> {
            RestClient.Builder ownBuilder = context.getBean(RestClient.Builder.class);
            UnobservedRestClientBuilderFactory factory = context.getBean(UnobservedRestClientBuilderFactory.class);

            RestClient.Builder unobserved = factory.create();

            assertThat(unobserved).isNotSameAs(ownBuilder).isNotSameAs(factory.create());
            assertThat(sendsTheModelVersionHeader(unobserved)).isFalse();
            assertThat(sendsTheModelVersionHeader(ownBuilder)).isTrue();
        });
    }

    /**
     * Only the builder the factory creates is left alone, not one that another customizer asks for meanwhile.
     */
    @Test
    void builderOfSpringBootCreatedWhileAnUnobservedOneIsConfigured_isObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            NestedBuilderCreation nested = context.getBean(NestedBuilderCreation.class);
            nested.whileTheNextBuilderIsConfigured(() -> context.getBean(RestClient.Builder.class));

            RestClient.Builder unobserved = context.getBean(UnobservedRestClientBuilderFactory.class).create();

            assertThat(sendsTheModelVersionHeader(nested.created())).isTrue();
            assertThat(sendsTheModelVersionHeader(unobserved)).isFalse();
        });
    }

    /**
     * The nested builder is done before the observation reaches the enclosing one, which has to stay unobserved.
     */
    @Test
    void unobservedBuilderCreatedWhileAnotherOneIsConfigured_bothAreUnobserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            UnobservedRestClientBuilderFactory factory = context.getBean(UnobservedRestClientBuilderFactory.class);
            NestedBuilderCreation nested = context.getBean(NestedBuilderCreation.class);
            nested.whileTheNextBuilderIsConfigured(factory::create);

            RestClient.Builder unobserved = factory.create();

            assertThat(sendsTheModelVersionHeader(nested.created())).isFalse();
            assertThat(sendsTheModelVersionHeader(unobserved)).isFalse();
        });
    }

    /**
     * Spring Boot creates all customizers when it creates its builder configurer. A customizer that gets the
     * factory injected must not close a cycle: the factory may only ask for the configurer when it is used.
     */
    @Test
    void customizerWithTheFactoryInjected_serviceStarts() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).withUserConfiguration(CustomizerUsingTheFactoryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();

            RestClient.Builder unobserved = context.getBean(UnobservedRestClientBuilderFactory.class).create();

            assertThat(sendsTheModelVersionHeader(unobserved)).isFalse();
            assertThat(sendsTheModelVersionHeader(context.getBean(RestClient.Builder.class))).isTrue();
        });
    }

    @Test
    void withoutTheBuilderConfigurerOfSpringBoot_thereIsNoFactory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(UnobservedRestClientBuilderAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(UnobservedRestClientBuilderFactory.class));
    }

    /**
     * The HTTP client dependencies of the module are optional.
     */
    @Test
    void noRestClientSupportOfSpringBootOnTheClasspath_nothingIsConfigured() {
        contextRunner.withClassLoader(new FilteredClassLoader(RestClientBuilderConfigurer.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(UnobservedRestClientBuilderFactory.class));
    }

    private static boolean sendsTheModelVersionHeader(RestClient.Builder builder) {
        AtomicBoolean headerSent = new AtomicBoolean();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(URL))
                .andExpect(request -> headerSent.set(request.getHeaders().getFirst(MODEL_VERSION_HEADER) != null))
                .andRespond(withSuccess());

        builder.build().get().uri(URL).retrieve().toBodilessEntity();

        server.verify();
        return headerSent.get();
    }

    private static boolean isBodyReadBeforeTheRequestIsCreated(RestClient.Builder builder) {
        AtomicBoolean bodyRead = new AtomicBoolean();
        AtomicBoolean bodyReadBeforeTheRequestIsCreated = new AtomicBoolean();
        InputStream body = new FilterInputStream(new ByteArrayInputStream("large upload".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                bodyRead.set(true);
                return super.read(buffer, offset, length);
            }
        };
        ClientHttpRequestFactory requestFactory = (uri, method) -> {
            bodyReadBeforeTheRequestIsCreated.set(bodyRead.get());
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            request.setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.OK));
            return request;
        };

        builder.requestFactory(requestFactory).build()
                .post().uri(URL).body(new InputStreamResource(body)).retrieve().toBodilessEntity();

        assertThat(bodyRead).isTrue();
        return bodyReadBeforeTheRequestIsCreated.get();
    }

    interface PartnerClient {

        @GetExchange("/api/partners/42")
        void partner();
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        static final String OTHER_HEADER = "OTHER-CUSTOMIZER";

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }

        @Bean
        RestClientCustomizer otherCustomizer() {
            return restClientBuilder -> restClientBuilder.defaultHeader(OTHER_HEADER, "set");
        }

        @Bean
        NestedBuilderCreation nestedBuilderCreation() {
            return new NestedBuilderCreation();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerUsingTheFactoryConfiguration {

        @Bean
        RestClientCustomizer customizerUsingTheFactory(UnobservedRestClientBuilderFactory unobservedRestClientBuilderFactory) {
            return restClientBuilder -> assertThat(unobservedRestClientBuilderFactory).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnBuilderConfiguration {

        @Bean
        RestClient.Builder ownRestClientBuilder(RestClientBuilderConfigurer restClientBuilderConfigurer) {
            return restClientBuilderConfigurer.configure(RestClient.builder());
        }
    }

    /**
     * A customizer that creates a further builder while a builder is configured, once and on request. It is
     * applied before the customizer of the observation.
     */
    static class NestedBuilderCreation implements RestClientCustomizer, Ordered {

        private Supplier<RestClient.Builder> creation;
        private RestClient.Builder created;

        void whileTheNextBuilderIsConfigured(Supplier<RestClient.Builder> creation) {
            this.creation = creation;
        }

        RestClient.Builder created() {
            return created;
        }

        @Override
        public void customize(RestClient.Builder restClientBuilder) {
            Supplier<RestClient.Builder> requested = creation;
            creation = null;
            if (requested != null) {
                created = requested.get();
            }
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}
