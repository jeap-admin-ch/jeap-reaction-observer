package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Wraps the response so that {@link ReactionObservationHeaderInterceptor} can protect the
 * {@code JEAP-REACTION-OBSERVATION} header it sets in {@code preHandle} from being overwritten later - see
 * {@link ProtectedObservationHeaderResponse}. Registered as early as possible in the filter chain so the
 * wrapped response reaches every later filter, the dispatcher servlet and the handler. Only wraps; it does
 * not know the handler or path pattern yet, so it never sets the header itself.
 * <p>
 * A failure here must not fail the request: if wrapping is skipped (the exception is swallowed after
 * logging), the interceptor still sets the header, just without the BFF-overwrite protection. Only the
 * wrapping itself is guarded - {@code chain.doFilter} is called exactly once and its exceptions propagate
 * unchanged, so a legitimate application failure is never swallowed or re-run.
 */
@Slf4j
class ReactionObservationHeaderProtectionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ServletResponse effectiveResponse = response;
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse httpResponse) {
            try {
                effectiveResponse = new ProtectedObservationHeaderResponse(httpResponse,
                        ReactionObserverHeaderNames.REACTION_OBSERVATION);
            } catch (RuntimeException e) {
                log.warn("Failed to wrap the response to protect the {} header; continuing without protection "
                        + "against it being overwritten later", ReactionObserverHeaderNames.REACTION_OBSERVATION, e);
            }
        }
        chain.doFilter(request, effectiveResponse);
    }
}
