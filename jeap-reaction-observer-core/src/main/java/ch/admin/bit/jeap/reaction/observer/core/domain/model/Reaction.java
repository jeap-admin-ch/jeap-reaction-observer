package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@EqualsAndHashCode
@ToString
public class Reaction {

    private final Observation trigger;
    private final List<Observation> actions;
    private final String id;

    public Reaction(Observation trigger, List<Observation> actions) {
        if (trigger == null && (actions == null || actions.isEmpty())) {
            throw new IllegalArgumentException("Reaction must have at least an action or a trigger");
        }

        this.trigger = trigger;
        this.actions = actions == null ? List.of() : actions;

        this.id = createId();
    }

    public static Reaction actionOnly(Observation action) {
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null for action-only reaction");
        }
        return new Reaction(null, List.of(action));
    }

    public boolean isTriggerOnly() {
        return actions.isEmpty();
    }

    public boolean isActionOnly() {
        return trigger == null;
    }

    public Observation getSingleAction() {
        if (!isActionOnly()) {
            throw new IllegalStateException("cannot get single action reaction - is not action-only");
        }
        return actions.getFirst();
    }

    public String id() {
        return id;
    }

    public Observation trigger() {
        return trigger;
    }

    public List<Observation> actions() {
        return actions;
    }

    /**
     * The reactionId is the unique identifier of the reaction. It is formatted as "[&lt;trigger-id&gt;][#&lt;action-id-hash&gt;]".
     * <p>The trigger-id/action-id are the IDs of the observation for the trigger/action, and are composed as follows:
     * "&lt;type&gt;:&lt;fqn&gt;[:&lt;props_md5_hash_hexstring&gt;]". type and fqn are guaranteed to never contain a hash or colon.
     * The props_md5_hash_hexstring is the md5 hash of the string representation of the props map, with sorted keys,
     * built using key=value pairs, separated by ampersands (for example "key1=value1&key2=value2").</p>
     * <p>The action-id-hash is calculated as follows:
     * <ol>
     *     <li>All action-ids are sorted using their natural String ordering</li>
     *     <li>All action-ids are concatenated using an ampersand character</li>
     *     <li>The resulting String is hashed using MD5</li>
     * </ol>
     * </p>
     */
    private String createId() {
        if (isActionOnly()) {
            return "#" + ObservationIds.actionIdsHash(actions);
        } else if (isTriggerOnly()) {
            return trigger.id().value();
        } else {
            return trigger.id().value() + "#" + ObservationIds.actionIdsHash(actions);
        }
    }


}
