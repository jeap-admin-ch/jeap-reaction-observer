package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sets the {@code JEAP-REACTION-OBSERVATION} response header for every request that is routed to an
 * observable handler and carries the {@code JEAP-REACTION-MODEL-VERSION} request header.
 * <p>
 * Runs in {@code preHandle}, before the handler and before the response body is written, using the path
 * pattern Spring's handler mapping already resolved by then - see {@link OperationNameResolver}. Because the
 * header is set this early, it is also present on 4xx/5xx responses produced by a
 * {@code @ControllerAdvice} or a {@code ResponseStatusException}, and on responses to requests handled
 * asynchronously ({@code Callable}, {@code DeferredResult}, SSE): in all of these cases the response object
 * is the same one {@code preHandle} already wrote the header to.
 * <p>
 * Only the {@code REQUEST} dispatch is used to compute and set the header; a container's {@code ERROR}
 * dispatch to the error page (triggered by an unhandled exception) is handled separately in
 * {@link #reapplyAfterErrorDispatch}, because it is undocumented/container-specific whether the container
 * resets response headers before that dispatch.
 * <p>
 * <b>Known limits</b> (see docs/response-header.md): no header on a 401 from a security filter, a 404 with no
 * matching handler, or a 503 from an ingress/proxy in front of the service - {@code preHandle} never runs
 * for these. No header on calls made in filters that run before the {@code DispatcherServlet}.
 * <p>
 * Fail-safe: any exception here is caught and logged with a bounded frequency; the request is never failed.
 */
@Slf4j
class ReactionObservationHeaderInterceptor implements AsyncHandlerInterceptor {

    private static final int MAX_LOGGED_FAILURES = 5;
    private static final String VARY_HEADER = "Vary";

    private final OperationNameResolver operationNameResolver;
    private final AtomicInteger loggedFailures = new AtomicInteger();

    ReactionObservationHeaderInterceptor(OperationNameResolver operationNameResolver) {
        this.operationNameResolver = operationNameResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            DispatcherType dispatcherType = request.getDispatcherType();
            if (dispatcherType == DispatcherType.REQUEST) {
                apply(request, response, handler);
            } else if (dispatcherType == DispatcherType.ERROR) {
                reapplyAfterErrorDispatch(request, response);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
        return true;
    }

    private void apply(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<String> operationName = operationNameResolver.resolve(request, handler);
        if (operationName.isEmpty()) {
            return;
        }
        String fqn = operationName.get();
        request.setAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE, fqn);
        // The response always varies on this request header for an observable operation, whether or not the
        // signal was present on this particular request - so Vary is added unconditionally, not only when
        // the JEAP-REACTION-OBSERVATION header below is actually set.
        addVaryHeader(response);
        if (request.getHeader(ReactionObserverHeaderNames.REACTION_MODEL_VERSION) == null) {
            return;
        }
        setObservationHeader(response, ReactionObservationEncoder.encode(fqn));
    }

    /**
     * The container's dispatch to the error page may reset response headers (see class javadoc); if it did,
     * and the original request dispatch had already resolved an operation name and seen the request signal,
     * re-apply the header from the name stored in the request attribute. {@code Vary} is re-applied whenever a
     * name was stored, whether or not the request carried the signal. A no-op if both headers already made
     * it onto the response, or if the original dispatch never resolved a name (e.g. no handler matched at
     * all - documented as a limit).
     */
    private void reapplyAfterErrorDispatch(HttpServletRequest request, HttpServletResponse response) {
        Object storedFqn = request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE);
        if (!(storedFqn instanceof String fqn)) {
            return;
        }
        addVaryHeader(response);
        if (request.getHeader(ReactionObserverHeaderNames.REACTION_MODEL_VERSION) == null) {
            return;
        }
        if (response.getHeader(ReactionObserverHeaderNames.REACTION_OBSERVATION) != null) {
            return;
        }
        setObservationHeader(response, ReactionObservationEncoder.encode(fqn));
    }

    /**
     * Later filters (e.g. Spring Security's {@code HeaderWriterFilter}, {@code ContentCachingResponseWrapper})
     * may wrap the response again, so the protected wrapper is searched for in the whole wrapper chain.
     */
    private void setObservationHeader(HttpServletResponse response, String encodedValue) {
        ProtectedObservationHeaderResponse protectedResponse =
                WebUtils.getNativeResponse(response, ProtectedObservationHeaderResponse.class);
        if (protectedResponse != null) {
            protectedResponse.setProtectedHeader(encodedValue);
        } else {
            response.setHeader(ReactionObserverHeaderNames.REACTION_OBSERVATION, encodedValue);
        }
    }

    private void addVaryHeader(HttpServletResponse response) {
        Collection<String> existingLines = response.getHeaders(VARY_HEADER);
        for (String line : existingLines) {
            if (containsToken(line, ReactionObserverHeaderNames.REACTION_MODEL_VERSION)) {
                return;
            }
        }
        // addHeader appends a new Vary line and leaves any existing lines (e.g. several Vary lines) untouched
        response.addHeader(VARY_HEADER, ReactionObserverHeaderNames.REACTION_MODEL_VERSION);
    }

    private static boolean containsToken(String headerValue, String token) {
        for (String part : headerValue.split(",")) {
            String trimmed = part.trim();
            if (trimmed.equalsIgnoreCase(token) || trimmed.equals("*")) {
                return true;
            }
        }
        return false;
    }

    private void logFailure(RuntimeException e) {
        if (loggedFailures.incrementAndGet() <= MAX_LOGGED_FAILURES) {
            log.warn("Failed to set the {} response header; the request continues without it",
                    ReactionObserverHeaderNames.REACTION_OBSERVATION, e);
        }
    }
}
