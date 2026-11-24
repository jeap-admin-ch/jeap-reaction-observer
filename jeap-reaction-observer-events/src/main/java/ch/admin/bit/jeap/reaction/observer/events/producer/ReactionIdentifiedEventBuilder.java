package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ReactionIdentifiedEventBuilder extends AvroDomainEventBuilder<ReactionIdentifiedEventBuilder, ReactionIdentifiedEvent> {

    private static final DateTimeFormatter IDENTIFICATION_START_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String serviceName;
    private final String systemName;
    private final int reactionModelVersion;
    private Reaction reaction;

    /**
     * @param serviceInstanceIdentifier identifies the reporting service instance, must not be null
     * @param reactionModelVersion      the reaction model version the service observes by
     * @param identificationStart       the start of the identification period the instance identified the
     *                                  reaction in: an instance reports each of its reactions once per period.
     *                                  Must not be null
     */
    public static ReactionIdentifiedEvent buildEvent(String systemName, String serviceName, UUID serviceInstanceIdentifier,
                                                     Reaction reaction, int reactionModelVersion,
                                                     Instant identificationStart) {
        Objects.requireNonNull(serviceInstanceIdentifier, "serviceInstanceIdentifier");
        Objects.requireNonNull(identificationStart, "identificationStart");
        ReactionIdentifiedEventBuilder builder = new ReactionIdentifiedEventBuilder(serviceName, systemName, reactionModelVersion);
        builder.setReaction(reaction, serviceInstanceIdentifier, identificationStart);
        return builder.build();
    }

    private void setReaction(Reaction reaction, UUID serviceInstanceIdentifier, Instant identificationStart) {
        this.reaction = reaction;
        idempotenceId(createIdempotenceId(serviceName, serviceInstanceIdentifier, reaction.id(), reactionModelVersion,
                identificationStart));
    }

    /**
     * The idempotence id is
     * "ri_&lt;service&gt;_&lt;serviceInstanceIdentifier&gt;_&lt;reactionId&gt;_v&lt;reactionModelVersion&gt;_&lt;identificationStart&gt;",
     * with the start of the identification period in UTC, for example "20261026T041233Z".
     * <p>
     * The id identifies one report: one reaction, reported by one instance since one identification start. A
     * service reports the same reaction once per instance and identification period, again after a restart, and
     * with another model version after an upgrade or a rollback. Each of these reports is a message of its own with an id of its own, so
     * that a consumer processing the events idempotently processes each report once, and a report that is
     * delivered twice only once.
     * <ul>
     *     <li>The instance identifier tells the reports of different instances apart. The identification start
     *     does not: it is formatted to whole seconds, and two instances may start within the same second. Their
     *     reports of a reaction have the same content, so a consumer would not miss anything - but the id would
     *     not identify a report.</li>
     *     <li>The identification start tells the reports of one instance apart: an instance reports its
     *     reactions again in each identification period, and no two periods of an instance start within the
     *     same second.</li>
     *     <li>The model version is the same for all reports of an instance. It is part of the id so that the id
     *     tells by which version the reaction was reported.</li>
     * </ul>
     */
    static String createIdempotenceId(String serviceName, UUID serviceInstanceIdentifier, String reactionId,
                                      int reactionModelVersion, Instant identificationStart) {
        return "ri_" + serviceName + "_" + serviceInstanceIdentifier + "_" + reactionId + "_v" + reactionModelVersion +
                "_" + IDENTIFICATION_START_FORMAT.format(identificationStart);
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
