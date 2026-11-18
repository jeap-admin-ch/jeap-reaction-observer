package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

class ObservationIds {

    private ObservationIds() {
    }

    /**
     * The trigger-id/action-id are the IDs of the observations for the trigger/action, and are composed as follows:
     * "&lt;type&gt;:&lt;fqn&gt;[:&lt;props_md5_hash_hexstring&gt;]". The type and the fqn part of the id are
     * guaranteed to never contain a hash or colon. The fqn itself may contain them, e.g. the fqn of an HTTP request.
     * The props_md5_hash_hexstring is the lowercase hex string of the  md5 hash of the string representation of the
     * props map, with sorted keys, built using key=value pairs, separated by ampersands (for example "key1=value1&key2=value2").
     * <p>
     * The fqn of an HTTP request is encoded instead of sanitized, see {@link #encodeHttpRequestFqn(String)}.
     */
    static ObservationId create(ObservationType type, String fqn, SortedMap<String, String> props) {
        String fqnPart = type == ObservationType.HTTP_REQUEST ? encodeHttpRequestFqn(fqn) : sanitize(fqn);
        String value = sanitize(type.name().toLowerCase()) + ":" + fqnPart + propsHash(props);
        return new ObservationId(value);
    }

    /**
     * Encodes the fqn of an HTTP request reversibly for its observation id. Sanitizing is not an option here:
     * ":" and "_" are both valid in a path, so "/jobs/{}:cancel" and "/jobs/{}_cancel" would get the same id.
     * Only the characters reserved in ids are encoded: "%" as "%25", ":" as "%3A", "#" as "%23" and "&amp;" as
     * "%26" (the ampersand separates the action ids before they are hashed). Everything else stays readable.
     */
    static String encodeHttpRequestFqn(String fqn) {
        return fqn.replace("%", "%25")
                .replace(":", "%3A")
                .replace("#", "%23")
                .replace("&", "%26");
    }

    /**
     * <p>The id-hash of a list of actions is calculated as follows:
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

        if (props.size() == 1) {
            Map.Entry<String, String> entry = props.firstEntry();
            return ":" + DigestUtils.md5Hex(sanitize(entry.getKey()) + "=" + sanitize(entry.getValue()));
        }

        StringBuilder concatenatedProps = concatenateSanitizedProps(props);

        return ":" + DigestUtils.md5Hex(concatenatedProps.toString());
    }

    private static StringBuilder concatenateSanitizedProps(SortedMap<String, String> props) {
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
        return concatenatedProps;
    }

    private static String sanitize(String type) {
        return type.replace("#", "_")
                .replace(":", "_");
    }
}
