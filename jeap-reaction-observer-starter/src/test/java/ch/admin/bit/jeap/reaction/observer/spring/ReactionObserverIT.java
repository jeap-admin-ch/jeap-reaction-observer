package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContract;
import ch.admin.bit.jeap.messaging.kafka.test.KafkaIntegrationTestBase;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
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

@SpringBootTest
// TODO Remove producer annotation when latest messaging version has been published
@JeapMessageProducerContract(appName = "test", topic = "reaction-identified", value = ReactionIdentifiedEvent.TypeRef.class)
class ReactionObserverIT extends KafkaIntegrationTestBase {

    @Autowired
    private ReactionObserverService reactionObserverService;
    @Autowired
    private TestConsumer testConsumer;

    @Test
    void reactionObserved() {
        Reaction reaction = new Reaction(
                new Observation("type", "fqn", new TreeMap<>(Map.of("key", "value"))),
                new Observation("type", "fqn", null)
        );

        reactionObserverService.reactionObserved(reaction);

        testConsumer.awaitReactionIdentifiedEventForReaction(reaction);
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
