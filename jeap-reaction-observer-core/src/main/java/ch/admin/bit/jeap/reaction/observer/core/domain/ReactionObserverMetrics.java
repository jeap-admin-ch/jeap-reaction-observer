package ch.admin.bit.jeap.reaction.observer.core.domain;

import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * The metrics of the observation of reactions: how many reactions the instance has identified, and how many
 * executions it did not count.
 * <p>
 * Internal interface between the modules of the reaction observer library. A service can replace the metrics
 * of the library with a bean of its own of this type, but the interface is no stable API and can change in any
 * release.
 */
public interface ReactionObserverMetrics {

    /**
     * Publishes nothing, for services without a meter registry.
     */
    ReactionObserverMetrics NONE = new ReactionObserverMetrics() {
        @Override
        public void droppedExecution(DroppedExecutionReason reason) {
            // No meter registry, nothing to count
        }

        @Override
        public void identifiedReactions(IntSupplier count) {
            // No meter registry, nothing to publish
        }
    };

    /**
     * Why an execution of a reaction was not counted.
     */
    enum DroppedExecutionReason {
        /**
         * The instance already identified the maximum number of reactions in the current identification period,
         * and the reaction is not one of them.
         */
        REACTION_LIMIT,
        /**
         * The reaction could not be marked as identified: its identification could not be handed over for
         * reporting, or the report had already failed when the execution was to be counted. An execution
         * another thread observed in the meantime can have been counted all the same.
         */
        NOT_IDENTIFIED,
        /**
         * The counts that wait to be reported already are those of the maximum number of reactions, and the
         * reaction is not one of them. This takes more different reactions than that since the counts were
         * reported the last time, e.g. those of two identification periods if a period ended in between.
         */
        COUNT_LIMIT;

        public String tagValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Loss: counts each execution the observer dropped for one of the {@link DroppedExecutionReason reasons},
     * exactly once. Other losses are not counted here, e.g. the counts of an event that could not be sent.
     */
    void droppedExecution(DroppedExecutionReason reason);

    /**
     * Publishes the number of reactions the instance has identified in the current identification period. To be
     * called once, by whoever creates the {@link ReactionObserverService}, with
     * {@link ReactionObserverService#identifiedReactionCount()}.
     */
    void identifiedReactions(IntSupplier count);
}
