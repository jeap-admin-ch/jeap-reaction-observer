package ch.admin.bit.jeap.reaction.observer.http;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The one place that knows how the {@link UnobservedRestClientBuilderFactory} keeps the observation of this
 * library off a builder. The rest of the module only uses {@link #unlessSuppressed} and
 * {@link #configureSuppressed}.
 * <p>
 * It is a workaround, for two reasons:
 * <ul>
 * <li>Spring Boot applies all customizers to a builder it configures. A customizer only gets the builder and
 * cannot tell whom it is for.</li>
 * <li>Taking the interceptor off a builder afterwards is no way out: Spring buffers the request body as soon
 * as a builder has a list of interceptors, also an empty one, and a builder cannot drop its list again.</li>
 * </ul>
 * So the customizer has to leave such a builder alone from the start, and it has to be told so from the
 * outside: while a builder is configured without the observation, it is remembered here, and the customizer
 * skips exactly this instance. Every other builder is observed as usual, also one that is configured at the
 * same time, by another thread or by a customizer applied to the remembered one.
 */
final class RestClientObservationSuppression {

    private static final Set<RestClient.Builder> SUPPRESSED_BUILDERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private RestClientObservationSuppression() {
    }

    /**
     * @return a customizer that applies the given one, except to a builder while it is configured by
     * {@link #configureSuppressed}
     */
    static RestClientCustomizer unlessSuppressed(RestClientCustomizer customizer) {
        return restClientBuilder -> {
            if (!SUPPRESSED_BUILDERS.contains(restClientBuilder)) {
                customizer.customize(restClientBuilder);
            }
        };
    }

    /**
     * Configures the builder so that the customizers wrapped with {@link #unlessSuppressed} leave it alone.
     *
     * @param configuration applies the customizers to the builder it is given
     * @return what the configuration returns
     */
    static RestClient.Builder configureSuppressed(RestClient.Builder restClientBuilder,
                                                  UnaryOperator<RestClient.Builder> configuration) {
        SUPPRESSED_BUILDERS.add(restClientBuilder);
        try {
            return configuration.apply(restClientBuilder);
        } finally {
            SUPPRESSED_BUILDERS.remove(restClientBuilder);
        }
    }
}
