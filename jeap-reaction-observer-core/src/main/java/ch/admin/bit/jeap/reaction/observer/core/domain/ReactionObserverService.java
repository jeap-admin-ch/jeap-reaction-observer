package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ReactionObserverService implements AutoCloseable {

    static final int MAX_REACTION_COUNT = 4096; // Maximum number of different reactions to track, limited to avoid memory issues
    static final String IDENTIFICATION_THREAD_NAME = "jeap-reaction-observer-identification";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IDLE_THREAD_KEEP_ALIVE = Duration.ofSeconds(60);
    private static boolean warningLogged = false;

    private final ReactionIdentifiedListener reactionIdentifiedListener;
    private final Executor identificationExecutor;
    private final boolean ownsIdentificationExecutor;
    private final AtomicBoolean notificationFailureLogged = new AtomicBoolean();

    private final AtomicReference<Map<String, AtomicInteger>> countByReactionId = new AtomicReference<>(new ConcurrentHashMap<>());
    private final Set<String> identifiedReactions = ConcurrentHashMap.newKeySet();

    /**
     * Notifies the listener about newly identified reactions in a thread of its own, so that a slow listener (e.g. a
     * Kafka broker that is slow or unreachable) does not delay the observed code.
     */
    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener) {
        this(reactionIdentifiedListener, newIdentificationExecutor(), true);
    }

    /**
     * @param identificationExecutor runs the notifications of the listener about newly identified reactions, e.g.
     *                               {@code Runnable::run} in tests to see them right away. {@link #close()} does
     *                               not shut it down.
     */
    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener, Executor identificationExecutor) {
        this(reactionIdentifiedListener, identificationExecutor, false);
    }

    private ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener, Executor identificationExecutor,
                                    boolean ownsIdentificationExecutor) {
        this.reactionIdentifiedListener = reactionIdentifiedListener;
        this.identificationExecutor = identificationExecutor;
        this.ownsIdentificationExecutor = ownsIdentificationExecutor;
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
            try {
                identificationExecutor.execute(() -> notifyReactionIdentified(reaction));
            } catch (RuntimeException e) {
                // Rejected (queue full, shut down) or failed hand-off: not marked as identified, so that the
                // reaction is identified again when it occurs again
                identifiedReactions.remove(reaction.id());
                logNotificationFailure(reaction, e);
            }
        }
    }

    private void notifyReactionIdentified(Reaction reaction) {
        try {
            reactionIdentifiedListener.onReactionIdentified(reaction);
            notificationFailureLogged.set(false);
        } catch (Exception e) {
            // Not marked as identified, so that the reaction is identified again when it occurs again
            identifiedReactions.remove(reaction.id());
            logNotificationFailure(reaction, e);
        }
    }

    /**
     * A failure that occurs right away (e.g. a missing authorization for the topic) recurs with every occurrence of
     * the reaction: only the first failure after a successful notification is logged as a warning.
     */
    private void logNotificationFailure(Reaction reaction, Exception e) {
        if (notificationFailureLogged.compareAndSet(false, true)) {
            log.warn("Error reporting identified reaction, will be retried when it occurs again. Further errors are " +
                    "logged at debug level until a report succeeds: {}", reaction, e);
        } else {
            log.debug("Error reporting identified reaction, will be retried when it occurs again: {}", reaction, e);
        }
    }

    public Map<String, AtomicInteger> getAndClearCountByReactionId() {
        return countByReactionId.getAndSet(new ConcurrentHashMap<>());
    }

    private void countReaction(Reaction reaction) {
        Map<String, AtomicInteger> counts = countByReactionId.get();
        if (counts.size() >= MAX_REACTION_COUNT) {
            logLimitWarningOnce();
            return;
        }

        counts
                .computeIfAbsent(reaction.id(), k -> new AtomicInteger())
                .incrementAndGet();
    }

    /**
     * Lets the pending identifications finish for a short while, then abandons them. An abandoned identification is
     * reported by a later run of the service instance when the reaction occurs there. Shuts down only the executor
     * the service created itself.
     */
    @Override
    public void close() {
        if (ownsIdentificationExecutor && identificationExecutor instanceof ExecutorService executorService) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * A single thread reports the identifications one after the other, and ends when idle. Its queue practically
     * cannot overflow: only a newly identified reaction is queued, and there are about {@link #MAX_REACTION_COUNT}
     * of them at most. A rejected hand-off is retried when the reaction occurs again.
     */
    private static ExecutorService newIdentificationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, IDLE_THREAD_KEEP_ALIVE.toMillis(), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_REACTION_COUNT),
                Thread.ofPlatform().name(IDENTIFICATION_THREAD_NAME).daemon().factory());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static void logLimitWarningOnce() {
        if (!warningLogged) {
            log.warn("Maximum reaction count reached ({}). Not identifying new reactions.", MAX_REACTION_COUNT);
            warningLogged = true; // Log this warning only once
        }
    }
}
