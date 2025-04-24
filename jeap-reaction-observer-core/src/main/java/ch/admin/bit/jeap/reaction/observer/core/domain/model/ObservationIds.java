package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Map;
import java.util.SortedMap;

class ObservationIds {

    /**
     * The reactionId is the unique identifier of the reaction. It is formatted as "[&lt;trigger-id&gt;][#&lt;action-id&gt;]".
     * The trigger-id/action-id are the IDs of the observations for the trigger/action, and are composed as follows:
     * "&lt;type&gt;:&lt;fqn&gt;[:&lt;props_md5_hash_hexstring&gt;]". type and fqn are guaranteed to never contain a hash or colon.
     * The props_md5_hash_hexstring is the md5 hash of the string representation of the props map, with sorted keys,
     * built using key=value pairs, separated by ampersands (for example "key1=value1&key2=value2").
     */
    static ObservationId create(String type, String fqn, SortedMap<String, String> props) {
        String value = sanitize(type) + ":" + sanitize(fqn) + propsHash(props);
        return new ObservationId(value);
    }

    private static String propsHash(SortedMap<String, String> props) {
        if (props == null || props.isEmpty()) {
            return "";
        }

        StringBuilder hashedValue = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (!first) {
                hashedValue.append("&");
            }
            first = false;

            hashedValue.append(sanitize(entry.getKey()))
                    .append("=")
                    .append(sanitize(entry.getValue()));
        }

        return ":" + DigestUtils.md5Hex(hashedValue.toString());
    }

    private static String sanitize(String type) {
        return type.replace("#", "_")
                .replace(":", "_");
    }
}
