package ch.admin.bit.jeap.reaction.observer.events.producer;

import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.test.ReactionEventsTestConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaEventProducerTest extends KafkaIntegrationTestBase {

    @Autowired
    private KafkaEventProducer kafkaEventProducer;
    @Autowired
    private ReactionEventsTestConsumer testConsumer;
    @MockitoBean
    private ReactionObserverService reactionObserverServiceMock;

    @Test
    void testKafkaEventProducingListener() {
        Observation trigger = new Observation(ObservationType.EVENT, "com.test.MyEvent", new TreeMap<>(Map.of("key1", "value1")));
        Observation action = new Observation(ObservationType.COMMAND, "com.test.MyCommand", new TreeMap<>());
        Reaction reaction = new Reaction(trigger, List.of(action));

        kafkaEventProducer.onReactionIdentified(reaction);

        ReactionIdentifiedEvent event = testConsumer.awaitReactionIdentifiedEventForReactionId(reaction.id());
        assertThat(event.getPayload().getReaction())
                .isInstanceOf(ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction.class);
        assertThat(event.getPublisher().getService())
                .isEqualTo("test-service-name");
        assertThat(event.getPublisher().getSystem())
                .isEqualTo("test-system-name");
        ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction reactionOnEvent =
                (ch.admin.bit.jeap.reaction.observer.event.identified.v2.Reaction) event.getPayload().getReaction();
        assertThat(reactionOnEvent.getReactionId())
                .isEqualTo(reaction.id());
        assertThat(reactionOnEvent.getTrigger().getType())
                .isEqualTo(trigger.type().name().toLowerCase());
        assertThat(reactionOnEvent.getTrigger().getFqn())
                .isEqualTo(trigger.fqn());
        assertThat(reactionOnEvent.getTrigger().getProps())
                .containsExactlyEntriesOf(Map.of("key1", "value1"));
        assertThat(reactionOnEvent.getActions().getFirst().getType())
                .isEqualTo(action.type().name().toLowerCase());
        assertThat(reactionOnEvent.getActions().getFirst().getFqn())
                .isEqualTo(action.fqn());
        assertThat(reactionOnEvent.getActions().getFirst().getProps())
                .isEmpty();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public ReactionEventsTestConsumer testConsumer() {
            return new ReactionEventsTestConsumer();
        }
    }

    @SpringBootApplication
    static class TestApp {
    }
}
