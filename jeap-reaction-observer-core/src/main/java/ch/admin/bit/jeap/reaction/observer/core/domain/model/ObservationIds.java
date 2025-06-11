package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

class ObservationIds {

    /**
     * The trigger-id/action-id are the IDs of the observations for the trigger/action, and are composed as follows:
     * "&lt;type&gt;:&lt;fqn&gt;[:&lt;props_md5_hash_hexstring&gt;]". type and fqn are guaranteed to never contain a hash or colon.
     * The props_md5_hash_hexstring is the lowercase hex string of the  md5 hash of the string representation of the
     * props map, with sorted keys, built using key=value pairs, separated by ampersands (for example "key1=value1&key2=value2").
     */
    static ObservationId create(ObservationType type, String fqn, SortedMap<String, String> props) {
        String value = sanitize(type.name().toLowerCase()) + ":" + sanitize(fqn) + propsHash(props);
        return new ObservationId(value);
    }

    /**
     * <p>The action-id-hash is calculated as follows:
     * <ol>
     *     <li>All action-ids are sorted using their natural String ordering</li>
     *     <li>All action-ids are concatenated using an ampersand character</li>
     *     <li>The resulting String is hashed using MD5</li>
     *     <li>The MD5 hash is returned as a lowercase hex string</li>
     * </ol>
     * </p>
     */
    static String actionIdsHash(List<Observation> actions) {
        String actionIds = actions.stream()
                .map(observation -> observation.id().value())
                .sorted()
                .collect(Collectors.joining("&"));
        return DigestUtils.md5Hex(actionIds);
    }

    private static String propsHash(SortedMap<String, String> props) {
        if (props == null || props.isEmpty()) {
            return "";
        }

        StringBuilder concatenatedProps = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (!first) {
                concatenatedProps.append("&");
            }
            first = false;

            concatenatedProps.append(sanitize(entry.getKey()))
                    .append("=")
                    .append(sanitize(entry.getValue()));
        }

        return ":" + DigestUtils.md5Hex(concatenatedProps.toString());
    }

    private static String sanitize(String type) {
        return type.replace("#", "_")
                .replace(":", "_");
    }
}
