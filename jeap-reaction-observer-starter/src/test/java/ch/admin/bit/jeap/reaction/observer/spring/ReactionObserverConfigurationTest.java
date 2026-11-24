package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import ch.admin.bit.jeap.reaction.observer.http.ReactionObserverHttpAutoConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class ReactionObserverConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                    ReactionObserverHttpAutoConfiguration.class, ReactionObserverAutoConfiguration.class))
            .withUserConfiguration(ListenerConfiguration.class);

    @Test
    void defaults() {
        contextRunner.run(context -> {
            ReactionObserverProperties properties = ReactionObserverProperties.of(context.getEnvironment());
            assertThat(properties.getModelVersion()).isEqualTo(1);
            assertThat(context).doesNotHaveBean(ClientHttpRequestInterceptor.class);
            assertThat(context)
                    .describedAs("The settings are read from the environment, a bean would only show values that have no effect")
                    .doesNotHaveBean(ReactionObserverProperties.class);
        });
    }

    @Test
    void modelVersion2InYaml_httpRequestsAreObserved() {
        contextRunner.withInitializer(context -> yaml("model-version: 2").forEach(context.getEnvironment().getPropertySources()::addFirst))
                .run(context -> {
                    assertThat(ReactionObserverProperties.of(context.getEnvironment()).getModelVersion()).isEqualTo(2);
                    assertThat(context).hasSingleBean(ClientHttpRequestInterceptor.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "true", "v2"})
    void modelVersionWithoutAModel_failsTheStart(String value) {
        contextRunner.withInitializer(context -> yaml("model-version: " + value).forEach(context.getEnvironment().getPropertySources()::addFirst))
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("jeap.reaction.observer.model-version"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "-1h", "daily"})
    void reidentificationPeriodThatIsNoPeriod_failsTheStart(String value) {
        contextRunner.withPropertyValues("jeap.reaction.observer.reidentification-period=" + value)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("jeap.reaction.observer.reidentification-period"));
    }

    @Test
    void reidentificationPeriodOfTheMinimum_starts() {
        contextRunner.withPropertyValues("jeap.reaction.observer.reidentification-period=2s")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ReactionObserverService.class));
    }

    @Test
    void disabled_reidentificationPeriodIsNotChecked() {
        contextRunner.withPropertyValues("jeap.reaction.observer.enabled=false", "jeap.reaction.observer.reidentification-period=0")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ReactionObserverService.class));
    }

    /**
     * All metrics are visible from the start with the value 0, so that an alert on an increase also sees the
     * first one.
     */
    @Test
    void metrics_withMeterRegistry_arePublishedFromTheStart() {
        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
                    assertThat(meterRegistry.get("jeap.reaction.observer.identified.reactions").gauge().value()).isZero();
                    assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions").counters())
                            .extracting(counter -> counter.getId().getTag("reason"), Counter::count)
                            .containsExactlyInAnyOrder(tuple("reaction_limit", 0.0), tuple("not_identified", 0.0),
                                    tuple("count_limit", 0.0));
                });
    }

    @Test
    void metrics_showIdentifiedReactionsAndDroppedExecutions() {
        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    ReactionObserverService reactionObserverService = context.getBean(ReactionObserverService.class);
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                    for (int i = 0; i <= ReactionObserverService.MAX_REACTION_COUNT; i++) {
                        reactionObserverService.reactionObserved(new Reaction(Observation.ofEvent("Event" + i, "topic"), null));
                    }

                    assertThat(meterRegistry.get("jeap.reaction.observer.identified.reactions").gauge().value())
                            .isEqualTo(ReactionObserverService.MAX_REACTION_COUNT);
                    assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions")
                            .tag("reason", "reaction_limit").counter().count()).isEqualTo(1);
                    assertThat(meterRegistry.get("jeap.reaction.observer.dropped.executions")
                            .tag("reason", "not_identified").counter().count()).isZero();
                });
    }

    /**
     * A metrics bean of the service replaces the one of the library, it does not fail the start as a second one.
     */
    @Test
    void metrics_beanOfTheService_replacesTheMetricsOfTheLibrary() {
        ReactionObserverMetrics metricsOfTheService = mock(ReactionObserverMetrics.class);
        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(ReactionObserverMetrics.class, () -> metricsOfTheService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ReactionObserverMetrics.class)).isSameAs(metricsOfTheService);
                    assertThat(context.getBean(MeterRegistry.class).find("jeap.reaction.observer.dropped.executions").counters())
                            .isEmpty();
                });
    }

    @Test
    void metrics_withoutMeterRegistry_nothingIsPublished() {
        contextRunner.run(context -> assertThat(context.getBean(ReactionObserverMetrics.class)).isSameAs(ReactionObserverMetrics.NONE));
    }

    @Test
    void metrics_withoutMicrometerOnTheClasspath_nothingIsPublished() {
        contextRunner.withClassLoader(new FilteredClassLoader(MeterRegistry.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(ReactionObserverService.class)
                        .doesNotHaveBean(ReactionObserverMetrics.class));
    }

    /**
     * The library has one source of the model, the property. It neither provides nor uses a bean an
     * application could replace.
     */
    @Test
    void libraryProvidesNoReactionModelBean() {
        contextRunner.withPropertyValues("jeap.reaction.observer.model-version=2")
                .run(context -> assertThat(context).doesNotHaveBean(ReactionModel.class));
    }

    @Test
    void disabled_nothingIsConfigured() {
        contextRunner.withPropertyValues("jeap.reaction.observer.enabled=false", "jeap.reaction.observer.model-version=2")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReactionObserverProperties.class);
                    assertThat(context).doesNotHaveBean(ClientHttpRequestInterceptor.class);
                });
    }

    /**
     * A Reaction Observer that is switched off observes by no model, so a model version that names no model
     * does not keep the service from starting.
     */
    @Test
    void disabled_modelVersionIsNotChecked() {
        contextRunner.withPropertyValues("jeap.reaction.observer.enabled=false", "jeap.reaction.observer.model-version=3")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ClientHttpRequestInterceptor.class));
    }

    /**
     * Every value of the switch but true switches the Reaction Observer off, also one that is no boolean.
     */
    @ParameterizedTest
    @ValueSource(strings = {"maybe", "on", "1"})
    void switchOtherThanTrue_nothingIsConfigured(String value) {
        contextRunner.withPropertyValues("jeap.reaction.observer.enabled=" + value, "jeap.reaction.observer.model-version=2")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(ClientHttpRequestInterceptor.class)
                        .doesNotHaveBean(ReactionRecorder.class));
    }

    private static List<PropertySource<?>> yaml(String line) {
        String yaml = "jeap:\n  reaction:\n    observer:\n      " + line + "\n";
        try {
            return new YamlPropertySourceLoader()
                    .load("test", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ListenerConfiguration {

        @Bean
        ReactionIdentifiedListener reactionIdentifiedListener() {
            return mock(ReactionIdentifiedListener.class);
        }
    }
}
