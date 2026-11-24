package ch.admin.bit.jeap.reaction.observer.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        ReactionObserverReidentificationITBase.SHORTEST_REIDENTIFICATION_PERIOD,
        "jeap.reaction.observer.model-version=2"})
@Import(ReactionObserverReidentificationITBase.ReidentificationTestConfiguration.class)
class ReactionObserverReidentificationModelVersion2IT extends ReactionObserverReidentificationITBase {

    @Override
    int reactionModelVersion() {
        return 2;
    }

    @SpringBootApplication
    static class TestApp {
    }
}
