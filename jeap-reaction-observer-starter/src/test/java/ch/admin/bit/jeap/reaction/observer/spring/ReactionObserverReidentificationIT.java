package ch.admin.bit.jeap.reaction.observer.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The reaction model version of a service that does not choose one, 1: the Reaction Observer Service gets the
 * events of the reidentification as it got them before, with reaction model version 1.
 */
@SpringBootTest(properties = {
        ReactionObserverReidentificationITBase.SHORTEST_REIDENTIFICATION_PERIOD,
        "jeap.reaction.observer.model-version=1"})
@Import(ReactionObserverReidentificationITBase.ReidentificationTestConfiguration.class)
class ReactionObserverReidentificationIT extends ReactionObserverReidentificationITBase {

    @Override
    int reactionModelVersion() {
        return 1;
    }

    @SpringBootApplication
    static class TestApp {
    }
}
