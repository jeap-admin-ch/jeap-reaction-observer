package ch.admin.bit.jeap.reaction.observer.core.domain;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The reactions an instance has identified since one point in time, the identification start. When the period
 * ends, the instance starts a new one and identifies its reactions again. A reaction is marked as identified
 * before it is reported, see {@link ReactionObserverService}.
 */
final class IdentificationPeriod {

    enum Marking {
        /**
         * The reaction was not marked before: it is to be reported.
         */
        NEWLY_MARKED,
        ALREADY_MARKED,
        /**
         * The period already has the maximum number of reactions, the reaction is not marked.
         */
        LIMIT_REACHED
    }

    private final Set<String> markedReactionIds = ConcurrentHashMap.newKeySet();
    private final Instant start;
    private final long endMillis;
    private final int maxReactionCount;
    private final AtomicBoolean limitWarningLogged = new AtomicBoolean();

    /**
     * @param start    the identification start, reported with each reaction identified in this period
     * @param endMillis the time in epoch milliseconds from which on the period is over
     */
    IdentificationPeriod(Instant start, long endMillis, int maxReactionCount) {
        this.start = start;
        this.endMillis = endMillis;
        this.maxReactionCount = maxReactionCount;
    }

    Instant start() {
        return start;
    }

    boolean isOverAt(long epochMillis) {
        return epochMillis >= endMillis;
    }

    boolean isMarked(String reactionId) {
        return markedReactionIds.contains(reactionId);
    }

    /**
     * Marks the reaction unless the period has its maximum number of reactions. Only a reaction that is to be
     * marked waits for the lock, which keeps the limit exact. The frequent cases do not: a reaction that is marked
     * already, and a further reaction once the limit is reached.
     */
    Marking mark(String reactionId) {
        // Read before the lookup, so that a reaction another thread marks as the last one in between is found
        boolean full = markedReactionIds.size() >= maxReactionCount;
        if (markedReactionIds.contains(reactionId)) {
            return Marking.ALREADY_MARKED;
        }
        if (full) {
            return Marking.LIMIT_REACHED;
        }
        synchronized (this) {
            if (markedReactionIds.contains(reactionId)) {
                return Marking.ALREADY_MARKED;
            }
            if (markedReactionIds.size() >= maxReactionCount) {
                return Marking.LIMIT_REACHED;
            }
            markedReactionIds.add(reactionId);
            return Marking.NEWLY_MARKED;
        }
    }

    void unmark(String reactionId) {
        markedReactionIds.remove(reactionId);
    }

    int markedReactionCount() {
        return markedReactionIds.size();
    }

    /**
     * @return true for the first caller in this period only
     */
    boolean claimLimitWarning() {
        return limitWarningLogged.compareAndSet(false, true);
    }
}
