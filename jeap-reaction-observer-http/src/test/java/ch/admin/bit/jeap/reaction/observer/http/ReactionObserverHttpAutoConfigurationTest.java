package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReactionObserverHttpAutoConfigurationTest {

    private static final String MODEL_VERSION_2 = "jeap.reaction.observer.model-version=2";
    private static final String URL = "https://partner.example.org/api/partners/42";
    private static final String MAX_TARGET_COUNT = "jeap.reaction.observer.http.max-target-count=";
    private static final String LOG_INTERVAL = "jeap.reaction.observer.http.log-interval=";
    private static final String TARGET_LIMIT_LOG_PREFIX = "for the maximum of";
    private static final String TARGET_LIMIT_LOG = TARGET_LIMIT_LOG_PREFIX + " 1 targets";
    private static final String FAILED_CALL_LOG = "It is not observed as an action";
    private static final String OBSERVATION_FAILURE_LOG = "Failed to observe an outgoing HTTP request";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, ReactionObserverHttpAutoConfiguration.class))
            .withUserConfiguration(RecorderConfiguration.class);

    @Test
    void default_modelVersion2IsOff_nothingIsObserved() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ReactionObservingClientHttpRequestInterceptor.class);
            assertThat(context).doesNotHaveBean(RestClientCustomizer.class);
            assertThat(context).doesNotHaveBean(HttpObservationMetrics.class);

            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(headerDoesNotExist("JEAP-REACTION-MODEL-VERSION"))
                    .andRespond(withSuccess());

            builder.build().get().uri(URL).retrieve().toBodilessEntity();

            server.verify();
            assertThat(context.getBean(ObservedReactions.class).identified()).isEmpty();
        });
    }

    @Test
    void modelVersion1_nothingIsObserved() {
        contextRunner.withPropertyValues("jeap.reaction.observer.model-version=1")
                .run(context -> assertThat(context).doesNotHaveBean(ReactionObservingClientHttpRequestInterceptor.class));
    }

    @Test
    void modelVersion2_httpRequestsAreObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .run(context -> assertThat(context).hasSingleBean(ReactionObservingClientHttpRequestInterceptor.class));
    }

    /**
     * YAML gives an unquoted number as a number, not as text.
     */
    @Test
    void modelVersion2AsNumber_httpRequestsAreObserved() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("yaml", Map.of("jeap.reaction.observer.model-version", 2))))
                .run(context -> assertThat(context).hasSingleBean(ReactionObservingClientHttpRequestInterceptor.class));
    }

    /**
     * A model version without a model is a configuration error and must not silently mean "the default".
     */
    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "v2", "true"})
    void unknownModelVersion_failsTheStart(String value) {
        contextRunner.withPropertyValues("jeap.reaction.observer.model-version=" + value)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("jeap.reaction.observer.model-version"));
    }

    @Test
    void request_carriesTheVersionOfTheConfiguredModel() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header("JEAP-REACTION-MODEL-VERSION", String.valueOf(ReactionModel.V2.version())))
                    .andRespond(withSuccess());

            builder.build().get().uri(URL).retrieve().toBodilessEntity();

            server.verify();
        });
    }

    @Test
    void modelVersion2Enabled_restClientsOfTheSpringBootBuilderAreObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            assertThat(context).hasSingleBean(ReactionObservingClientHttpRequestInterceptor.class);

            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header("JEAP-REACTION-MODEL-VERSION", "2"))
                    .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", "%\"partner-service GET /api/partners/{}\""));

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> builder.build().get().uri(URL).retrieve().toBodilessEntity());

            server.verify();
            assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("partner-service GET /api/partners/{}");
        });
    }

    @Test
    void modelVersion2Enabled_clonedBuilderIsObservedToo() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            // The OAuth2 clients of jEAP and the HTTP interface clients are built on a clone of the builder
            RestClient.Builder builder = context.getBean(RestClient.Builder.class).clone().baseUrl("https://partner.example.org");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(URL))
                    .andExpect(header("JEAP-REACTION-MODEL-VERSION", "2"))
                    .andRespond(withSuccess());

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> builder.build().get().uri("/api/partners/42").retrieve().toBodilessEntity());

            server.verify();
            assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//partner.example.org GET /{*}");
        });
    }

    /**
     * The observation has to enclose the interceptors of the client, however a service registers them: what
     * counts is the outcome of the call as the client returns it to the service. A retry interceptor that a
     * service registers with a RestClientCustomizer of its own must not run outside of the observation -
     * the execution would be discarded although the service got a successful call.
     */
    @Test
    void interceptorsOfOtherCustomizers_runInsideOfTheObservation() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withUserConfiguration(RetryCustomizerConfiguration.class)
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
                    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
                    server.expect(once(), requestTo(URL))
                            .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", "%\"partner-service GET /api/partners/{}\""));

                    ObservedReactions reactions = context.getBean(ObservedReactions.class);
                    reactions.inTrigger(() -> builder.build().get().uri(URL).retrieve().toBodilessEntity());

                    server.verify();
                    assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("partner-service GET /api/partners/{}");
                });
    }

    /**
     * The observation's customizer is applied after the others: an interceptor that another customizer puts in
     * front of all others with addFirst still runs inside of the observation.
     */
    @Test
    void interceptorPutFirstByAnotherCustomizer_runsInsideOfTheObservation() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withUserConfiguration(AddFirstRetryCustomizerConfiguration.class)
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
                    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
                    server.expect(once(), requestTo(URL))
                            .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", "%\"partner-service GET /api/partners/{}\""));

                    ObservedReactions reactions = context.getBean(ObservedReactions.class);
                    reactions.inTrigger(() -> builder.build().get().uri(URL).retrieve().toBodilessEntity());

                    server.verify();
                    assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("partner-service GET /api/partners/{}");
                });
    }

    /**
     * An interceptor that fails before the request is sent, for example an open circuit breaker: the call ends
     * without a response, the observation sees it also if another customizer registered the interceptor.
     */
    @Test
    void failingInterceptorOfAnotherCustomizer_isAFailedCall() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withUserConfiguration(FailingCustomizerConfiguration.class)
                .run(context -> {
                    RestClient restClient = context.getBean(RestClient.Builder.class).build();

                    ObservedReactions reactions = context.getBean(ObservedReactions.class);
                    reactions.inTrigger(() -> assertThatThrownBy(() -> restClient.get().uri(URL).retrieve().toBodilessEntity())
                            .isInstanceOf(IllegalStateException.class));

                    assertThat(reactions.identified()).isEmpty();
                });
    }

    @Test
    void globalSwitchOff_nothingIsObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.reaction.observer.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReactionObservingClientHttpRequestInterceptor.class);
                    assertThat(context).doesNotHaveBean(RestClientCustomizer.class);
                });
    }

    @Test
    void noRestClientOnTheClasspath_nothingIsConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactionObserverHttpAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(RestClient.class))
                .withPropertyValues(MODEL_VERSION_2)
                .run(context -> assertThat(context).doesNotHaveBean(ReactionObservingClientHttpRequestInterceptor.class));
    }

    @Test
    void portInTheTargetOfCoarseActions_isOffByDefault() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context ->
                assertThat(context.getBean(ReactionObserverHttpProperties.class).getTargetHost().isIncludePort()).isFalse());
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.reaction.observer.http.target-host.include-port=true").run(context -> {
            assertThat(context.getBean(ReactionObserverHttpProperties.class).getTargetHost().isIncludePort()).isTrue();

            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo("http://localhost:8095/svc/api/x")).andRespond(withSuccess());
            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> builder.build().get().uri("http://localhost:8095/svc/api/x").retrieve().toBodilessEntity());

            assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//localhost:8095/svc GET /{*}");
        });
    }

    @Test
    void targetLimit_is100ByDefault() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context ->
                assertThat(context.getBean(ReactionObserverHttpProperties.class).getMaxTargetCount()).isEqualTo(100));
    }

    @Test
    void targetLimit_configured_limitsTheTargetsOfCoarseActions() {
        contextRunner.withPropertyValues(MODEL_VERSION_2, MAX_TARGET_COUNT + 1).run(context -> {
            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo("https://first.example.org/api/x")).andRespond(withSuccess());
            server.expect(once(), requestTo("https://second.example.org/api/x")).andRespond(withSuccess());
            RestClient restClient = builder.build();

            ObservedReactions reactions = context.getBean(ObservedReactions.class);
            reactions.inTrigger(() -> {
                restClient.get().uri("https://first.example.org/api/x").retrieve().toBodilessEntity();
                restClient.get().uri("https://second.example.org/api/x").retrieve().toBodilessEntity();
            });

            assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//first.example.org GET /{*}");
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void targetLimit_zero_noCoarseActionsButFineActions_andNoWarning(CapturedOutput output) {
        contextRunner.withPropertyValues(MODEL_VERSION_2, MAX_TARGET_COUNT + 0)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
                    server.expect(once(), requestTo("https://first.example.org/api/x")).andRespond(withSuccess());
                    server.expect(once(), requestTo(URL))
                            .andRespond(withSuccess().header("JEAP-REACTION-OBSERVATION", "%\"partner-service GET /api/partners/{}\""));
                    RestClient restClient = builder.build();

                    ObservedReactions reactions = context.getBean(ObservedReactions.class);
                    reactions.inTrigger(() -> {
                        restClient.get().uri("https://first.example.org/api/x").retrieve().toBodilessEntity();
                        restClient.get().uri(URL).retrieve().toBodilessEntity();
                    });

                    assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("partner-service GET /api/partners/{}");
                    assertThat(context.getBean(MeterRegistry.class).get("jeap.reaction.observer.dropped.http.calls")
                            .tag("reason", "target_limit").counter().count())
                            .describedAs("The request without header reaches the limit and counts in the loss metric")
                            .isEqualTo(1);
                });
        assertThat(output.getOut())
                .describedAs("0 switches coarse actions off on purpose")
                .doesNotContain(TARGET_LIMIT_LOG_PREFIX);
    }

    @Test
    void targetLimit_negative_failsTheStart() {
        contextRunner.withPropertyValues(MODEL_VERSION_2, MAX_TARGET_COUNT + -1)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("The maximum number of targets must not be negative: -1"));
    }

    @Test
    void logInterval_isOneMinuteByDefault() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context ->
                assertThat(context.getBean(ReactionObserverHttpProperties.class).getLogInterval()).isEqualTo(Duration.ofMinutes(1)));
    }

    @ParameterizedTest
    @CsvSource({"30, PT30S", "0, PT0S", "2m, PT2M", "500ms, PT0.5S", "PT5S, PT5S"})
    void logInterval_configured_aNumberWithoutAUnitIsInSeconds(String value, Duration interval) {
        contextRunner.withPropertyValues(MODEL_VERSION_2, LOG_INTERVAL + value).run(context ->
                assertThat(context.getBean(ReactionObserverHttpProperties.class).getLogInterval()).isEqualTo(interval));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logInterval_default_everyRecurringLogEntryIsWrittenOncePerInterval(CapturedOutput output) {
        causeEveryRecurringLogEntryThreeTimes();

        assertThat(output.getOut())
                .containsOnlyOnce(TARGET_LIMIT_LOG)
                .containsOnlyOnce(FAILED_CALL_LOG)
                .containsOnlyOnce(OBSERVATION_FAILURE_LOG);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logInterval_zero_everyOccurrenceIsLogged(CapturedOutput output) {
        causeEveryRecurringLogEntryThreeTimes(LOG_INTERVAL + 0);

        assertThat(output.getOut().split(TARGET_LIMIT_LOG, -1)).hasSize(4);
        assertThat(output.getOut().split(FAILED_CALL_LOG, -1)).hasSize(4);
        assertThat(output.getOut().split(OBSERVATION_FAILURE_LOG, -1)).hasSize(4);
    }

    /**
     * Three requests to a target over the limit, three failed calls and three failures of the observation
     * itself (there is no recorder).
     */
    private void causeEveryRecurringLogEntryThreeTimes(String... properties) {
        contextRunner.withPropertyValues(MODEL_VERSION_2, MAX_TARGET_COUNT + 1).withPropertyValues(properties).run(context -> {
            requestTheOnlyTarget(context);
            requestThreeTimes(context, withSuccess());
            requestThreeTimes(context, withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        });
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, ReactionObserverHttpAutoConfiguration.class))
                .withPropertyValues(MODEL_VERSION_2).withPropertyValues(properties)
                .run(context -> requestThreeTimes(context, withSuccess()));
    }

    private static void requestTheOnlyTarget(ApplicationContext context) {
        RestClient.Builder builder = context.getBean(RestClient.Builder.class);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://first.example.org/api/x")).andRespond(withSuccess());
        builder.build().get().uri("https://first.example.org/api/x").retrieve().toBodilessEntity();
    }

    private static void requestThreeTimes(ApplicationContext context, ResponseCreator response) {
        RestClient.Builder builder = context.getBean(RestClient.Builder.class);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(3), requestTo(URL)).andRespond(response);
        RestClient restClient = builder.build();
        for (int i = 0; i < 3; i++) {
            restClient.get().uri(URL).exchange((request, clientResponse) -> clientResponse.getStatusCode());
        }
    }

    @Test
    void metrics_withMeterRegistry_arePublished() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context.getBean(HttpObservationMetrics.class)).isInstanceOf(MicrometerHttpObservationMetrics.class);
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
                    assertThat(meterRegistry.get("jeap.reaction.observer.unidentified.http.calls").counters()).hasSize(3);
                    assertThat(meterRegistry.get("jeap.reaction.observer.dropped.http.calls").counters()).hasSize(3);
                });
    }

    @Test
    void metrics_withoutMeterRegistry_nothingIsCounted() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .run(context -> assertThat(context.getBean(HttpObservationMetrics.class)).isSameAs(HttpObservationMetrics.NONE));
    }

    @Test
    void metrics_withoutMicrometerOnTheClasspath_nothingIsCounted() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReactionObservingClientHttpRequestInterceptor.class);
                    assertThat(context).doesNotHaveBean(HttpObservationMetrics.class);
                });
    }

    private static ClientHttpRequestInterceptor retryOnceOnServerError() {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            if (!response.getStatusCode().is5xxServerError()) {
                return response;
            }
            response.close();
            return execution.execute(request, body);
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class RetryCustomizerConfiguration {

        @Bean
        RestClientCustomizer retryOnceOnServerErrorCustomizer() {
            return builder -> builder.requestInterceptor(retryOnceOnServerError());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AddFirstRetryCustomizerConfiguration {

        @Bean
        @Order(0)
        RestClientCustomizer addFirstRetryOnceOnServerErrorCustomizer() {
            return builder -> builder.requestInterceptors(interceptors -> interceptors.addFirst(retryOnceOnServerError()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingCustomizerConfiguration {

        @Bean
        RestClientCustomizer openCircuitBreakerCustomizer() {
            return builder -> builder.requestInterceptor((request, body, execution) -> {
                throw new IllegalStateException("circuit breaker is open");
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RecorderConfiguration {

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
