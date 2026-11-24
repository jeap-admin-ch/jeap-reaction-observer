package ch.admin.bit.jeap.reaction.observer.configuration;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionObserverPropertiesTest {

    @Test
    void notConfigured_defaults() {
        ReactionObserverProperties properties = ReactionObserverProperties.of(new MockEnvironment());

        assertThat(properties.getModelVersion()).isEqualTo(1);
        assertThat(properties.reactionModel()).isEqualTo(ReactionModel.V1);
        assertThat(properties.validReidentificationPeriod()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void configured_isRead() {
        ReactionObserverProperties properties = ReactionObserverProperties.of(new MockEnvironment()
                .withProperty(ReactionObserverProperties.MODEL_VERSION_PROPERTY, "2"));

        assertThat(properties.getModelVersion()).isEqualTo(2);
        assertThat(properties.reactionModel()).isEqualTo(ReactionModel.V2);
    }

    /**
     * YAML gives an unquoted number as a number and a quoted one as text; both are the version.
     */
    @ParameterizedTest
    @CsvSource({"1,V1", "2,V2", "'\"2\"',V2", "' 2 ',V2"})
    void modelVersionInYaml_choosesTheModel(String yamlValue, ReactionModel expected) throws IOException {
        assertThat(ReactionObserverProperties.of(environmentOfYaml(yamlValue)).reactionModel()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "-1"})
    void modelVersionWithoutAModel_isRejected(String yamlValue) throws IOException {
        ReactionObserverProperties properties = ReactionObserverProperties.of(environmentOfYaml(yamlValue));

        assertThatThrownBy(properties::reactionModel)
                .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                .hasMessageContaining(ReactionObserverProperties.MODEL_VERSION_PROPERTY)
                .hasMessageContaining(yamlValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v2", "true", "two", "''"})
    void modelVersionThatIsNoNumber_isRejected(String yamlValue) throws IOException {
        StandardEnvironment environment = environmentOfYaml(yamlValue);

        assertThatThrownBy(() -> ReactionObserverProperties.of(environment))
                .isInstanceOf(BindException.class)
                .hasMessageContaining(ReactionObserverProperties.MODEL_VERSION_PROPERTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JEAP_REACTION_OBSERVER_MODEL_VERSION", "JEAP_REACTION_OBSERVER_MODELVERSION"})
    void modelVersionAsEnvironmentVariable_choosesTheModel(String variable) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(variable, "2")));

        assertThat(ReactionObserverProperties.of(environment).reactionModel()).isEqualTo(ReactionModel.V2);
    }

    /**
     * A number without a unit is in seconds.
     */
    @ParameterizedTest
    @CsvSource({"15s,PT15S", "2,PT2S", "2000ms,PT2S", "PT36H,PT36H", "7d,PT168H"})
    void reidentificationPeriod_isRead(String value, Duration expected) {
        ReactionObserverProperties properties = ReactionObserverProperties.of(new MockEnvironment()
                .withProperty(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY, value));

        assertThat(properties.validReidentificationPeriod()).isEqualTo(expected);
    }

    /**
     * The identification start is reported to the second: with a shorter period, two periods of an instance
     * could start within the same second. The reidentification cannot be switched off either.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1999ms", "1", "0", "-1h"})
    void reidentificationPeriodTooShort_isRejected(String value) {
        ReactionObserverProperties properties = ReactionObserverProperties.of(new MockEnvironment()
                .withProperty(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY, value));

        assertThatThrownBy(properties::validReidentificationPeriod)
                .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                .hasMessageContaining(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY)
                .hasMessageContaining("at least 2 seconds");
    }

    /**
     * The reidentification cannot be switched off, not by a period that practically never ends either.
     */
    @ParameterizedTest
    @ValueSource(strings = {"604801", "PT168H1S", "8d", "36500d"})
    void reidentificationPeriodTooLong_isRejected(String value) {
        ReactionObserverProperties properties = ReactionObserverProperties.of(new MockEnvironment()
                .withProperty(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY, value));

        assertThatThrownBy(properties::validReidentificationPeriod)
                .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                .hasMessageContaining(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY)
                .hasMessageContaining("at most 7 days");
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "true", "1 day"})
    void reidentificationPeriodThatIsNoDuration_isRejected(String value) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY, value);

        assertThatThrownBy(() -> ReactionObserverProperties.of(environment))
                .isInstanceOf(BindException.class)
                .hasMessageContaining(ReactionObserverProperties.REIDENTIFICATION_PERIOD_PROPERTY);
    }

    private static StandardEnvironment environmentOfYaml(String yamlValue) throws IOException {
        String yaml = "jeap:\n  reaction:\n    observer:\n      model-version: " + yamlValue + "\n";
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("test", new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)))
                .forEach(environment.getPropertySources()::addFirst);
        return environment;
    }
}
