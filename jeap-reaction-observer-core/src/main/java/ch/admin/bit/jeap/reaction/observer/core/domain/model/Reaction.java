package ch.admin.bit.jeap.reaction.observer.core.domain.model;

public record Reaction(Observation trigger, Observation action) {

    /**
     * The reactionId is the unique identifier of the reaction. It is formatted as "[&lt;trigger-id&gt;][#&lt;action-id&gt;]".
     * The trigger-id/action-id are the IDs of the observations for the trigger/action, and are composed as follows:
     * "&lt;type&gt;:&lt;fqn&gt;[:&lt;props_md5_hash_hexstring&gt;]". type and fqn are guaranteed to never contain a hash or colon.
     * The props_md5_hash_hexstring is the md5 hash of the string representation of the props map, with sorted keys,
     * built using key=value pairs, separated by ampersands (for example "key1=value1&key2=value2").
     */
    public String id() {
        if (trigger == null) {
            return "#" + action.id().value();
        } else if (action == null) {
            return trigger.id().value();
        } else {
            return trigger.id().value() + "#" + action.id().value();
        }
    }
}
