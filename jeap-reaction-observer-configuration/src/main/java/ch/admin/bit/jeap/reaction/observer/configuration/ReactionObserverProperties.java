package ch.admin.bit.jeap.reaction.observer.configuration;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.core.env.Environment;

/**
 * The settings of the Reaction Observer as a whole. Settings of a single part of the library are with that
 * part: jeap.reaction.observer.events.* and jeap.reaction.observer.http.*.
 * <p>
 * Every part of the library that depends on these settings reads them here, with {@link #of(Environment)}, so
 * that all parts decide alike. The switch jeap.reaction.observer.enabled is not one of these settings: a part
 * that is only to exist if the Reaction Observer is active carries {@link ConditionalOnReactionObserverEnabled}.
 */
@ConfigurationProperties(ReactionObserverProperties.PREFIX)
@Getter
@Setter
public class ReactionObserverProperties {

    static final String PREFIX = "jeap.reaction.observer";
    static final String ENABLED_PROPERTY = PREFIX + ".enabled";
    static final String MODEL_VERSION_PROPERTY = PREFIX + ".model-version";

    /**
     * The version of the reaction model the service observes by: 1 or 2. Version 1 observes the events and
     * commands a service sends. Version 2 adds its outgoing HTTP requests as actions and its incoming HTTP
     * requests as triggers, and requires a Reaction Observer Service that supports reaction model version 2. A
     * version that does not exist fails the start of the
     * service.
     */
    private int modelVersion = 1;

    /**
     * Reads the settings with the binder of Spring Boot. This also works where no bean exists yet, in a
     * condition.
     */
    public static ReactionObserverProperties of(Environment environment) {
        return Binder.get(environment).bindOrCreate(PREFIX, ReactionObserverProperties.class);
    }

    /**
     * @return the reaction model of the configured version
     * @throws InvalidConfigurationPropertyValueException if there is no reaction model of the configured
     *                                                    version; this fails the start of the service
     */
    public ReactionModel reactionModel() {
        return ReactionModel.ofVersion(modelVersion).orElseThrow(() -> new InvalidConfigurationPropertyValueException(
                MODEL_VERSION_PROPERTY, modelVersion, "There is no reaction model of this version"));
    }
}
