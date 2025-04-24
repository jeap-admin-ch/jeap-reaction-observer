package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionTest {

    @Test
    void idReturnsTriggerIdWhenActionIsNullWithValidTrigger() {
        Observation trigger = new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value")));
        Reaction reaction = new Reaction(trigger, null);
        assertEquals("event:fqn:" + DigestUtils.md5Hex("key=value"), reaction.id());
    }

    @Test
    void idReturnsActionIdWhenTriggerIsNullWithValidAction() {
        Observation action = new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value")));
        Reaction reaction = new Reaction(null, action);
        assertEquals("#event:fqn:" + DigestUtils.md5Hex("key=value"), reaction.id());
    }

    @Test
    void idReturnsConcatenatedIdsWhenBothTriggerAndActionArePresentWithValidProps() {
        Observation trigger = new Observation(ObservationType.EVENT, "fqn1", new TreeMap<>(Map.of("key1", "value1")));
        Observation action = new Observation(ObservationType.COMMAND, "fqn2", new TreeMap<>(Map.of("key2", "value2")));
        Reaction reaction = new Reaction(trigger, action);
        assertEquals(
                "event:fqn1:" + DigestUtils.md5Hex("key1=value1") + "#" + "command:fqn2:" + DigestUtils.md5Hex("key2=value2"),
                reaction.id()
        );
    }

    @Test
    void idHandlesEmptyPropsGracefullyForTriggerAndAction() {
        Observation trigger = new Observation(ObservationType.EVENT, "fqn1", new TreeMap<>());
        Observation action = new Observation(ObservationType.COMMAND, "fqn2", new TreeMap<>());
        Reaction reaction = new Reaction(trigger, action);
        assertEquals("event:fqn1" + "#" + "command:fqn2", reaction.id());
    }

    @Test
    void idHandlesNullPropsGracefullyForTriggerAndAction() {
        Observation trigger = new Observation(ObservationType.EVENT, "fqn1", null);
        Observation action = new Observation(ObservationType.COMMAND, "fqn2", null);
        Reaction reaction = new Reaction(trigger, action);
        assertEquals("event:fqn1" + "#" + "command:fqn2", reaction.id());
    }
}
