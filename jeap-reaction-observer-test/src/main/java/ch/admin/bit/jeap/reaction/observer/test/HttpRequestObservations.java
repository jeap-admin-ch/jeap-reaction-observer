package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import org.springframework.test.web.client.response.DefaultResponseCreator;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Test support for HTTP requests observed as actions: names and ids of the observations, and the response
 * header a provider names the called operation with.
 * <p>
 * To stub a provider with a {@code MockRestServiceServer}:
 * <pre>{@code
 * server.expect(requestTo(url))
 *       .andRespond(withObservation(withSuccess(), fqn("partner-service", "GET", "/api/partners/{}")));
 * }</pre>
 */
public final class HttpRequestObservations {

    /**
     * The type of an HTTP request observation in the reaction events
     */
    public static final String TYPE = "http_request";
    /**
     * Request header a consumer asks the provider with to name the called operation
     */
    public static final String REACTION_MODEL_VERSION_HEADER = "JEAP-REACTION-MODEL-VERSION";
    /**
     * Response header a provider names the called operation with
     */
    public static final String REACTION_OBSERVATION_HEADER = "JEAP-REACTION-OBSERVATION";

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HttpRequestObservations() {
    }

    /**
     * @return the name of an operation as its provider states it, for example
     * "partner-service GET /api/partners/{}"
     */
    public static String fqn(String provider, String method, String pathPattern) {
        return provider + " " + method + " " + pathPattern;
    }

    /**
     * @param target the called target as "//&lt;host&gt;" or "//&lt;host&gt;/&lt;context&gt;", for example
     *               "//foo.bar.admin.ch/some-service"
     * @return the name of the coarse action for a request the provider did not name the operation of, for
     * example "//foo.bar.admin.ch/some-service POST /{*}"
     */
    public static String coarseFqn(String target, String method) {
        return target + " " + method.toUpperCase(Locale.ROOT) + " /{*}";
    }

    /**
     * @return the id of the observation of the HTTP request with the name
     */
    public static String observationId(String fqn) {
        return Observation.ofHttpRequest(fqn).id().value();
    }

    /**
     * @return the value of the {@value #REACTION_OBSERVATION_HEADER} response header naming the operation, as
     * the header module of the provider encodes it (without shortening an FQN of more than 512 characters)
     */
    public static String observationHeaderValue(String fqn) {
        StringBuilder value = new StringBuilder("%\"");
        for (byte b : fqn.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = b & 0xFF;
            if (unsigned == '%' || unsigned == '"' || unsigned < 0x20 || unsigned >= 0x7F) {
                value.append('%').append(HEX_DIGITS[unsigned >> 4]).append(HEX_DIGITS[unsigned & 0xF]);
            } else {
                value.append((char) unsigned);
            }
        }
        return value.append('"').toString();
    }

    /**
     * Adds the response header naming the operation to the response of a {@code MockRestServiceServer}.
     */
    public static DefaultResponseCreator withObservation(DefaultResponseCreator response, String fqn) {
        return response.header(REACTION_OBSERVATION_HEADER, observationHeaderValue(fqn));
    }
}
