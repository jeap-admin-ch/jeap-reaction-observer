package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics.DroppedExecutionReason;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class ReactionObserverService implements AutoCloseable {

    /**
     * The maximum number of different reactions an instance identifies in one identification period, and the
     * maximum number of different reactions it counts between two calls of
     * {@link #getAndClearCountByReactionId()}. Limited to avoid memory issues, and to keep the event with the
     * counts from growing: it has one entry per counted reaction.
     * <p>
     * An instance that observes more different reactions than that cannot observe and count all of them
     * reliably anyway. What does not fit is dropped and shown in the metrics, without further effort for an
     * accuracy that is not given beyond the limit.
     */
    public static final int MAX_REACTION_COUNT = 4096;
    public static final Duration DEFAULT_REIDENTIFICATION_PERIOD = Duration.ofHours(24);
    /**
     * The identification start is reported to the second, and the first period of an instance lasts at least
     * half of the reidentification period: two periods of an instance never start within the same second.
     */
    public static final Duration MIN_REIDENTIFICATION_PERIOD = Duration.ofSeconds(2);
    /**
     * The reidentification is wanted and cannot be switched off, not by a period that practically never ends
     * either: a lost identification is made up for after a week at the latest.
     */
    public static final Duration MAX_REIDENTIFICATION_PERIOD = Duration.ofDays(7);
    static final String IDENTIFICATION_THREAD_NAME = "jeap-reaction-observer-identification";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IDLE_THREAD_KEEP_ALIVE = Duration.ofSeconds(60);

    private final ReactionIdentifiedListener reactionIdentifiedListener;
    private final Executor identificationExecutor;
    private final boolean ownsIdentificationExecutor;
    private final long reidentificationPeriodMillis;
    private final Clock clock;
    private final ReactionObserverMetrics metrics;
    private final AtomicBoolean notificationFailureLogged = new AtomicBoolean();

    /**
     * Counting holds the read lock, fetching the counts the write lock: an execution is counted either in the
     * counts that are fetched or in the following ones, never in counts that were fetched before.
     */
    private final ReadWriteLock countsLock = new ReentrantReadWriteLock();
    /**
     * Read with the read lock of {@link #countsLock}, replaced with its write lock.
     */
    private Map<String, AtomicInteger> countByReactionId = new ConcurrentHashMap<>();
    private final Object newCountLock = new Object();
    private final AtomicReference<IdentificationPeriod> identificationPeriod;

    /**
     * Notifies the listener about newly identified reactions in a thread of its own, so that a slow listener (e.g. a
     * Kafka broker that is slow or unreachable) does not delay the observed code.
     *
     * @param reidentificationPeriod how long the service remembers which reactions it has identified. After that
     *                               it identifies each reaction again when it occurs. The first period of an
     *                               instance lasts a random 50 to 100 percent of it, so that the instances of a
     *                               service that started together do not identify their reactions again all at
     *                               once. At least {@link #MIN_REIDENTIFICATION_PERIOD} and at most
     *                               {@link #MAX_REIDENTIFICATION_PERIOD}
     * @param metrics                counts the executions that are not counted. Its
     *                               {@link ReactionObserverMetrics#identifiedReactions} is to be called by the
     *                               creator of the service
     */
    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener, Duration reidentificationPeriod,
                                   Clock clock, ReactionObserverMetrics metrics) {
        this(reactionIdentifiedListener, newIdentificationExecutor(), true, validated(reidentificationPeriod),
                randomFirstPeriod(reidentificationPeriod), clock, metrics);
    }

    /**
     * @param identificationExecutor runs the notifications of the listener about newly identified reactions, e.g.
     *                               {@code Runnable::run} in tests to see them right away. {@link #close()} does
     *                               not shut it down.
     */
    public ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener, Executor identificationExecutor) {
        this(reactionIdentifiedListener, identificationExecutor, false, DEFAULT_REIDENTIFICATION_PERIOD,
                DEFAULT_REIDENTIFICATION_PERIOD, Clock.systemUTC(), ReactionObserverMetrics.NONE);
    }

    /**
     * @param firstPeriod how long the first identification period lasts, the following ones last the
     *                    reidentification period
     */
    ReactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener, Executor identificationExecutor,
                            boolean ownsIdentificationExecutor, Duration reidentificationPeriod, Duration firstPeriod,
                            Clock clock, ReactionObserverMetrics metrics) {
        this.reactionIdentifiedListener = reactionIdentifiedListener;
        this.identificationExecutor = identificationExecutor;
        this.ownsIdentificationExecutor = ownsIdentificationExecutor;
        this.reidentificationPeriodMillis = reidentificationPeriod.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.identificationPeriod = new AtomicReference<>(newIdentificationPeriod(clock.millis(), firstPeriod.toMillis()));
    }

    public void reactionObserved(Reaction reaction) {
        log.trace("Reaction observed: {}", reaction);

        try {
            // The period is determined once: the reaction is marked and checked in the same period, also if the
            // period ends in between. An execution whose reaction is identified in it is counted
            if (identifyReaction(reaction, currentIdentificationPeriod())) {
                countReaction(reaction);
            }
        } catch (Exception e) {
            // Log the error but do not throw it, as this is a best-effort service that should not impact the business logic
            log.warn("Error observing reaction: {}", reaction, e);
        }
    }

    /**
     * @return the number of reactions the instance has identified in the current identification period
     */
    public int identifiedReactionCount() {
        IdentificationPeriod period = identificationPeriod.get();
        // A period that is over is replaced when the next reaction is observed: nothing is identified until then
        return period.isOverAt(clock.millis()) ? 0 : period.markedReactionCount();
    }

    /**
     * Replacing the period is what forgets the identified reactions, all at once. A reaction is not identified
     * again when the period starts, but when it occurs the next time.
     */
    private IdentificationPeriod currentIdentificationPeriod() {
        IdentificationPeriod current = identificationPeriod.get();
        long now = clock.millis();
        if (!current.isOverAt(now)) {
            return current;
        }
        IdentificationPeriod next = newIdentificationPeriod(now, reidentificationPeriodMillis);
        // Another thread may have started the next period in the meantime: then that one is the current period
        return identificationPeriod.compareAndSet(current, next) ? next : identificationPeriod.get();
    }

    /**
     * @return whether the reaction is identified in the period. Only then its execution is counted: a Reaction
     * Observer Service does not store the count of a reaction it does not know
     */
    private boolean identifyReaction(Reaction reaction, IdentificationPeriod period) {
        String reactionId = reaction.id();
        IdentificationPeriod.Marking marking = period.mark(reactionId);
        if (marking == IdentificationPeriod.Marking.LIMIT_REACHED) {
            logLimitWarningOncePerPeriod(period);
            metrics.droppedExecution(DroppedExecutionReason.REACTION_LIMIT);
            return false;
        }
        if (marking == IdentificationPeriod.Marking.NEWLY_MARKED) {
            handOverIdentification(reaction, period);
            // The hand-off or, with an executor that runs in the observing thread, the report may have failed
            if (!period.isMarked(reactionId)) {
                metrics.droppedExecution(DroppedExecutionReason.NOT_IDENTIFIED);
                return false;
            }
        }
        return true;
    }

    private void handOverIdentification(Reaction reaction, IdentificationPeriod period) {
        log.trace("New reaction identified: {}", reaction);
        try {
            identificationExecutor.execute(() -> notifyReactionIdentified(reaction, period));
        } catch (RuntimeException e) {
            // Rejected (queue full, shut down) or failed hand-off: not marked as identified, so that the
            // reaction is identified again when it occurs again
            period.unmark(reaction.id());
            logNotificationFailure(reaction, e);
        }
    }

    /**
     * Reports the reaction with the start of the period it was marked in, and unmarks it there if the report
     * fails - also if that period is over by now: the reactions of the current period are left alone.
     */
    private void notifyReactionIdentified(Reaction reaction, IdentificationPeriod period) {
        try {
            reactionIdentifiedListener.onReactionIdentified(reaction, period.start());
            notificationFailureLogged.set(false);
        } catch (Exception | LinkageError e) {
            // Not marked as identified, so that the reaction is identified again when it occurs again.
            // A LinkageError is caught as well, e.g. the AbstractMethodError of a listener from a module of
            // another version of this library. Uncaught, it would end the identification thread with nothing
            // but a stack trace on the standard error stream, not in the log, and the reaction would stay
            // marked and counted. The case is hypothetical: the modules of the library are released together,
            // and the jEAP parent manages their versions with a single property (jeap-reaction-observer.version).
            period.unmark(reaction.id());
            logNotificationFailure(reaction, e);
        }
    }

    /**
     * A failure that occurs right away (e.g. a missing authorization for the topic) recurs with every occurrence of
     * the reaction: only the first failure after a successful notification is logged as a warning.
     */
    private void logNotificationFailure(Reaction reaction, Throwable e) {
        if (notificationFailureLogged.compareAndSet(false, true)) {
            log.warn("Error reporting identified reaction, will be retried when it occurs again. Further errors are " +
                    "logged at debug level until a report succeeds: {}", reaction, e);
        } else {
            log.debug("Error reporting identified reaction, will be retried when it occurs again: {}", reaction, e);
        }
    }

    /**
     * Waits for the executions that are being counted at this moment, which takes no longer than counting them:
     * the counts returned do not change any more.
     */
    public Map<String, AtomicInteger> getAndClearCountByReactionId() {
        countsLock.writeLock().lock();
        try {
            Map<String, AtomicInteger> counts = countByReactionId;
            countByReactionId = new ConcurrentHashMap<>();
            return counts;
        } finally {
            countsLock.writeLock().unlock();
        }
    }

    /**
     * The counts are those of {@link #MAX_REACTION_COUNT} different reactions at most. The limit only keeps
     * further reactions from being counted, a reaction that is counted already is always counted.
     * <p>
     * Only identified reactions are counted, so an instance that observes no more different reactions than the
     * limit never reaches it. One that observes more can, without reaching the limit of a period: when a period
     * ends since the counts were fetched, the counts take the reactions of both periods. The same goes for a
     * reidentification period shorter than the interval of the counts, and if nobody fetches the counts. The
     * executions dropped then are not made up for.
     * <p>
     * Counting threads share the read lock and do not wait for each other. They only wait while the counts are
     * fetched, see {@link #getAndClearCountByReactionId()}.
     */
    private void countReaction(Reaction reaction) {
        boolean counted;
        countsLock.readLock().lock();
        try {
            counted = count(countByReactionId, reaction.id());
        } finally {
            countsLock.readLock().unlock();
        }
        if (!counted) {
            metrics.droppedExecution(DroppedExecutionReason.COUNT_LIMIT);
        }
    }

    /**
     * The frequent cases do not wait for the lock of new counts: a reaction that is counted already, and a
     * further reaction once the limit is reached.
     *
     * @return whether the execution is counted, false if the counts have no room for a further reaction
     */
    private boolean count(Map<String, AtomicInteger> counts, String reactionId) {
        // Read before the lookup, so that a reaction another thread adds as the last one in between is found
        boolean full = counts.size() >= MAX_REACTION_COUNT;
        AtomicInteger count = counts.get(reactionId);
        if (count == null && !full) {
            count = addCount(counts, reactionId);
        }
        if (count == null) {
            return false;
        }
        count.incrementAndGet();
        return true;
    }

    /**
     * Only a reaction that is to be added waits for the lock, which keeps the limit exact.
     *
     * @return the count of the reaction, or null if the counts have no room for a further reaction
     */
    private AtomicInteger addCount(Map<String, AtomicInteger> counts, String reactionId) {
        synchronized (newCountLock) {
            AtomicInteger count = counts.get(reactionId);
            if (count == null && counts.size() < MAX_REACTION_COUNT) {
                count = new AtomicInteger();
                counts.put(reactionId, count);
            }
            return count;
        }
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
     * A single thread reports the identifications one after the other, and ends when idle. Only a newly identified
     * reaction is queued, and there are {@link #MAX_REACTION_COUNT} of them at most in an identification period:
     * the queue cannot overflow with the reactions of one period. It can when a period starts while the reports
     * of the period before are still waiting, for example because the broker is unreachable. A rejected hand-off
     * is retried when the reaction occurs again, its executions are not counted until then.
     */
    private static ExecutorService newIdentificationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, IDLE_THREAD_KEEP_ALIVE.toMillis(), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_REACTION_COUNT),
                Thread.ofPlatform().name(IDENTIFICATION_THREAD_NAME).daemon().factory());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private IdentificationPeriod newIdentificationPeriod(long startMillis, long durationMillis) {
        return new IdentificationPeriod(Instant.ofEpochMilli(startMillis), startMillis + durationMillis, MAX_REACTION_COUNT);
    }

    private static Duration validated(Duration reidentificationPeriod) {
        if (reidentificationPeriod.compareTo(MIN_REIDENTIFICATION_PERIOD) < 0 ||
                reidentificationPeriod.compareTo(MAX_REIDENTIFICATION_PERIOD) > 0) {
            throw new IllegalArgumentException("The reidentification period has to be at least " +
                    MIN_REIDENTIFICATION_PERIOD + " and at most " + MAX_REIDENTIFICATION_PERIOD + ", but is " +
                    reidentificationPeriod);
        }
        return reidentificationPeriod;
    }

    /**
     * @return a random 50 to 100 percent of the period
     */
    static Duration randomFirstPeriod(Duration reidentificationPeriod) {
        long periodMillis = reidentificationPeriod.toMillis();
        long halfPeriodMillis = periodMillis / 2;
        long randomShareMillis = ThreadLocalRandom.current().nextLong(periodMillis - halfPeriodMillis + 1); // NOSONAR java:S2245 not security-relevant: only spreads the reidentifications of instances over time
        return Duration.ofMillis(halfPeriodMillis + randomShareMillis);
    }

    private static void logLimitWarningOncePerPeriod(IdentificationPeriod period) {
        if (period.claimLimitWarning()) {
            log.warn("Maximum reaction count reached ({}). Neither identifying nor counting further reactions until " +
                    "the reactions are identified again, see jeap.reaction.observer.reidentification-period.", MAX_REACTION_COUNT);
        }
    }
}
