package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import ch.admin.bit.jeap.reaction.observer.http.ReactionObserverHttpAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
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
