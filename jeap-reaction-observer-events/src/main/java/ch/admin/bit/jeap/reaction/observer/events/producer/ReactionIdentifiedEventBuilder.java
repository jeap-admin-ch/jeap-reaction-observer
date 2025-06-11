package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.*;

import java.util.List;
import java.util.Map;

public class ReactionIdentifiedEventBuilder extends AvroDomainEventBuilder<ReactionIdentifiedEventBuilder, ReactionIdentifiedEvent> {

    private final String serviceName;
    private final String systemName;
    private Reaction reaction;

    public static ReactionIdentifiedEvent buildEvent(String systemName, String serviceName, Reaction reaction) {
        ReactionIdentifiedEventBuilder builder = new ReactionIdentifiedEventBuilder(serviceName, systemName);
        builder.setReaction(reaction);
        return builder.build();
    }

    private void setReaction(Reaction reaction) {
        this.reaction = reaction;
        idempotenceId("ri_" + reaction.id());
    }

    private ReactionIdentifiedEventBuilder(String serviceName, String systemName) {
        super(ReactionIdentifiedEvent::new);
        this.serviceName = serviceName;
        this.systemName = systemName;
    }

    @Override
    public ReactionIdentifiedEvent build() {
        Object reactionPayload;
        if (reaction.isTriggerOnly()) {
            Observation observation = createObservation(reaction.trigger());
            reactionPayload = new TriggerOnly(reaction.id(), observation);
        } else if (reaction.isActionOnly()) {
            Observation observation = createObservation(reaction.getSingleAction());
            reactionPayload = new ActionOnly(reaction.id(), observation);
        } else {
            List<Observation> actions = createObservations(reaction.actions());
            reactionPayload = new ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction(reaction.id(),
                    createObservation(reaction.trigger()), actions);
        }
        setPayload(new ReactionIdentifiedPayload(reactionPayload));
        return super.build();
    }

    private List<Observation> createObservations(List<ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }

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
