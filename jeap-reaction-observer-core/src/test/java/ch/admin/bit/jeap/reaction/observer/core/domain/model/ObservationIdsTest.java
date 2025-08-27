package ch.admin.bit.jeap.reaction.observer.core.domain.model;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservationIdsTest {

    @Test
    void createGeneratesCorrectObservationIdWithMultipleProps() {
        SortedMap<String, String> props = new TreeMap<>(Map.of("key1", "value1", "key2", "value2"));
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", props);
        String expected = "event:fqn:" + DigestUtils.md5Hex("key1=value1&key2=value2");
        assertEquals(expected, result.value());
    }

    @Test
    void createGeneratesCorrectObservationIdWithSingleProp() {
        SortedMap<String, String> props = new TreeMap<>(Map.of("key1", "value1"));
        ObservationId result = ObservationIds.create(ObservationType.EVENT, "fqn", props);
        String expected = "event:fqn:" + DigestUtils.md5Hex("key1=value1");
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

    @Test
    void ofCommandWithVariantGeneratesCorrectObservationId() {
        Observation result = Observation.ofCommand("MessageType", "variant1", "topicName");
        String expectedFqn = "MessageType/variant1";
        String expectedId = "command:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(ObservationType.COMMAND, result.type());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void ofCommandWithNullVariantGeneratesCorrectObservationId() {
        Observation result = Observation.ofCommand("MessageType", null, "topicName");
        String expectedFqn = "MessageType";
        String expectedId = "command:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(ObservationType.COMMAND, result.type());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void ofCommandWithBlankVariantGeneratesCorrectObservationId() {
        Observation result = Observation.ofCommand("MessageType", "", "topicName");
        String expectedFqn = "MessageType";
        String expectedId = "command:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(ObservationType.COMMAND, result.type());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void ofEventWithVariantGeneratesCorrectObservationId() {
        Observation result = Observation.ofEvent("MessageType", "variant1", "topicName");
        String expectedFqn = "MessageType/variant1";
        String expectedId = "event:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(ObservationType.EVENT, result.type());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void ofEventWithNullVariantGeneratesCorrectObservationId() {
        Observation result = Observation.ofEvent("MessageType", null, "topicName");
        String expectedFqn = "MessageType";
        String expectedId = "event:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(ObservationType.EVENT, result.type());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void observationSanitizesSlashesInVariant() {
        Observation result = Observation.ofEvent("Message/Type", "variant/1", "topicName");
        String expectedFqn = "Message/Type/variant_1";
        String expectedId = "event:" + expectedFqn + ":" + DigestUtils.md5Hex("topic=topicName");
        assertEquals(expectedId, result.id().value());
        assertEquals(expectedFqn, result.fqn());
    }

    @Test
    void observationHandlesComplexVariantScenarios() {
        // Test with whitespace variant
        Observation result1 = Observation.ofCommand("MessageType", "   ", "topicName");
        String expectedFqn1 = "MessageType";
        assertEquals(expectedFqn1, result1.fqn());

        // Test with variant containing multiple slashes
        Observation result2 = Observation.ofEvent("Message/Type", "var/i/ant", "topicName");
        String expectedFqn2 = "Message/Type/var_i_ant";
        assertEquals(expectedFqn2, result2.fqn());
    }
}
