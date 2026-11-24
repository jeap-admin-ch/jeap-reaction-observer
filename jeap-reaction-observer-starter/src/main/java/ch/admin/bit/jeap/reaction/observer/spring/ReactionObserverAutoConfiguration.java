package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.configuration.ConditionalOnReactionObserverEnabled;
import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnReactionObserverEnabled
public class ReactionObserverAutoConfiguration {

    @Bean
    ReactionObserverService reactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener,
                                                    Environment environment, ObjectProvider<ReactionObserverMetrics> metrics) {
        ReactionObserverMetrics reactionObserverMetrics = metrics.getIfAvailable(() -> ReactionObserverMetrics.NONE);
        ReactionObserverService reactionObserverService = new ReactionObserverService(reactionIdentifiedListener,
                ReactionObserverProperties.of(environment).validReidentificationPeriod(), Clock.systemUTC(),
                reactionObserverMetrics);
        reactionObserverMetrics.identifiedReactions(reactionObserverService::identifiedReactionCount);
        return reactionObserverService;
    }

    @Bean
    ReactionRecorder reactionRecorder(ReactionObserverService reactionObserverService) {
        return new ReactionRecorder(reactionObserverService);
    }

    /**
     * The metrics are published if the service has a meter registry. The registry is looked up when the bean is
     * created instead of with a bean condition, which would depend on the order of the auto-configurations.
     * Without Micrometer there is no such bean, and the service publishes no metrics. A service that has a
     * {@link ReactionObserverMetrics} bean of its own gets that one instead; the interface is an internal one
     * all the same and can change in any release.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ReactionObserverMetrics jeapReactionObserverMicrometerMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
            MeterRegistry registry = meterRegistry.getIfUnique();
            return registry == null ? ReactionObserverMetrics.NONE : new MicrometerReactionObserverMetrics(registry);
        }
    }
}
