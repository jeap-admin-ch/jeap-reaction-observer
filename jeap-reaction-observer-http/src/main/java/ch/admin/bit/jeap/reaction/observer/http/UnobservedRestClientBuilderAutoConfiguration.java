package ch.admin.bit.jeap.reaction.observer.http;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Provides the {@link UnobservedRestClientBuilderFactory}. Unlike the observation itself, the factory does not
 * depend on the reaction model or on {@code jeap.reaction.observer.enabled}: a service that uses the factory has
 * to start with every configuration of the Reaction Observer. For the same reason the auto-configuration is
 * registered for the {@code @RestClientTest} slice as well.
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass({RestClient.class, RestClientBuilderConfigurer.class})
public class UnobservedRestClientBuilderAutoConfiguration {

    @Bean
    @ConditionalOnBean(RestClientBuilderConfigurer.class)
    UnobservedRestClientBuilderFactory jeapReactionObserverUnobservedRestClientBuilderFactory(
            ObjectProvider<RestClientBuilderConfigurer> restClientBuilderConfigurer) {
        return new UnobservedRestClientBuilderFactory(restClientBuilderConfigurer);
    }
}
