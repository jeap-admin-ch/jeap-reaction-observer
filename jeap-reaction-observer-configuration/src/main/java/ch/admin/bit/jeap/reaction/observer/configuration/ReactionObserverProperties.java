package ch.admin.bit.jeap.reaction.observer.configuration;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

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
    static final String REIDENTIFICATION_PERIOD_PROPERTY = PREFIX + ".reidentification-period";

    /**
     * The version of the reaction model the service observes by: 1 or 2. Version 1 observes the events and
     * commands a service sends. Version 2 adds its outgoing HTTP requests as actions and its incoming HTTP
     * requests as triggers, and requires a Reaction Observer Service that supports reaction model version 2. A
     * version that does not exist fails the start of the
     * service.
     */
    private int modelVersion = 1;

    /**
     * How long an instance remembers which reactions it has identified. After that it identifies each reaction
     * again when it occurs, which also starts the limit of 4096 identified reactions per instance anew. The first
     * period of an instance lasts a random 50 to 100 percent of this time. A number without a unit is in
     * seconds. A period of less than 2 seconds or of more than 7 days fails the start of the service; the
     * reidentification cannot be switched off. Periods of seconds or minutes only make sense in tests and
     * demos: in operation, choose a period of one or several days.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration reidentificationPeriod = ReactionObserverService.DEFAULT_REIDENTIFICATION_PERIOD;

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

    /**
     * @return the configured reidentification period
     * @throws InvalidConfigurationPropertyValueException if the period is not set, shorter than the minimum or
     *                                                    longer than the maximum; this fails the start of the
     *                                                    service
     */
    public Duration validReidentificationPeriod() {
        if (reidentificationPeriod == null ||
                reidentificationPeriod.compareTo(ReactionObserverService.MIN_REIDENTIFICATION_PERIOD) < 0 ||
                reidentificationPeriod.compareTo(ReactionObserverService.MAX_REIDENTIFICATION_PERIOD) > 0) {
            throw new InvalidConfigurationPropertyValueException(REIDENTIFICATION_PERIOD_PROPERTY, reidentificationPeriod,
                    "The reidentification period has to be at least " +
                            ReactionObserverService.MIN_REIDENTIFICATION_PERIOD.toSeconds() + " seconds and at most " +
                            ReactionObserverService.MAX_REIDENTIFICATION_PERIOD.toDays() + " days");
        }
        return reidentificationPeriod;
    }
}
