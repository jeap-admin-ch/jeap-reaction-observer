package ch.admin.bit.jeap.reaction.observer.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalOnReactionObserverEnabledTest {

    private static final String SWITCH = "jeap.reaction.observer.enabled=";
    private static final String ON_TYPE = "onType";
    private static final String ON_METHOD = "onMethod";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConditionOnTypeConfiguration.class, ConditionOnMethodConfiguration.class);

    @Test
    void notConfigured_matches() {
        contextRunner.run(context -> assertThat(context).hasBean(ON_TYPE).hasBean(ON_METHOD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void switchedOn_matches(String value) {
        contextRunner.withPropertyValues(SWITCH + value)
                .run(context -> assertThat(context).hasBean(ON_TYPE).hasBean(ON_METHOD));
    }

    /**
     * Every value but true switches the Reaction Observer off, as before the switch moved into this condition.
     */
    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "off", "no", "0", "on", "yes", "1", "maybe", ""})
    void switchedOff_doesNotMatch(String value) {
        contextRunner.withPropertyValues(SWITCH + value)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ON_TYPE).doesNotHaveBean(ON_METHOD));
    }

    /**
     * A switched-off Reaction Observer does not read its other settings: an invalid model version does not fail
     * the start then.
     */
    @Test
    void switchedOff_otherSettingsAreNotRead() {
        contextRunner.withPropertyValues(SWITCH + "false", "jeap.reaction.observer.model-version=v2")
                .withUserConfiguration(ReadsModelVersionConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ON_TYPE));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnReactionObserverEnabled
    static class ConditionOnTypeConfiguration {

        @Bean
        String onType() {
            return ON_TYPE;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnReactionObserverEnabled
    @Conditional(ReadsModelVersionCondition.class)
    static class ReadsModelVersionConfiguration {
    }

    static class ReadsModelVersionCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ReactionObserverProperties.of(context.getEnvironment()).reactionModel() != null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConditionOnMethodConfiguration {

        @Bean
        @ConditionalOnReactionObserverEnabled
        String onMethod() {
            return ON_METHOD;
        }
    }
}
