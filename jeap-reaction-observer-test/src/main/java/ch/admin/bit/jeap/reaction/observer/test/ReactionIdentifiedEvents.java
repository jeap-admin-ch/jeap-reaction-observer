package ch.admin.bit.jeap.reaction.observer.test;

import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ActionOnly;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Observation;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.TriggerOnly;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Test support to read a {@link ReactionIdentifiedEvent} whatever kind of reaction it reports.
 */
public final class ReactionIdentifiedEvents {

    private ReactionIdentifiedEvents() {
    }

    public static String reactionId(ReactionIdentifiedEvent event) {
        return switch (event.getPayload().getReaction()) {
            case TriggerOnly triggerOnly -> triggerOnly.getReactionId();
            case ActionOnly actionOnly -> actionOnly.getReactionId();
            case Reaction reaction -> reaction.getReactionId();
            default -> throw unexpectedReaction(event);
        };
    }

    /**
     * @return the trigger of the reaction, empty for an action-only reaction
     */
    public static Optional<Observation> trigger(ReactionIdentifiedEvent event) {
        return switch (event.getPayload().getReaction()) {
            case TriggerOnly triggerOnly -> Optional.of(triggerOnly.getTrigger());
            case ActionOnly _ -> Optional.empty();
            case Reaction reaction -> Optional.of(reaction.getTrigger());
            default -> throw unexpectedReaction(event);
        };
    }

    /**
     * @return the actions of the reaction, empty for a trigger-only reaction
     */
    public static List<Observation> actions(ReactionIdentifiedEvent event) {
        return switch (event.getPayload().getReaction()) {
            case TriggerOnly _ -> List.of();
            case ActionOnly actionOnly -> List.of(actionOnly.getAction());
            case Reaction reaction -> reaction.getActions();
            default -> throw unexpectedReaction(event);
        };
    }

    /**
     * @return the names of the HTTP requests among the actions of the reaction
     */
    public static List<String> httpRequestActionFqns(ReactionIdentifiedEvent event) {
        return actions(event).stream()
                .filter(action -> HttpRequestObservations.TYPE.equals(action.getType()))
                .map(Observation::getFqn)
                .toList();
    }

    /**
     * @return whether the reaction has an HTTP request with the name among its actions, for example
     * "partner-service GET /api/partners/{}" or the coarse "//foo.bar.admin.ch POST /{*}"
     */
    public static Predicate<ReactionIdentifiedEvent> hasHttpRequestAction(String fqn) {
        return event -> httpRequestActionFqns(event).contains(fqn);
    }

    private static IllegalStateException unexpectedReaction(ReactionIdentifiedEvent event) {
        return new IllegalStateException("Unexpected reaction payload type: " + event.getPayload().getReaction());
    }
}
