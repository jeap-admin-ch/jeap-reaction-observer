package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaEventProducingListenerTest extends KafkaIntegrationTestBase {

    @Autowired
    private KafkaEventProducingListener kafkaEventProducingListener;
    @Autowired
    private TestConsumer testConsumer;

    @Test
    void testKafkaEventProducingListener() {
        Observation trigger = new Observation("Event", "com.test.MyEvent", new TreeMap<>(Map.of("key1", "value1")));
        Observation action = new Observation("Command", "com.test.MyCommand", new TreeMap<>());
        Reaction reaction = new Reaction(trigger, action);

        kafkaEventProducingListener.onReactionIdentified(reaction);

        ReactionIdentifiedEvent event = testConsumer.awaitReactionIdentifiedEventForReaction(reaction);
        assertThat(event.getPayload().getReactionId())
                .isEqualTo(reaction.id());
        assertThat(event.getPayload().getReaction())
                .isInstanceOf(ch.admin.bit.jeap.reaction.observer.event.identified.Reaction.class);
        ch.admin.bit.jeap.reaction.observer.event.identified.Reaction reactionOnEvent =
                (ch.admin.bit.jeap.reaction.observer.event.identified.Reaction) event.getPayload().getReaction();
        assertThat(reactionOnEvent.getTrigger().getType())
                .isEqualTo(trigger.type());
        assertThat(reactionOnEvent.getTrigger().getFqn())
                .isEqualTo(trigger.fqn());
        assertThat(reactionOnEvent.getTrigger().getProps())
                .containsExactlyEntriesOf(Map.of("key1", "value1"));
        assertThat(reactionOnEvent.getAction().getType())
                .isEqualTo(action.type());
        assertThat(reactionOnEvent.getAction().getFqn())
                .isEqualTo(action.fqn());
        assertThat(reactionOnEvent.getAction().getProps())
                .isEmpty();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public TestConsumer testConsumer() {
            return new TestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
