package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics.DroppedExecutionReason;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

class ReactionObserverServiceTest {

    private static final Duration PERIOD = Duration.ofHours(1);
    private static final Instant START = Instant.parse("2026-11-24T10:00:00Z");

    private final AdjustableClock clock = new AdjustableClock(START);
    private final RecordedMetrics metrics = new RecordedMetrics();

    @Test
    void reactionObservedNotifiesListenerForNewReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);
        Reaction reaction = new Reaction(
                new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);

        verify(identifiedListener, times(1)).onReactionIdentified(eq(reaction), any());
    }

    @Test
    void reactionObservedNotifiesListenerForDifferentVariants() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);
        Reaction reaction = new Reaction(
                Observation.ofEvent("type1", "topic1"),
                null
        );
        Reaction reactionOtherVariant = new Reaction(
                Observation.ofEvent("type1", "variant1", "topic1"),
                null
        );

        service.reactionObserved(reaction);
        service.reactionObserved(reactionOtherVariant);

        verify(identifiedListener, times(1)).onReactionIdentified(eq(reaction), any());
        verify(identifiedListener, times(1)).onReactionIdentified(eq(reactionOtherVariant), any());
    }

    @Test
    void reactionObservedDoesNotNotifyListenerForDuplicateReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);
        Reaction reaction = new Reaction(
                new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(identifiedListener, times(1)).onReactionIdentified(eq(reaction), any());
    }

    @Test
    void getAndClearCountByReactionIdRetrievesAndClearsCounts() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);

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

    @Test
    void reactionOverTheLimit_isNeitherIdentifiedNorCounted() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = serviceReportingRightAway(identifiedListener);
        List<Reaction> reactionsWithinTheLimit = reactions(0, ReactionObserverService.MAX_REACTION_COUNT);
        Reaction reactionOverTheLimit = reaction(ReactionObserverService.MAX_REACTION_COUNT);

        reactionsWithinTheLimit.forEach(service::reactionObserved);
        service.reactionObserved(reactionOverTheLimit);
        service.reactionObserved(reactionOverTheLimit);

        verify(identifiedListener, times(ReactionObserverService.MAX_REACTION_COUNT)).onReactionIdentified(any(), any());
        verify(identifiedListener, never()).onReactionIdentified(eq(reactionOverTheLimit), any());
        assertThat(service.getAndClearCountByReactionId())
                .hasSize(ReactionObserverService.MAX_REACTION_COUNT)
                .doesNotContainKey(reactionOverTheLimit.id());
        assertThat(service.identifiedReactionCount()).isEqualTo(ReactionObserverService.MAX_REACTION_COUNT);
        assertThat(metrics.dropped(DroppedExecutionReason.REACTION_LIMIT)).isEqualTo(2);
        assertThat(metrics.dropped(DroppedExecutionReason.NOT_IDENTIFIED)).isZero();
    }

    /**
     * The counts of an interval had a limit of their own: once it was reached, no execution was counted any more,
     * not even of a reaction that had been counted in the interval before.
     */
    @Test
    void identifiedReactions_areCountedAlsoOnceTheLimitIsReached() {
        ReactionObserverService service = serviceReportingRightAway(mock(ReactionIdentifiedListener.class));
        List<Reaction> reactionsWithinTheLimit = reactions(0, ReactionObserverService.MAX_REACTION_COUNT);
        reactionsWithinTheLimit.forEach(service::reactionObserved);
        service.reactionObserved(reaction(ReactionObserverService.MAX_REACTION_COUNT));

        service.reactionObserved(reactionsWithinTheLimit.getFirst());
        service.reactionObserved(reactionsWithinTheLimit.getLast());

        Map<String, AtomicInteger> counts = service.getAndClearCountByReactionId();
        assertThat(counts.get(reactionsWithinTheLimit.getFirst().id())).hasValue(2);
        assertThat(counts.get(reactionsWithinTheLimit.getLast().id())).hasValue(2);
        assertThat(counts.get(reactionsWithinTheLimit.get(1).id())).hasValue(1);
    }

    @Test
    void afterThePeriod_reactionIsIdentifiedAgainWithANewIdentificationStart() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = serviceReportingRightAway(identifiedListener);
        Reaction reaction = reaction(1);

        service.reactionObserved(reaction);
        clock.advance(PERIOD.minusMillis(1));
        service.reactionObserved(reaction);
        verify(identifiedListener, times(1)).onReactionIdentified(any(), any());

        clock.advance(Duration.ofMillis(1));
        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(identifiedListener).onReactionIdentified(reaction, START);
        verify(identifiedListener).onReactionIdentified(reaction, START.plus(PERIOD));
        verifyNoMoreInteractions(identifiedListener);
        assertThat(service.getAndClearCountByReactionId().get(reaction.id())).hasValue(4);
    }

    @Test
    void afterThePeriod_theLimitStartsAnew() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = serviceReportingRightAway(identifiedListener);
        Reaction reactionOverTheLimit = reaction(ReactionObserverService.MAX_REACTION_COUNT);
        reactions(0, ReactionObserverService.MAX_REACTION_COUNT).forEach(service::reactionObserved);
        service.reactionObserved(reactionOverTheLimit);
        service.getAndClearCountByReactionId();

        clock.advance(PERIOD);
        assertThat(service.identifiedReactionCount())
                .describedAs("The period is over, nothing is identified until the next reaction is observed")
                .isZero();
        service.reactionObserved(reactionOverTheLimit);

        verify(identifiedListener).onReactionIdentified(reactionOverTheLimit, START.plus(PERIOD));
        assertThat(service.getAndClearCountByReactionId()).containsOnlyKeys(reactionOverTheLimit.id());
        assertThat(service.identifiedReactionCount()).isEqualTo(1);
        assertThat(metrics.dropped(DroppedExecutionReason.REACTION_LIMIT)).isEqualTo(1);
    }

    /**
     * The counts are fetched at an interval of their own, and a period can end within it. The reactions of both
     * periods must not pile up in the counts then, also if neither period reaches its own limit - nor those of
     * more periods if nobody fetches the counts.
     */
    @Test
    void periodEndsBeforeTheCountsAreFetched_countsAreLimitedToTheReactionLimit() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = serviceReportingRightAway(identifiedListener);
        int limit = ReactionObserverService.MAX_REACTION_COUNT;
        Reaction countedInFirstPeriod = reaction(0);
        Reaction lastOneTheCountsHaveRoomFor = reaction(limit - 1);
        Reaction oneTooMany = reaction(limit);

        reactions(0, limit - 1).forEach(service::reactionObserved);
        clock.advance(PERIOD);
        service.reactionObserved(lastOneTheCountsHaveRoomFor);
        service.reactionObserved(oneTooMany);
        service.reactionObserved(oneTooMany);
        service.reactionObserved(countedInFirstPeriod);

        verify(identifiedListener).onReactionIdentified(oneTooMany, START.plus(PERIOD));
        assertThat(service.identifiedReactionCount())
                .describedAs("The reaction is identified all the same, the second period is far from its limit")
                .isEqualTo(3);
        assertThat(metrics.dropped(DroppedExecutionReason.COUNT_LIMIT)).isEqualTo(2);
        assertThat(metrics.dropped(DroppedExecutionReason.REACTION_LIMIT)).isZero();
        Map<String, AtomicInteger> counts = service.getAndClearCountByReactionId();
        assertThat(counts).hasSize(limit).doesNotContainKey(oneTooMany.id());
        assertThat(counts.get(lastOneTheCountsHaveRoomFor.id())).hasValue(1);
        assertThat(counts.get(countedInFirstPeriod.id()))
                .describedAs("A reaction that is counted already is counted also once the limit is reached")
                .hasValue(2);

        service.reactionObserved(oneTooMany);
        assertThat(service.getAndClearCountByReactionId()).containsOnlyKeys(oneTooMany.id());
    }

    @Test
    void firstPeriod_lastsARandomHalfToAllOfThePeriod() {
        Set<Duration> firstPeriods = IntStream.range(0, 1000)
                .mapToObj(i -> ReactionObserverService.randomFirstPeriod(PERIOD))
                .collect(Collectors.toSet());

        assertThat(firstPeriods)
                .hasSizeGreaterThan(1)
                .allSatisfy(firstPeriod -> assertThat(firstPeriod).isBetween(PERIOD.dividedBy(2), PERIOD));
        assertThat(ReactionObserverService.randomFirstPeriod(ReactionObserverService.MIN_REIDENTIFICATION_PERIOD))
                .describedAs("Two periods of an instance never start within the same second")
                .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void firstPeriodOfAService_isShortenedAndTheNextOneLastsThePeriod() {
        List<Instant> identificationStarts = new CopyOnWriteArrayList<>();
        Reaction reaction = reaction(1);
        try (ReactionObserverService service = new ReactionObserverService(
                (identified, identificationStart) -> identificationStarts.add(identificationStart), PERIOD, clock, metrics)) {
            service.reactionObserved(reaction);
            clock.advance(PERIOD.dividedBy(2).minusMillis(1));
            service.reactionObserved(reaction); // still in the first period, however short it is
            clock.advance(PERIOD.dividedBy(2).plusMillis(1));
            service.reactionObserved(reaction); // the first period is over, however long it is
            clock.advance(PERIOD.minusMillis(1));
            service.reactionObserved(reaction); // still in the second period
            clock.advance(Duration.ofMillis(1));
            service.reactionObserved(reaction); // the second period is over
        }

        assertThat(identificationStarts)
                .containsExactly(START, START.plus(PERIOD), START.plus(PERIOD).plus(PERIOD));
    }

    @Test
    void periodTooShort_isRejected() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Duration tooShort = ReactionObserverService.MIN_REIDENTIFICATION_PERIOD.minusMillis(1);

        assertThatThrownBy(() -> new ReactionObserverService(identifiedListener, tooShort, clock, metrics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT2S");
    }

    /**
     * The reidentification cannot be switched off, not by a period that practically never ends either.
     */
    @Test
    void periodTooLong_isRejected() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Duration longest = ReactionObserverService.MAX_REIDENTIFICATION_PERIOD;
        Duration tooLong = longest.plusMillis(1);

        assertThatThrownBy(() -> new ReactionObserverService(identifiedListener, tooLong, clock, metrics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT168H");
        assertThatCode(() -> new ReactionObserverService(identifiedListener, longest, clock, metrics).close())
                .doesNotThrowAnyException();
    }

    /**
     * A report can still be waiting when its period ends, for example because the broker is unreachable. When it
     * fails then, the reaction may already be marked in the next period, and has to stay so.
     */
    @Test
    void failedReportOfAPeriodThatIsOver_leavesTheCurrentPeriodAlone() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = reaction(1);
        doThrow(new IllegalStateException("broker unreachable")).doNothing()
                .when(identifiedListener).onReactionIdentified(eq(reaction), any());
        Queue<Runnable> pendingReports = new ArrayDeque<>();
        ReactionObserverService service = serviceWith(identifiedListener, pendingReports::add);

        service.reactionObserved(reaction);
        clock.advance(PERIOD);
        service.reactionObserved(reaction);
        pendingReports.remove().run(); // the report of the first period fails
        service.reactionObserved(reaction);

        assertThat(pendingReports).describedAs("Only the report of the second period is pending").hasSize(1);
        assertThat(service.identifiedReactionCount()).isEqualTo(1);
        pendingReports.remove().run();
        verify(identifiedListener).onReactionIdentified(reaction, START);
        verify(identifiedListener).onReactionIdentified(reaction, START.plus(PERIOD));
        verifyNoMoreInteractions(identifiedListener);
        assertThat(service.getAndClearCountByReactionId().get(reaction.id())).hasValue(3);
        assertThat(metrics.droppedInTotal()).isZero();
    }

    @Test
    void periodEndsBetweenIdentifyingAndCounting_executionIsCounted() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Executor endingThePeriodOnHandOver = report -> {
            clock.advance(PERIOD);
            report.run();
        };
        ReactionObserverService service = serviceWith(identifiedListener, endingThePeriodOnHandOver);
        Reaction reaction = reaction(1);

        service.reactionObserved(reaction);

        verify(identifiedListener).onReactionIdentified(reaction, START);
        assertThat(service.getAndClearCountByReactionId().get(reaction.id())).hasValue(1);
        assertThat(metrics.droppedInTotal()).isZero();
    }

    @Test
    void observingWhilePeriodsEnd_losesNoCountAndIdentifiesEachReactionOncePerPeriod() throws InterruptedException {
        int threadCount = 8;
        int observationsPerThread = 20_000;
        List<Reaction> reactions = reactions(0, 50);
        Map<String, AtomicInteger> reports = new ConcurrentHashMap<>();
        ReactionObserverService service = serviceReportingRightAway((reaction, identificationStart) ->
                reports.computeIfAbsent(reaction.id() + "@" + identificationStart, key -> new AtomicInteger()).incrementAndGet());

        runInThreads(threadCount, () -> {
            for (int i = 0; i < observationsPerThread; i++) {
                service.reactionObserved(reactions.get(i % reactions.size()));
                if (i % 1000 == 999) {
                    clock.advance(PERIOD);
                }
            }
        });

        int counted = service.getAndClearCountByReactionId().values().stream().mapToInt(AtomicInteger::get).sum();
        assertThat(counted).isEqualTo(threadCount * observationsPerThread);
        assertThat(metrics.droppedInTotal()).isZero();
        assertThat(reports).hasSizeGreaterThan(reactions.size());
        assertThat(reports.values()).allSatisfy(reportsOfAReactionInAPeriod -> assertThat(reportsOfAReactionInAPeriod).hasValue(1));
    }

    /**
     * The scheduler fetches the counts while the service observes, and reads them right away. An execution is
     * counted either in the counts that are fetched or in the following ones, never in counts that were read
     * already.
     */
    @Test
    void fetchingTheCountsWhileObserving_losesNoCount() throws InterruptedException {
        int threadCount = 8;
        int observationsPerThread = 20_000;
        List<Reaction> reactions = reactions(0, 50);
        ReactionObserverService service = serviceReportingRightAway((reaction, identificationStart) -> {
        });
        AtomicBoolean observing = new AtomicBoolean(true);
        AtomicLong fetched = new AtomicLong();
        AtomicInteger fetchesWithCounts = new AtomicInteger();
        Thread fetcher = Thread.ofPlatform().daemon().start(() -> {
            while (observing.get()) {
                int counts = sumOf(service.getAndClearCountByReactionId());
                if (counts > 0) {
                    fetched.addAndGet(counts);
                    fetchesWithCounts.incrementAndGet();
                }
                LockSupport.parkNanos(50_000);
            }
        });
        try {
            runInThreads(threadCount, () -> {
                for (int i = 0; i < observationsPerThread; i++) {
                    service.reactionObserved(reactions.get(i % reactions.size()));
                }
            });
        } finally {
            // Also if the observers did not end in time
            observing.set(false);
        }
        fetcher.join(10_000);

        assertThat(fetcher.isAlive()).isFalse();
        // The second fetch with counts proves that the first one was followed by observations
        assertThat(fetchesWithCounts.get())
                .describedAs("Counts are fetched while the reactions are observed")
                .isGreaterThan(1);
        assertThat(fetched.get() + sumOf(service.getAndClearCountByReactionId()))
                .isEqualTo((long) threadCount * observationsPerThread);
        assertThat(metrics.droppedInTotal()).isZero();
    }

    /**
     * Runs the task in several threads at once and waits for them to end. The threads are daemon threads and
     * the wait has a time limit, also when it fails: a test that fails must not keep the build from ending.
     * Closing the executor would wait for its threads without a limit.
     */
    private static void runInThreads(int threadCount, Runnable task) throws InterruptedException {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(threadCount, Thread.ofPlatform().daemon().factory());
        try {
            for (int thread = 0; thread < threadCount; thread++) {
                threads.execute(() -> {
                    awaitRelease(go);
                    task.run();
                });
            }
            go.countDown();
            threads.shutdown();
            assertThat(threads.awaitTermination(60, TimeUnit.SECONDS))
                    .describedAs("The threads end in time")
                    .isTrue();
        } finally {
            go.countDown();
            threads.shutdownNow();
        }
    }

    private static int sumOf(Map<String, AtomicInteger> counts) {
        return counts.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    /**
     * While the broker is unreachable, the reports of a period wait in the queue. The next period queues the same
     * reactions again, and the queue is full: the hand-off is rejected, and the reaction is tried again when it
     * occurs the next time.
     */
    @Test
    void queueFullAfterThePeriod_reactionIsTriedAgainWhenItOccursAgain() throws InterruptedException {
        int queueCapacity = ReactionObserverService.MAX_REACTION_COUNT;
        CountDownLatch firstReportStarted = new CountDownLatch(1);
        CountDownLatch brokerReachable = new CountDownLatch(1);
        List<Reaction> reactions = reactions(0, queueCapacity);
        Reaction rejected = reactions.getLast();
        CountDownLatch queuedReportsSent = new CountDownLatch(queueCapacity + 1);
        CountDownLatch rejectedReportedInSecondPeriod = new CountDownLatch(1);
        ReactionIdentifiedListener waitingForTheBroker = (reaction, identificationStart) -> {
            firstReportStarted.countDown();
            awaitRelease(brokerReachable);
            queuedReportsSent.countDown();
            if (reaction.equals(rejected) && identificationStart.equals(START.plus(PERIOD))) {
                rejectedReportedInSecondPeriod.countDown();
            }
        };

        try (ReactionObserverService service = new ReactionObserverService(waitingForTheBroker, PERIOD, clock, metrics)) {
            try {
                service.reactionObserved(reactions.getFirst());
                assertThat(firstReportStarted.await(10, TimeUnit.SECONDS)).isTrue();
                reactions.forEach(service::reactionObserved); // one report is being sent, all others are queued
                assertThat(metrics.droppedInTotal()).isZero();

                clock.advance(PERIOD);
                reactions.forEach(service::reactionObserved); // the queue has room for one more report

                assertThat(metrics.dropped(DroppedExecutionReason.NOT_IDENTIFIED)).isEqualTo(queueCapacity - 1L);
                assertThat(service.identifiedReactionCount()).isEqualTo(1);
            } finally {
                brokerReachable.countDown();
            }
            assertThat(queuedReportsSent.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(rejectedReportedInSecondPeriod.getCount()).isEqualTo(1);

            service.reactionObserved(rejected);

            assertThat(rejectedReportedInSecondPeriod.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(service.identifiedReactionCount()).isEqualTo(2);
        }
    }

    @Test
    void limitWarning_isLoggedOncePerPeriod() {
        ReactionObserverService service = serviceReportingRightAway(mock(ReactionIdentifiedListener.class));
        Reaction reactionOverTheLimit = reaction(ReactionObserverService.MAX_REACTION_COUNT);

        String firstPeriodLog = logOf(() -> {
            reactions(0, ReactionObserverService.MAX_REACTION_COUNT).forEach(service::reactionObserved);
            service.reactionObserved(reactionOverTheLimit);
            service.reactionObserved(reactionOverTheLimit);
        });
        clock.advance(PERIOD);
        String secondPeriodLog = logOf(() -> {
            reactions(0, ReactionObserverService.MAX_REACTION_COUNT).forEach(service::reactionObserved);
            service.reactionObserved(reactionOverTheLimit);
            service.reactionObserved(reactionOverTheLimit);
        });

        assertThat(firstPeriodLog).containsOnlyOnce("Maximum reaction count reached (4096)");
        assertThat(secondPeriodLog).containsOnlyOnce("Maximum reaction count reached (4096)");
    }

    @Test
    void reactionObservedDoesNotWaitForTheListener() throws InterruptedException {
        CountDownLatch notified = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> notifyingThread = new AtomicReference<>();
        ReactionIdentifiedListener blockingListener = (reaction, identificationStart) -> {
            notifyingThread.set(Thread.currentThread().getName());
            notified.countDown();
            awaitRelease(release);
        };
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);

        try (ReactionObserverService service = new ReactionObserverService(blockingListener, PERIOD, clock, metrics)) {
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> service.reactionObserved(reaction));

                assertThat(notified.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(notifyingThread.get()).isEqualTo(ReactionObserverService.IDENTIFICATION_THREAD_NAME);
                assertThat(service.getAndClearCountByReactionId()).containsOnlyKeys(reaction.id());
            } finally {
                release.countDown(); // before closing the service, which waits for the listener
            }
        }
    }

    /**
     * An {@link AbstractMethodError} is what a listener from a module of another version of the library fails
     * with: it must not end the identification thread unnoticed while the reaction stays marked.
     */
    static Stream<Throwable> notificationFailures() {
        return Stream.of(new IllegalStateException("broker unreachable"), new AbstractMethodError("listener of another version"));
    }

    @ParameterizedTest
    @MethodSource("notificationFailures")
    void reactionObservedNotifiesListenerAgainAfterFailedNotification(Throwable notificationFailure) {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        doThrow(notificationFailure).doNothing()
                .when(identifiedListener).onReactionIdentified(eq(reaction), any());
        ReactionObserverService service = serviceReportingRightAway(identifiedListener);

        service.reactionObserved(reaction);
        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(identifiedListener, times(2)).onReactionIdentified(eq(reaction), any());
        // The executor reports in the observing thread: the first report has failed before the execution is counted
        assertThat(service.getAndClearCountByReactionId())
                .extractingByKey(reaction.id()).extracting(AtomicInteger::get).isEqualTo(2);
        assertThat(metrics.dropped(DroppedExecutionReason.NOT_IDENTIFIED)).isEqualTo(1);
    }

    static Stream<RuntimeException> handOffFailures() {
        return Stream.of(new RejectedExecutionException("queue full"), new IllegalStateException("executor broken"));
    }

    @ParameterizedTest
    @MethodSource("handOffFailures")
    void reactionObservedNotifiesListenerAgainAfterFailedHandOff(RuntimeException handOffFailure) {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        AtomicBoolean failNext = new AtomicBoolean(true);
        Executor failingOnceExecutor = task -> {
            if (failNext.getAndSet(false)) {
                throw handOffFailure;
            }
            task.run();
        };
        ReactionObserverService service = serviceWith(identifiedListener, failingOnceExecutor);

        service.reactionObserved(reaction);
        verifyNoInteractions(identifiedListener);
        assertThat(service.identifiedReactionCount()).isZero();

        service.reactionObserved(reaction);
        verify(identifiedListener, times(1)).onReactionIdentified(eq(reaction), any());
        assertThat(service.getAndClearCountByReactionId())
                .extractingByKey(reaction.id()).extracting(AtomicInteger::get).isEqualTo(1);
        assertThat(metrics.dropped(DroppedExecutionReason.NOT_IDENTIFIED)).isEqualTo(1);
    }

    @Test
    void closeLetsPendingNotificationsFinish() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        try (ReactionObserverService service = new ReactionObserverService(identifiedListener, PERIOD, clock, metrics)) {
            service.reactionObserved(reaction);
        }

        verify(identifiedListener, times(1)).onReactionIdentified(eq(reaction), any());
    }

    @Test
    void reactionObservedAfterCloseCountsOnlyTheReactionsIdentifiedBefore() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction identifiedBefore = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        Reaction newAfterClose = new Reaction(Observation.ofEvent("type2", "topic2"), null);
        ReactionObserverService closedService;
        try (ReactionObserverService service = new ReactionObserverService(identifiedListener, PERIOD, clock, metrics)) {
            service.reactionObserved(identifiedBefore);
            closedService = service;
        }
        closedService.getAndClearCountByReactionId();

        closedService.reactionObserved(identifiedBefore);
        closedService.reactionObserved(newAfterClose);

        verify(identifiedListener, never()).onReactionIdentified(eq(newAfterClose), any());
        assertThat(closedService.getAndClearCountByReactionId()).containsOnlyKeys(identifiedBefore.id());
        assertThat(metrics.dropped(DroppedExecutionReason.NOT_IDENTIFIED)).isEqualTo(1);
    }

    @Test
    void closeDoesNotShutDownAGivenExecutor() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        try (ExecutorService givenExecutor = Executors.newSingleThreadExecutor()) {
            new ReactionObserverService(identifiedListener, givenExecutor).close();

            assertThat(givenExecutor.isShutdown()).isFalse();
        }
    }

    private ReactionObserverService serviceReportingRightAway(ReactionIdentifiedListener identifiedListener) {
        return serviceWith(identifiedListener, Runnable::run);
    }

    /**
     * A service with the clock and the metrics of the test, whose first period lasts as long as the following ones.
     */
    private ReactionObserverService serviceWith(ReactionIdentifiedListener identifiedListener, Executor identificationExecutor) {
        return new ReactionObserverService(identifiedListener, identificationExecutor, false, PERIOD, PERIOD, clock, metrics);
    }

    private static Reaction reaction(int number) {
        return new Reaction(Observation.ofEvent("type" + number, "topic"), null);
    }

    private static List<Reaction> reactions(int from, int to) {
        return IntStream.range(from, to).mapToObj(ReactionObserverServiceTest::reaction).toList();
    }

    /**
     * @return what the action logged: the logger of the tests writes to the standard error stream
     */
    private static String logOf(Runnable action) {
        PrintStream standardError = System.err;
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        System.setErr(new PrintStream(log, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(standardError);
        }
        return log.toString(StandardCharsets.UTF_8);
    }

    private static final class AdjustableClock extends Clock {

        private final AtomicReference<Instant> now;

        private AdjustableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class RecordedMetrics implements ReactionObserverMetrics {

        private final Map<DroppedExecutionReason, AtomicInteger> dropped = new EnumMap<>(DroppedExecutionReason.class);

        private RecordedMetrics() {
            for (DroppedExecutionReason reason : DroppedExecutionReason.values()) {
                dropped.put(reason, new AtomicInteger());
            }
        }

        @Override
        public void droppedExecution(DroppedExecutionReason reason) {
            dropped.get(reason).incrementAndGet();
        }

        @Override
        public void identifiedReactions(IntSupplier count) {
            // The tests read the number from the service
        }

        long dropped(DroppedExecutionReason reason) {
            return dropped.get(reason).get();
        }

        long droppedInTotal() {
            return dropped.values().stream().mapToLong(AtomicInteger::get).sum();
        }
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
