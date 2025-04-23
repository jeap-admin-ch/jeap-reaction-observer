package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.*;

class ReactionObserverServiceTest {

    @Test
    void reactionObservedNotifiesListenerForNewReaction() {
        ReactionIdentifiedListener listener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(listener);
        Reaction reaction = new Reaction(
                new Observation("type", "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);

        verify(listener, times(1)).onReactionIdentified(reaction);
    }

    @Test
    void reactionObservedDoesNotNotifyListenerForDuplicateReaction() {
        ReactionIdentifiedListener listener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(listener);
        Reaction reaction = new Reaction(
                new Observation("type", "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(listener, times(1)).onReactionIdentified(reaction);
    }
}
