package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.configuration.ConditionalOnReactionObserverEnabled;
import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.web.client.RestClient;

/**
 * Observes the outgoing HTTP requests of all {@link RestClient}s built with the {@link RestClient.Builder}
 * provided by Spring Boot as actions. Only active if the service is configured with a reaction model that
 * observes HTTP requests ({@code jeap.reaction.observer.model-version}), and like the rest of the library not at
 * all if {@code jeap.reaction.observer.enabled} is false. See docs/http-requests.md.
 */
@AutoConfiguration
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class})
@ConditionalOnReactionObserverEnabled
@Conditional(OnHttpRequestObservationCondition.class)
@EnableConfigurationProperties(ReactionObserverHttpProperties.class)
public class ReactionObserverHttpAutoConfiguration {

    /**
     * The recorder is looked up when the first request is observed, not when the bean is created: the recorder
     * depends on the infrastructure that reports reactions, which may itself need an HTTP client.
     */
    @Bean
    ReactionObservingClientHttpRequestInterceptor jeapReactionObservingClientHttpRequestInterceptor(
            ObjectProvider<ReactionRecorder> reactionRecorder, ReactionObserverHttpProperties properties,
            ObjectProvider<HttpObservationMetrics> metrics, Environment environment) {
        HttpActionObserver httpActionObserver = new HttpActionObserver(
                new LazyReactionRecorder(reactionRecorder),
                new HttpTargetNaming(properties.getTargetHost().isIncludePort()),
                new HttpTargetRegistry(properties.getMaxTargetCount()),
                metrics.getIfAvailable(() -> HttpObservationMetrics.NONE),
                properties.getLogInterval());
        return new ReactionObservingClientHttpRequestInterceptor(httpActionObserver,
                ReactionObserverProperties.of(environment).reactionModel(), properties.getLogInterval());
    }

    /**
     * The observation has to enclose all other interceptors of a client: what counts is the outcome of a call
     * as the client returns it to the service, for example after a retry by an interceptor of the client. The
     * customizer is therefore applied after the others and puts the interceptor first, in front of the
     * interceptors that other customizers or a jEAP starter added before - also with {@code addFirst}.
     * Interceptors that the service adds to the builder later on are appended and run inside of the
     * observation as well.
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    RestClientCustomizer jeapReactionObserverRestClientCustomizer(ReactionObservingClientHttpRequestInterceptor interceptor) {
        return restClientBuilder -> restClientBuilder.requestInterceptors(interceptors -> interceptors.addFirst(interceptor));
    }

    /**
     * The metrics are published if the service has a meter registry. The registry is looked up when the bean is
     * created instead of with a bean condition, which would depend on the order of the auto-configurations.
     * Without Micrometer there is no such bean, and the interceptor counts nothing.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MicrometerMetricsConfiguration {

        @Bean
        HttpObservationMetrics jeapReactionObserverMicrometerHttpObservationMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
            MeterRegistry registry = meterRegistry.getIfUnique();
            return registry == null ? HttpObservationMetrics.NONE : new MicrometerHttpObservationMetrics(registry);
        }
    }
}
