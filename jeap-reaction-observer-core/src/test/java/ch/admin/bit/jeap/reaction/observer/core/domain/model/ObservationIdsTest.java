package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservationIdsTest {

    @Test
    void createGeneratesCorrectObservationIdForValidInputs() {
        SortedMap<String, String> props = new TreeMap<>(Map.of("key1", "value1", "key2", "value2"));
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", props);
        String expected = "event:fqn:" + DigestUtils.md5Hex("key1=value1&key2=value2");
        assertEquals(expected, result.value());
    }

    @Test
    void createHandlesNullPropsGracefully() {
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", null);
        String expected = "event:fqn";
        assertEquals(expected, result.value());
    }

    @Test
    void createHandlesEmptyPropsGracefully() {
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", new TreeMap<>());
        String expected = "event:fqn";
        assertEquals(expected, result.value());
    }

    @Test
    void createSanitizesTypeAndFqnCorrectly() {
        SortedMap<String, String> props = new TreeMap<>(Map.of("key", "value"));
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fq:n", props);
        String expected = "event:fq_n:" + DigestUtils.md5Hex("key=value");
        assertEquals(expected, result.value());
    }

    @Test
    void createSanitizesPropsKeysAndValuesCorrectly() {
        SortedMap<String, String> props = new TreeMap<>(Map.of("ke:y#", "val#ue:"));
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", props);
        String expected = "event:fqn:" + DigestUtils.md5Hex("ke_y_=val_ue_");
        assertEquals(expected, result.value());
    }
}
