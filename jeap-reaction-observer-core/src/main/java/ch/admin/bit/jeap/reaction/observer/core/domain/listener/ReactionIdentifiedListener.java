package ch.admin.bit.jeap.reaction.observer.core.domain.listener;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

public interface ReactionIdentifiedListener {

    void onReactionIdentified(Reaction reaction);
}
