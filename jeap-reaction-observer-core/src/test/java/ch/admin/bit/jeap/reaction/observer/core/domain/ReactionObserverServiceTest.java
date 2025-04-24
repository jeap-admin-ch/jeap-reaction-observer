package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReactionObserverServiceTest {

    @Test
    void reactionObservedNotifiesListenerForNewReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener);
        Reaction reaction = new Reaction(
                new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
    }

    @Test
    void reactionObservedDoesNotNotifyListenerForDuplicateReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener);
        Reaction reaction = new Reaction(
                new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
    }

    @Test
    void getAndClearCountByReactionIdRetrievesAndClearsCounts() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener);

        Reaction reaction1 = new Reaction(
                new Observation(ObservationType.EVENT, "fqn1", new TreeMap<>(Map.of("key1", "value1"))),
                null
        );
        Reaction reaction2 = new Reaction(
                new Observation(ObservationType.EVENT, "fqn2", new TreeMap<>(Map.of("key2", "value2"))),
                null
        );

        // Simulate observing reactions
        service.reactionObserved(reaction1);
        service.reactionObserved(reaction2);
        service.reactionObserved(reaction1); // Duplicate reaction to increment count

        // Retrieve and clear counts
        Map<String, AtomicInteger> counts = service.getAndClearCountByReactionId();

        // Verify counts by comparing AtomicInteger values
        assertThat(counts)
                .hasSize(2)
                .extractingByKey(reaction1.id()).extracting(AtomicInteger::get).isEqualTo(2);
        assertThat(counts)
                .extractingByKey(reaction2.id()).extracting(AtomicInteger::get).isEqualTo(1);

        // Verify that counts are cleared
        Map<String, AtomicInteger> clearedCounts = service.getAndClearCountByReactionId();
        assertThat(clearedCounts).isEmpty();
    }
}
