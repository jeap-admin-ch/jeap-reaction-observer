package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ReactionObserverService {

    private final ReactionIdentifiedListener reactionIdentifiedListener;
    private final Set<String> identifiedReactions = ConcurrentHashMap.newKeySet();

    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener) {
        this.reactionIdentifiedListener = reactionIdentifiedListener;
    }

    public void reactionObserved(Reaction reaction) {
        if (identifiedReactions.add(reaction.id())) {
            reactionIdentifiedListener.onReactionIdentified(reaction);
        }
    }
}
