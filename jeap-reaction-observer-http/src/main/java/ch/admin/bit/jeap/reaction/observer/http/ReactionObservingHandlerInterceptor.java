package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.header.OperationNameResolver;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Observes an incoming HTTP request as a trigger: the messages the service sends and the HTTP requests it makes
 * while it handles the request become the actions of the trigger. The trigger is named like the operation in the
 * {@code JEAP-REACTION-OBSERVATION} response header, by the {@link OperationNameResolver} of the header module:
 * a request the header module does not name is not observed.
 * <p>
 * The life cycle of the request is mapped to the recorder:
 * <ul>
 *     <li>{@code preHandle} starts the trigger.</li>
 *     <li>{@code afterCompletion} publishes the reaction if the request counts, see
 *     {@link HttpTriggerSuccessRule}, and always ends the trigger.</li>
 *     <li>{@code afterConcurrentHandlingStarted} abandons the trigger: a request that is handled asynchronously
 *     ({@code Callable}, {@code DeferredResult}, SSE) is not observed as a trigger. Its outcome is not known yet,
 *     and what it causes happens in another thread. The actions recorded until then are reported as if the
 *     trigger had never been started, see {@link ReactionRecorder#abandonTrigger()}: without the trigger, as
 *     the actions in the other thread are.</li>
 * </ul>
 * Spring calls at least one of {@code afterCompletion} and {@code afterConcurrentHandlingStarted} for every
 * {@code preHandle} that returned true, in the thread of the {@code preHandle}, also if the handler or another
 * interceptor throws. The recorder keeps the trigger per thread, so no trigger outlives its request. Spring can
 * call both (an exception after concurrent handling has started), and it calls
 * {@code afterConcurrentHandlingStarted} also for a request this interceptor has not started a trigger for:
 * the mark at the request, see below, makes sure that a trigger is ended once and only by its own request.
 * <p>
 * A handler or an interceptor can also start the asynchronous processing of the servlet container itself
 * ({@code request.startAsync()}). Spring MVC does not know about it and calls {@code afterCompletion}: the
 * trigger of such a request is abandoned as well.
 * <p>
 * Only the {@code REQUEST} dispatch is observed. The other dispatches of the same request pass this interceptor
 * as well - a forward or include inside the request, the error dispatch after an unhandled exception or
 * {@code sendError}, the async dispatch - and neither start nor end a trigger. The request is marked while its
 * trigger is open, so that the trigger is ended exactly once.
 * <p>
 * Fail-safe: a failure of the observation is logged (throttled) and otherwise ignored, the request is never
 * affected.
 */
@Slf4j
class ReactionObservingHandlerInterceptor implements AsyncHandlerInterceptor {

    static final String TRIGGER_OPEN_ATTRIBUTE = ReactionObservingHandlerInterceptor.class.getName() + ".TRIGGER_OPEN";

    private final Supplier<ReactionRecorder> reactionRecorder;
    private final OperationNameResolver operationNameResolver;
    private final HttpTriggerSuccessRule successRule = new HttpTriggerSuccessRule();
    private final ThrottledLog observationFailureLog;

    /**
     * @param logInterval the interval between two log entries on a failure of the observation
     */
    ReactionObservingHandlerInterceptor(Supplier<ReactionRecorder> reactionRecorder,
                                        OperationNameResolver operationNameResolver, Duration logInterval) {
        this.reactionRecorder = reactionRecorder;
        this.operationNameResolver = operationNameResolver;
        this.observationFailureLog = new ThrottledLog(logInterval);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            if (isRequestDispatch(request) && !isTriggerOpen(request)) {
                operationName(request, handler).ifPresent(fqn -> startTrigger(request, fqn));
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            if (isRequestDispatch(request) && isTriggerOpen(request)) {
                completeTrigger(request, response, ex);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            if (isRequestDispatch(request) && isTriggerOpen(request)) {
                abandonTrigger(request);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /**
     * The header module stores the name at the request when it sets the header. Without that, e.g. because the
     * header is switched off, the name is resolved here, by the same rule.
     */
    private Optional<String> operationName(HttpServletRequest request, Object handler) {
        if (request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE) instanceof String fqn) {
            return Optional.of(fqn);
        }
        return operationNameResolver.resolve(request, handler);
    }

    /**
     * Spring only guarantees the callback that ends the trigger if this {@code preHandle} returns normally. If
     * the trigger cannot be marked as open after it has been started, it is ended right here.
     */
    private void startTrigger(HttpServletRequest request, String fqn) {
        ReactionRecorder recorder = reactionRecorder.get();
        recorder.onTriggerStart(Observation.ofHttpRequest(fqn));
        try {
            request.setAttribute(TRIGGER_OPEN_ATTRIBUTE, Boolean.TRUE);
        } catch (RuntimeException e) {
            recorder.afterTrigger();
            throw e;
        }
    }

    private void completeTrigger(HttpServletRequest request, HttpServletResponse response, Exception unhandledException) {
        ReactionRecorder recorder = reactionRecorder.get();
        boolean asyncStarted = false;
        try {
            request.removeAttribute(TRIGGER_OPEN_ATTRIBUTE);
            asyncStarted = request.isAsyncStarted();
            if (!asyncStarted
                    && successRule.counts(response.getStatus(), unhandledException, recorder.currentTriggerHasActions())) {
                recorder.onTriggerHandled();
            }
        } finally {
            if (asyncStarted) {
                recorder.abandonTrigger();
            } else {
                recorder.afterTrigger();
            }
        }
    }

    private void abandonTrigger(HttpServletRequest request) {
        try {
            request.removeAttribute(TRIGGER_OPEN_ATTRIBUTE);
        } finally {
            reactionRecorder.get().abandonTrigger();
        }
    }

    private static boolean isRequestDispatch(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.REQUEST;
    }

    private static boolean isTriggerOpen(HttpServletRequest request) {
        return request.getAttribute(TRIGGER_OPEN_ATTRIBUTE) != null;
    }

    private void logFailure(RuntimeException e) {
        observationFailureLog.log(occurrences -> log.warn("Failed to observe an incoming HTTP request as a " +
                "trigger, the request itself is not affected. Failures since the last log entry: {}", occurrences, e));
    }
}
