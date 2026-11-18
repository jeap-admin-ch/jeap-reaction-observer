package ch.admin.bit.jeap.reaction.observer.http;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Publishes the two metrics as counters, one per reason. All counters are registered at startup, so that they
 * are visible with the value 0.
 */
class MicrometerHttpObservationMetrics implements HttpObservationMetrics {

    static final String UNIDENTIFIED_HTTP_CALLS = "jeap.reaction.observer.unidentified.http.calls";
    static final String DROPPED_HTTP_CALLS = "jeap.reaction.observer.dropped.http.calls";
    static final String REASON_TAG = "reason";

    private final Map<UnidentifiedCallReason, Counter> unidentifiedCalls = new EnumMap<>(UnidentifiedCallReason.class);
    private final Map<DroppedCallReason, Counter> droppedCalls = new EnumMap<>(DroppedCallReason.class);

    MicrometerHttpObservationMetrics(MeterRegistry meterRegistry) {
        for (UnidentifiedCallReason reason : UnidentifiedCallReason.values()) {
            unidentifiedCalls.put(reason, Counter.builder(UNIDENTIFIED_HTTP_CALLS)
                    .description("Outgoing HTTP requests whose operation the provider did not name")
                    .tag(REASON_TAG, reason.tagValue())
                    .register(meterRegistry));
        }
        for (DroppedCallReason reason : DroppedCallReason.values()) {
            droppedCalls.put(reason, Counter.builder(DROPPED_HTTP_CALLS)
                    .description("Observed outgoing HTTP requests that did not become an action of a reaction")
                    .tag(REASON_TAG, reason.tagValue())
                    .register(meterRegistry));
        }
    }

    @Override
    public void unidentifiedCall(UnidentifiedCallReason reason) {
        unidentifiedCalls.get(reason).increment();
    }

    @Override
    public void droppedCall(DroppedCallReason reason) {
        droppedCalls.get(reason).increment();
    }
}
