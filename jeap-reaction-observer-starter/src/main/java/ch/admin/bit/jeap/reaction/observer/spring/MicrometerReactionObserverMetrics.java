package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Publishes the dropped executions as counters, one per reason, and the identified reactions as a gauge. The
 * counters are registered at startup, so that they are visible with the value 0.
 */
class MicrometerReactionObserverMetrics implements ReactionObserverMetrics {

    static final String IDENTIFIED_REACTIONS = "jeap.reaction.observer.identified.reactions";
    static final String DROPPED_EXECUTIONS = "jeap.reaction.observer.dropped.executions";
    static final String REASON_TAG = "reason";

    private final MeterRegistry meterRegistry;
    private final Map<DroppedExecutionReason, Counter> droppedExecutions = new EnumMap<>(DroppedExecutionReason.class);

    MicrometerReactionObserverMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (DroppedExecutionReason reason : DroppedExecutionReason.values()) {
            droppedExecutions.put(reason, Counter.builder(DROPPED_EXECUTIONS)
                    .description("Executions of reactions the observer dropped, by reason")
                    .tag(REASON_TAG, reason.tagValue())
                    .register(meterRegistry));
        }
    }

    @Override
    public void droppedExecution(DroppedExecutionReason reason) {
        droppedExecutions.get(reason).increment();
    }

    @Override
    public void identifiedReactions(IntSupplier count) {
        Gauge.builder(IDENTIFIED_REACTIONS, count, IntSupplier::getAsInt)
                .description("Reactions the instance has identified in the current identification period")
                .strongReference(true)
                .register(meterRegistry);
    }
}
