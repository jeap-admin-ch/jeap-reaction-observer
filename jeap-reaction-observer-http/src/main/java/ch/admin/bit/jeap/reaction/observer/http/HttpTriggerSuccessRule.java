package ch.admin.bit.jeap.reaction.observer.http;

/**
 * Decides whether an incoming HTTP request that has been handled counts as a reaction:
 * <ul>
 *     <li>A status below 400 always counts, also without actions.</li>
 *     <li>A status from 400 to 499 counts only if the request caused at least one action. Whether the handler
 *     threw an exception that was turned into the status or returned the status makes no difference.</li>
 *     <li>A status of 500 or above, or an exception that leaves Spring MVC, never counts - also if a filter
 *     turns the exception into a response with a status of 4xx afterwards, as Spring Security does with the
 *     exception of a denied method call. A service that does not handle an exception itself did not foresee
 *     it, so it is not a functional outcome of the request.</li>
 * </ul>
 * A status of 4xx can often be the functional outcome of a request handled correctly. A 4xx without any action
 * does not count, so that a reaction without actions always means "handled successfully, nothing caused".
 * A reaction carries no status, and rejected requests would not be told apart from successful ones.
 * <p>
 * The rule is fixed for a reaction model version: services observing by the same version observe the same.
 */
final class HttpTriggerSuccessRule {

    private static final int FIRST_CLIENT_ERROR_STATUS = 400;
    private static final int FIRST_SERVER_ERROR_STATUS = 500;

    /**
     * @param status             the status of the response when the handling of the request completed
     * @param unhandledException the exception that ended the handling without having been turned into a
     *                           response, null if there is none. With such an exception the status is not the
     *                           one the caller gets: the response turns into a 500 only afterwards
     * @param hasActions         whether the request caused at least one action
     */
    boolean counts(int status, Throwable unhandledException, boolean hasActions) {
        if (unhandledException != null || status >= FIRST_SERVER_ERROR_STATUS) {
            return false;
        }
        return status < FIRST_CLIENT_ERROR_STATUS || hasActions;
    }
}
