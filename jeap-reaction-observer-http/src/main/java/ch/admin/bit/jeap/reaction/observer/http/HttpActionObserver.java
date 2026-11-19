package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.http.HttpObservationMetrics.DroppedCallReason;
import ch.admin.bit.jeap.reaction.observer.http.HttpObservationMetrics.UnidentifiedCallReason;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Turns the outcome of an outgoing HTTP request into an action of the reaction being recorded:
 * <ul>
 *     <li>The provider named the operation in the observation header: a fine action with that name, whatever
 *     the status of the response is - the action is identified and it took place. An operation name over the
 *     limits of the {@link HttpOperationRegistry} is not accepted: the call is then treated like one whose
 *     observation header cannot be used.</li>
 *     <li>The target answered with a status below 500 without naming the operation (a provider without the
 *     header module, an external target, a 401 of a security filter, a 404 without a mapping): a coarse action,
 *     named by the target and the method, see {@link HttpTargetNaming}.</li>
 *     <li>The call failed - no response, or a status of 500 or above without a valid observation header: no
 *     action, and the execution of the current trigger is discarded. A coarse action for an operational failure
 *     would result in a reaction that describes the failure instead of what the service does.</li>
 * </ul>
 * Only the outcome of the single call counts. What is remembered about earlier calls serves the limits alone:
 * the targets of coarse actions and the operation names accepted so far.
 */
@Slf4j
class HttpActionObserver {

    private static final int FIRST_FAILED_STATUS = 500;

    private final Supplier<ReactionRecorder> reactionRecorder;
    private final HttpTargetNaming targetNaming;
    private final HttpTargetRegistry targetRegistry;
    private final HttpOperationRegistry operationRegistry;
    private final HttpObservationMetrics metrics;
    private final ThrottledLog failedCallLog;
    private final ThrottledLog targetLimitLog;
    private final ThrottledLog operationLimitLog;

    /**
     * @param reactionRecorder supplies the recorder when the first call is observed, not earlier: the recorder
     *                         depends on the infrastructure that reports reactions
     * @param logInterval      the interval between two log entries on the same subject
     */
    HttpActionObserver(Supplier<ReactionRecorder> reactionRecorder, HttpTargetNaming targetNaming,
                       HttpTargetRegistry targetRegistry, HttpOperationRegistry operationRegistry,
                       HttpObservationMetrics metrics, Duration logInterval) {
        this.reactionRecorder = reactionRecorder;
        this.targetNaming = targetNaming;
        this.targetRegistry = targetRegistry;
        this.operationRegistry = operationRegistry;
        this.metrics = metrics;
        this.failedCallLog = new ThrottledLog(logInterval);
        this.targetLimitLog = new ThrottledLog(logInterval);
        this.operationLimitLog = new ThrottledLog(logInterval);
    }

    /**
     * @param observationHeader the value of the first observation header line of the response, null if there
     *                          is none
     */
    void onResponse(String method, URI uri, int status, String observationHeader) {
        ReactionObservationHeader header = ReactionObservationHeader.parse(observationHeader);
        if (header.isValid() && operationRegistry.register(operationTarget(uri), header.fqn())) {
            recordAction(Observation.ofHttpRequest(header.fqn()));
            return;
        }
        // From here on a valid header names an operation over the limit, which counts like a header that cannot
        // be used.
        // The recorder comes first, the metrics after it: a failure of a metric must not keep the recorder from
        // discarding the execution.
        if (status >= FIRST_FAILED_STATUS) {
            onCallFailed(method, uri, "status " + status + (header.isValid() ?
                    " with an operation name that is not accepted" : " without a valid observation header"));
        } else {
            recordCoarseAction(method, uri);
        }
        if (header.isValid()) {
            logOperationLimit(uri);
        }
        metrics.unidentifiedCall(unidentifiedCallReason(header));
    }

    /**
     * New operation names are counted by the target the consumer called. A URI without a host has no target;
     * the new names of all such requests are counted together.
     */
    private String operationTarget(URI uri) {
        return targetNaming.target(uri).orElse("");
    }

    private static UnidentifiedCallReason unidentifiedCallReason(ReactionObservationHeader header) {
        return switch (header.status()) {
            case VALID -> UnidentifiedCallReason.OPERATION_LIMIT;
            case ABSENT -> UnidentifiedCallReason.NO_HEADER;
            case INVALID -> UnidentifiedCallReason.INVALID_HEADER;
        };
    }

    /**
     * The call ended without a response, for example with an I/O error or a timeout.
     */
    void onNoResponse(String method, URI uri) {
        onCallFailed(method, uri, "no response");
        metrics.unidentifiedCall(UnidentifiedCallReason.NO_RESPONSE);
    }

    private void onCallFailed(String method, URI uri, String cause) {
        // A failed call is detected before the target limit is checked: it does not take one of the targets.
        reactionRecorder.get().onActionFailed();
        metrics.droppedCall(DroppedCallReason.CALL_FAILED);
        failedCallLog.log(occurrences -> log.info("HTTP request {} {} failed ({}). It is not observed as an action, " +
                        "and the reaction of the execution it belongs to, if any, is not reported for this execution. " +
                        "Failed HTTP requests since the last log entry: {}",
                method, targetNaming.target(uri).orElse("to an unknown target"), cause, occurrences));
    }

    private void recordCoarseAction(String method, URI uri) {
        Optional<String> target = targetNaming.target(uri);
        if (target.isEmpty()) {
            // Without a host there is nothing to name the action by
            return;
        }
        // The target is registered before the action is recorded, although recording may still drop the action
        // because the HTTP action budget of the trigger is used up - the target then holds one of the places
        // without an action this time. That is accepted on purpose: it takes both limits at once, the target is
        // a real one that needs its place in the next execution within the budget anyway, and checking the
        // budget first would need a way to ask the recorder for room without recording.
        if (!targetRegistry.register(target.get())) {
            metrics.droppedCall(DroppedCallReason.TARGET_LIMIT);
            logTargetLimit();
            return;
        }
        recordAction(Observation.ofHttpRequest(HttpTargetNaming.coarseFqn(target.get(), method)));
    }

    private void logTargetLimit() {
        if (targetRegistry.maxTargetCount() == 0) {
            // Coarse actions are switched off on purpose: nothing to warn about, the metric still counts
            return;
        }
        targetLimitLog.log(occurrences -> log.warn("This instance already observes coarse HTTP request actions " +
                        "for the maximum of {} targets. Requests to further targets that do not name their " +
                        "operation are not observed. Requests not observed since the last log entry: {}",
                targetRegistry.maxTargetCount(), occurrences));
    }

    private void logOperationLimit(URI uri) {
        if (operationRegistry.acceptsNoOperations()) {
            // Operation names are switched off on purpose: nothing to warn about, the metric still counts
            return;
        }
        operationLimitLog.log(occurrences -> log.warn("Responses name operations that are not accepted, " +
                        "most recently the response of {}: a target has already contributed the maximum of {} new " +
                        "operation names, or this instance has already accepted the maximum of {} different " +
                        "operation names in total. Such a request is observed like one whose operation is not " +
                        "named. Requests with an operation name that was not accepted since the last log entry: {}",
                targetNaming.target(uri).orElse("an unknown target"), operationRegistry.maxNewOperationCountPerTarget(),
                operationRegistry.maxOperationCount(), occurrences));
    }

    private void recordAction(Observation action) {
        if (!reactionRecorder.get().recordAction(action)) {
            metrics.droppedCall(DroppedCallReason.HTTP_ACTION_LIMIT);
        }
    }
}
