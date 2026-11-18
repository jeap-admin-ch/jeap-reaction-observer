package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReactionIdentifiedEventBuilder extends AvroDomainEventBuilder<ReactionIdentifiedEventBuilder, ReactionIdentifiedEvent> {

    private static final DateTimeFormatter IDENTIFICATION_START_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String serviceName;
    private final String systemName;
    private final int reactionModelVersion;
    private Reaction reaction;

    /**
     * @param reactionModelVersion the reaction model version the service observes by
     * @param identificationStart  the time at which the service instance began to report its reactions.
     *                             Currently that is the start of the instance.
     */
    public static ReactionIdentifiedEvent buildEvent(String systemName, String serviceName, Reaction reaction,
                                                     int reactionModelVersion, Instant identificationStart) {
        ReactionIdentifiedEventBuilder builder = new ReactionIdentifiedEventBuilder(serviceName, systemName, reactionModelVersion);
        builder.setReaction(reaction, identificationStart);
        return builder.build();
    }

    private void setReaction(Reaction reaction, Instant identificationStart) {
        this.reaction = reaction;
        idempotenceId(createIdempotenceId(serviceName, reaction.id(), reactionModelVersion, identificationStart));
    }

    /**
     * The idempotence id is "ri_&lt;service&gt;_&lt;reactionId&gt;_v&lt;reactionModelVersion&gt;_&lt;identificationStart&gt;",
     * with the time at which the instance began to report its reactions in UTC, for example "20261026T041233Z".
     * A service reports the same reaction again after a restart, and with another model version after an upgrade
     * or a rollback. Model version and identification start make each of these reports a message of its own, so
     * that a consumer processing the events idempotently does not drop them as duplicates.
     */
    static String createIdempotenceId(String serviceName, String reactionId, int reactionModelVersion,
                                      Instant identificationStart) {
        return "ri_" + serviceName + "_" + reactionId + "_v" + reactionModelVersion + "_" +
                IDENTIFICATION_START_FORMAT.format(identificationStart);
    }

    private ReactionIdentifiedEventBuilder(String serviceName, String systemName, int reactionModelVersion) {
        super(ReactionIdentifiedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
        this.reactionModelVersion = reactionModelVersion;
    }

    @Override
    public ReactionIdentifiedEvent build() {
        Object reactionPayload;
        if (reaction.isTriggerOnly()) {
            Observation observation = createObservation(reaction.trigger());
            reactionPayload = new TriggerOnly(reaction.id(), observation);
        } else if (reaction.isActionOnly()) {
            // For action-only reactions, a single observation is created.
            // If there is no trigger, we cannot group several actions during the execution of the trigger.
            Observation observation = createObservation(reaction.getSingleAction());
            reactionPayload = new ActionOnly(reaction.id(), observation);
        } else {
            List<Observation> actions = createObservations(reaction.actions());
            reactionPayload = new ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction(reaction.id(),
                    createObservation(reaction.trigger()), actions);
        }
        setPayload(new ReactionIdentifiedPayload(reactionPayload, reactionModelVersion));
        return super.build();
    }

    private List<Observation> createObservations(List<ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }

        // Avoid creating a stream if there is only one action
        if (actions.size() == 1) {
            return List.of(createObservation(actions.getFirst()));
        }

        return actions.stream()
                .map(this::createObservation)
                .toList();
    }

    private Observation createObservation(ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation observation) {
        Map<String, String> props = observation.props();
        if (props == null) {
            props = Map.of();
        }
        return new Observation(observation.id().value(), observation.type().name().toLowerCase(), observation.fqn(), props);
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
    protected ReactionIdentifiedEventBuilder self() {
        return this;
    }
}
