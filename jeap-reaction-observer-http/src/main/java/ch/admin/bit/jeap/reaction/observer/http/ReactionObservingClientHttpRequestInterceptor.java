package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;

/**
 * Observes an outgoing HTTP request as an action: asks the provider to name the called operation with the
 * request header {@value #REACTION_MODEL_VERSION_HEADER} and reads the answer from the response header
 * {@value #REACTION_OBSERVATION_HEADER}, see docs/response-header.md for the protocol.
 * <p>
 * The observation never affects the call: request, response and exceptions of the call pass unchanged, and a
 * failure of the observation itself is logged and otherwise ignored. The one difference: if the status of the
 * response cannot be read, a caller reading it again may get a new instance of the exception, see the known limits
 * in docs/http-requests.md.
 */
@Slf4j
class ReactionObservingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    static final String REACTION_MODEL_VERSION_HEADER = "JEAP-REACTION-MODEL-VERSION";
    static final String REACTION_OBSERVATION_HEADER = "JEAP-REACTION-OBSERVATION";

    private final HttpActionObserver httpActionObserver;
    private final String reactionModelVersion;
    private final ThrottledLog observationFailureLog;

    /**
     * @param reactionModel the reaction model the service observes by, its version is sent with every request
     * @param logInterval   the interval between two log entries on a failure of the observation
     */
    ReactionObservingClientHttpRequestInterceptor(HttpActionObserver httpActionObserver, ReactionModel reactionModel,
                                                  Duration logInterval) {
        this.httpActionObserver = httpActionObserver;
        this.reactionModelVersion = String.valueOf(reactionModel.version());
        this.observationFailureLog = new ThrottledLog(logInterval);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        failSafe(() -> request.getHeaders().set(REACTION_MODEL_VERSION_HEADER, reactionModelVersion));
        ClientHttpResponse response = execute(request, body, execution);
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException | RuntimeException e) {
            // Some request factories read the response only now (HttpURLConnection for a request with a body): a
            // timeout or a broken connection shows up here, there is no response after all. Or the status code is
            // invalid. Either way the call failed. The response is passed unchanged, the caller gets the failure when
            // it reads the response - from HttpURLConnection as a new exception of the same class and with the same
            // message, with the original one as its cause.
            observeNoResponse(request);
            return response;
        }
        failSafe(() -> httpActionObserver.onResponse(request.getMethod().name(), request.getURI(), status,
                response.getHeaders().getFirst(REACTION_OBSERVATION_HEADER)));
        return response;
    }

    /**
     * Executes the call. Without a response - an I/O error, a timeout, or an interceptor further down the chain
     * gave up - the call failed, and its exception is passed on.
     */
    private ClientHttpResponse execute(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            return execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            observeNoResponse(request);
            throw e;
        }
    }

    private void observeNoResponse(HttpRequest request) {
        failSafe(() -> httpActionObserver.onNoResponse(request.getMethod().name(), request.getURI()));
    }

    /**
     * Runs a step of the observation so that its failure never reaches the call: the failure is logged
     * (throttled) and otherwise ignored.
     */
    private void failSafe(Runnable observationStep) {
        try {
            observationStep.run();
        } catch (RuntimeException e) {
            observationFailureLog.log(occurrences -> log.warn("Failed to observe an outgoing HTTP request as an " +
                    "action, the request itself is not affected. Failures since the last log entry: {}", occurrences, e));
        }
    }
}
