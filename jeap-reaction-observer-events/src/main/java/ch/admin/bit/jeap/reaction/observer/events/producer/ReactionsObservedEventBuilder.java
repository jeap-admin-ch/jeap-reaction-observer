package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.event.observed.Observation;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedPayload;
import ch.admin.bit.jeap.reaction.observer.event.observed.Timeframe;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ReactionsObservedEventBuilder extends AvroDomainEventBuilder<ReactionsObservedEventBuilder, ReactionsObservedEvent> {

    private static final DateTimeFormatter IDEMPOTENCE_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String serviceName;
    private final String systemName;
    private UUID serviceInstanceIdentifier;
    private Timeframe timeframe;
    private List<Observation> observations;
    private final int reactionModelVersion;

    /**
     * @param reactionModelVersion the reaction model version the service observes by, required so that the event
     *                             never reports another version than the one observed by
     */
    ReactionsObservedEventBuilder(String systemName, String serviceName, int reactionModelVersion) {
        super(ReactionsObservedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
        this.reactionModelVersion = reactionModelVersion;
    }

    ReactionsObservedEventBuilder serviceInstanceIdentifier(UUID serviceInstanceIdentifier) {
        this.serviceInstanceIdentifier = serviceInstanceIdentifier;
        return this;
    }

    ReactionsObservedEventBuilder timeframe(Instant from, Instant to) {
        this.timeframe = new Timeframe(from, to);
        return this;
    }

    ReactionsObservedEventBuilder countByReactionId(Map<String, AtomicInteger> countByReactionId) {
        this.observations = toObservations(countByReactionId);
        return this;
    }

    private List<Observation> toObservations(Map<String, AtomicInteger> countByReactionId) {
        return countByReactionId.entrySet().stream()
                .map(entry -> toObservation(entry.getKey(), entry.getValue().get()))
                .toList();
    }

    private static Observation toObservation(String reactionId, int count) {
        return Observation.newBuilder()
                .setReactionId(reactionId)
                .setCount(count)
                .build();
    }

    @Override
    public ReactionsObservedEvent build() {
        setPayload(new ReactionsObservedPayload(timeframe, observations, reactionModelVersion));
        idempotenceId(createIdempotenceId());
        return super.build();
    }

    private String createIdempotenceId() {
        return fmt(timeframe.getStart()) + "-" +
                fmt(timeframe.getEnd()) + "-" +
                serviceName + "-" +
                serviceInstanceIdentifier;
    }

    private String fmt(Instant instant) {
        return IDEMPOTENCE_ID_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected String getSystemName() {
        return systemName;
    }

    @Override
    protected ReactionsObservedEventBuilder self() {
        return this;
    }
}
