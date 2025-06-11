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
        if (reaction.action() == null && reaction.trigger() == null) {
            throw new IllegalArgumentException("Reaction must have at least an action or a trigger");
        }
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
        if (reaction.action() == null) {
            Observation observation = createObservation(reaction.trigger());
            reactionPayload = new TriggerOnly(observation);
        } else if (reaction.trigger() == null) {
            Observation observation = createObservation(reaction.action());
            reactionPayload = new ActionOnly(List.of(observation));
        } else {
            Observation action = createObservation(reaction.action());
            // TODO: This should include a list of actions
            List<Observation> actions = List.of(action);
            reactionPayload = new ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction(
                    createObservation(reaction.trigger()), actions);
        }
        setPayload(new ReactionIdentifiedPayload(reaction.id(), reactionPayload));
        return super.build();
    }

    private Observation createObservation(ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation observation) {
        Map<String, String> props = observation.props();
        if (props == null) {
            props = Map.of();
        }
        return new Observation(observation.type().name().toLowerCase(), observation.fqn(), props);
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
