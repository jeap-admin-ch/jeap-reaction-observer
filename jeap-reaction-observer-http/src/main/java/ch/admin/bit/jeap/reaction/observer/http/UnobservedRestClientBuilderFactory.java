package ch.admin.bit.jeap.reaction.observer.http;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.web.client.RestClient;

/**
 * Creates {@link RestClient.Builder}s whose clients are not observed by the Reaction Observer: their requests
 * are no actions, and the observation does not make Spring buffer their request bodies in memory. Meant for
 * the few clients that have to stream large request bodies, see docs/http-requests.md.
 * <p>
 * A created builder is configured like the {@link RestClient.Builder} provided by Spring Boot, with all other
 * customizers applied to it. The factory is also available if the service does not observe HTTP requests at
 * all.
 */
public final class UnobservedRestClientBuilderFactory {

    private final ObjectProvider<RestClientBuilderConfigurer> restClientBuilderConfigurer;

    /**
     * @param restClientBuilderConfigurer looked up when a builder is created, not when the factory is: Spring
     *                                    Boot creates all customizers together with its configurer, and one
     *                                    of them may depend on this factory
     */
    UnobservedRestClientBuilderFactory(ObjectProvider<RestClientBuilderConfigurer> restClientBuilderConfigurer) {
        this.restClientBuilderConfigurer = restClientBuilderConfigurer;
    }

    /**
     * @return a new builder, configured by Spring Boot but without the observation of the Reaction Observer
     */
    public RestClient.Builder create() {
        // Spring Boot creates the builder it provides in the same way: a new builder, configured by its configurer
        return RestClientObservationSuppression.configureSuppressed(RestClient.builder(),
                restClientBuilderConfigurer.getObject()::configure);
    }
}
