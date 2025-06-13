package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReactionObserverService {

    static final int MAX_REACTION_COUNT = 4096; // Maximum number of different reactions to track, limited to avoid memory issues
    private static boolean warningLogged = false;

    private final ReactionIdentifiedListener reactionIdentifiedListener;

    private volatile Map<String, AtomicInteger> countByReactionId = new ConcurrentHashMap<>();
    private final Set<String> identifiedReactions = ConcurrentHashMap.newKeySet();

    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener) {
        this.reactionIdentifiedListener = reactionIdentifiedListener;
    }

    public void reactionObserved(Reaction reaction) {
        log.trace("Reaction observed: {}", reaction);

        try {
            identifyReaction(reaction);
            countReaction(reaction);
        } catch (Exception e) {
            // Log the error but do not throw it, as this is a best-effort service that should not impact the business logic
            log.warn("Error observing reaction: {}", reaction, e);
        }
    }

    private void identifyReaction(Reaction reaction) {
        if (identifiedReactions.size() >= MAX_REACTION_COUNT) {
            logLimitWarningOnce();
            return;
        }

        if (identifiedReactions.add(reaction.id())) {
            log.trace("New reaction identified: {}", reaction);
            reactionIdentifiedListener.onReactionIdentified(reaction);
        }
    }

    public Map<String, AtomicInteger> getAndClearCountByReactionId() {
        Map<String, AtomicInteger> counts = countByReactionId;
        countByReactionId = new ConcurrentHashMap<>();
        return counts;
    }

    private void countReaction(Reaction reaction) {
        if (countByReactionId.size() >= MAX_REACTION_COUNT) {
            logLimitWarningOnce();
            return;
        }

        countByReactionId
                .computeIfAbsent(reaction.id(), k -> new AtomicInteger())
                .incrementAndGet();
    }

    private static void logLimitWarningOnce() {
        if (!warningLogged) {
            log.warn("Maximum reaction count reached ({}). Not identifying new reactions.", MAX_REACTION_COUNT);
            warningLogged = true; // Log this warning only once
        }
    }
}
