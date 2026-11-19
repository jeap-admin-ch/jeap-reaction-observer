package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

class ReactionObserverServiceTest {

    @Test
    void reactionObservedNotifiesListenerForNewReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);
        Reaction reaction = new Reaction(
                new Observation(ObservationType.EVENT, "fqn", new TreeMap<>(Map.of("key", "value"))),
                null
        );

        service.reactionObserved(reaction);

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
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

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
        verify(identifiedListener, times(1)).onReactionIdentified(reactionOtherVariant);
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

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
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
    void reactionObservedDoesNotExceedMaxReactionCount() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);

        // Simulate reaching the maximum reaction count
        for (int i = 0; i < ReactionObserverService.MAX_REACTION_COUNT + 1; i++) {
            Reaction reaction = new Reaction(
                    new Observation(ObservationType.EVENT, "fqn" + i, new TreeMap<>(Map.of("key", "value" + i))),
                    null
            );
            service.reactionObserved(reaction);
        }

        // Verify that the listener was notified only for the first MAX_REACTION_COUNT reactions
        verify(identifiedListener, times(ReactionObserverService.MAX_REACTION_COUNT)).onReactionIdentified(any());
    }

    @Test
    void reactionObservedDoesNotWaitForTheListener() throws InterruptedException {
        CountDownLatch notified = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> notifyingThread = new AtomicReference<>();
        ReactionIdentifiedListener blockingListener = reaction -> {
            notifyingThread.set(Thread.currentThread().getName());
            notified.countDown();
            awaitRelease(release);
        };
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);

        try (ReactionObserverService service = new ReactionObserverService(blockingListener)) {
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

    @Test
    void reactionObservedNotifiesListenerAgainAfterFailedNotification() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        doThrow(new IllegalStateException("broker unreachable")).doNothing()
                .when(identifiedListener).onReactionIdentified(reaction);
        ReactionObserverService service = new ReactionObserverService(identifiedListener, Runnable::run);

        service.reactionObserved(reaction);
        service.reactionObserved(reaction);
        service.reactionObserved(reaction);

        verify(identifiedListener, times(2)).onReactionIdentified(reaction);
        assertThat(service.getAndClearCountByReactionId())
                .extractingByKey(reaction.id()).extracting(AtomicInteger::get).isEqualTo(3);
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
        ReactionObserverService service = new ReactionObserverService(identifiedListener, failingOnceExecutor);

        service.reactionObserved(reaction);
        verifyNoInteractions(identifiedListener);

        service.reactionObserved(reaction);
        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
        assertThat(service.getAndClearCountByReactionId())
                .extractingByKey(reaction.id()).extracting(AtomicInteger::get).isEqualTo(2);
    }

    @Test
    void closeLetsPendingNotificationsFinish() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        try (ReactionObserverService service = new ReactionObserverService(identifiedListener)) {
            service.reactionObserved(reaction);
        }

        verify(identifiedListener, times(1)).onReactionIdentified(reaction);
    }

    @Test
    void reactionObservedAfterCloseStillCountsTheReaction() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        Reaction reaction = new Reaction(Observation.ofEvent("type1", "topic1"), null);
        ReactionObserverService closedService;
        try (ReactionObserverService service = new ReactionObserverService(identifiedListener)) {
            closedService = service;
        }

        closedService.reactionObserved(reaction);

        verifyNoInteractions(identifiedListener);
        assertThat(closedService.getAndClearCountByReactionId()).containsOnlyKeys(reaction.id());
    }

    @Test
    void closeDoesNotShutDownAGivenExecutor() {
        ReactionIdentifiedListener identifiedListener = mock(ReactionIdentifiedListener.class);
        try (ExecutorService givenExecutor = Executors.newSingleThreadExecutor()) {
            new ReactionObserverService(identifiedListener, givenExecutor).close();

            assertThat(givenExecutor.isShutdown()).isFalse();
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
