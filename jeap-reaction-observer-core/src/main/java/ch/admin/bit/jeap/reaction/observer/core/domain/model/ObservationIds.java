package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.Map;
import java.util.SortedMap;

class ObservationIds {

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
