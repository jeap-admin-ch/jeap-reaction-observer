package ch.admin.bit.jeap.reaction.observer.header;

/**
 * The two header names of the frozen contract between consumer and provider (see docs/response-header.md): the
 * request signal a consumer sends to ask for the operation name, and the response header a provider answers
 * with.
 */
public final class ReactionObserverHeaderNames {

    /**
     * Request header a consumer sends to ask the provider to name the operation it is calling. The value is
     * the consumer's reaction model version as an integer; the provider does not evaluate it, only checks
     * that the header is present.
     */
    public static final String REACTION_MODEL_VERSION = "JEAP-REACTION-MODEL-VERSION";

    /**
     * Response header a provider answers with, naming the called operation as provider, HTTP method and
     * normalised path pattern. Encoded as an RFC 9651 Structured Field List containing one Display String,
     * see {@link ReactionObservationEncoder}.
     */
    public static final String REACTION_OBSERVATION = "JEAP-REACTION-OBSERVATION";

    private ReactionObserverHeaderNames() {
    }
}
