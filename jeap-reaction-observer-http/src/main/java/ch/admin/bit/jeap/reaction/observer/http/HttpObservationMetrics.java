package ch.admin.bit.jeap.reaction.observer.http;

import java.util.Locale;

/**
 * The two metrics of the HTTP observation. Neither of them carries the called host.
 */
interface HttpObservationMetrics {

    /**
     * Does not count anything, for services without a meter registry.
     */
    HttpObservationMetrics NONE = new HttpObservationMetrics() {
        @Override
        public void unidentifiedCall(UnidentifiedCallReason reason) {
            // No meter registry, nothing to count
        }

        @Override
        public void droppedCall(DroppedCallReason reason) {
            // No meter registry, nothing to count
        }
    };

    /**
     * Why a call was not identified by an operation name of the provider.
     */
    enum UnidentifiedCallReason {
        /**
         * The response has no observation header.
         */
        NO_HEADER,
        /**
         * The response has an observation header that cannot be used.
         */
        INVALID_HEADER,
        /**
         * The response names an operation that is not accepted: the called target already contributed the
         * maximum number of new operation names, or the instance already accepted the maximum number of
         * different operation names in total.
         */
        OPERATION_LIMIT,
        /**
         * There was no response.
         */
        NO_RESPONSE;

        String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Why an observed call did not become an action.
     */
    enum DroppedCallReason {
        /**
         * The call failed: no response, or a status of 500 or above without a valid observation header.
         */
        CALL_FAILED,
        /**
         * The instance already names coarse actions for the maximum number of targets.
         */
        TARGET_LIMIT,
        /**
         * The current trigger already has the maximum number of HTTP request actions.
         */
        HTTP_ACTION_LIMIT;

        String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Diagnosis: counts each call that was not identified by an operation name of the provider, exactly once
     * and at the time of the call - also outside a trigger and in an execution that is discarded.
     */
    void unidentifiedCall(UnidentifiedCallReason reason);

    /**
     * Loss: counts each observed call that did not become an action, exactly once.
     */
    void droppedCall(DroppedCallReason reason);
}
