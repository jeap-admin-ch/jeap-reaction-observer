package ch.admin.bit.jeap.reaction.observer.events.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionObserverKafkaConfigPropertiesTest {

    @Test
    void validate_noTopics_throws() {
        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jeap.reaction.observer.events.reaction-identified-topic must not be empty");
    }

    @Test
    void validate_noReactionsObservedTopic_throws() {
        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();
        properties.setReactionIdentifiedTopic("reaction-identified");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jeap.reaction.observer.events.reactions-observed-topic must not be empty");
    }

    @Test
    void validate_bothTopics_passes() {
        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();
        properties.setReactionIdentifiedTopic("reaction-identified");
        properties.setReactionsObservedTopic("reactions-observed");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    /**
     * The publication of the counts cannot be switched off. An interval of less than a second used to fail the
     * start of the service as well, but in the scheduler and without the name of the property.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void validate_intervalOfLessThanASecond_throws(int observedEventRateSeconds) {
        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();
        properties.setReactionIdentifiedTopic("reaction-identified");
        properties.setReactionsObservedTopic("reactions-observed");
        properties.setObservedEventRateSeconds(observedEventRateSeconds);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("jeap.reaction.observer.events.observed-event-rate-seconds must be at least 1, but is " +
                        observedEventRateSeconds);
    }

    /**
     * A service that switches the Reaction Observer off does not have to configure the topics: the properties
     * do not exist then, so nothing validates them.
     */
    @Test
    void observerSwitchedOff_noTopicsNeeded() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactionObserverEventAutoConfiguration.class))
                .withPropertyValues("jeap.reaction.observer.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(ReactionObserverKafkaConfigProperties.class));
    }
}
