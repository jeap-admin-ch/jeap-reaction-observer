package ch.admin.bit.jeap.reaction.observer.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches if the Reaction Observer is active: unless jeap.reaction.observer.enabled is set to a value other than
 * {@code true} (case-insensitive). Every part of the library that is only to exist in an active Reaction Observer
 * carries this condition, so that the switch is read in one place.
 * <p>
 * As a property condition it is evaluated before the other conditions of the Reaction Observer, which read
 * further settings: a switched-off Reaction Observer does not read them.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = ReactionObserverProperties.ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnReactionObserverEnabled {
}
