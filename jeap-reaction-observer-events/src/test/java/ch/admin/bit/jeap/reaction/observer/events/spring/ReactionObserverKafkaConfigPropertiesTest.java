package ch.admin.bit.jeap.reaction.observer.events.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactionObserverKafkaConfigPropertiesTest {

    @Test
    void validateThrowsExceptionWhenObserverEnabledAndTopicsAreEmpty() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("jeap.reaction.observer.enabled", Boolean.class, true)).thenReturn(true);

        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();
        properties.setEnvironment(environment);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, properties::validate);
        assertEquals("jeap.reaction.observer.events.reaction-identified-topic must not be empty", exception.getMessage());
    }

    @Test
    void validateDoesNotThrowExceptionWhenObserverDisabled() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("jeap.reaction.observer.enabled", Boolean.class, true)).thenReturn(false);

        ReactionObserverKafkaConfigProperties properties = new ReactionObserverKafkaConfigProperties();
        properties.setEnvironment(environment);

        assertDoesNotThrow(properties::validate);
    }
}
