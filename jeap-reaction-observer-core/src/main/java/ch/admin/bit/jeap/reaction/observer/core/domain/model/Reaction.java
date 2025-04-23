package ch.admin.bit.jeap.reaction.observer.core.domain.model;

public record Reaction(Observation trigger, Observation action) {

    public String id() {
        if (trigger == null) {
            return action.id().value();
        } else if (action == null) {
            return trigger.id().value();
        } else {
            return trigger.id().value() + "#" + action.id().value();
        }
    }
}
