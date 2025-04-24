package ch.admin.bit.jeap.reaction.observer.events.scheduler;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.events.producer.KafkaEventProducer;
import ch.admin.bit.jeap.reaction.observer.events.spring.ReactionObserverKafkaConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReactionsObservedEventScheduler implements SmartLifecycle {

    private final ReactionObserverKafkaConfigProperties props;
    private final ReactionObserverService reactionObserverService;
    private final KafkaEventProducer kafkaEventProducer;
    private Instant lastObservationTimeframeStart;
    private boolean running = false;

    public ReactionsObservedEventScheduler(ReactionObserverKafkaConfigProperties props, ReactionObserverService reactionObserverService, KafkaEventProducer kafkaEventProducer) {
        this.props = props;
        this.reactionObserverService = reactionObserverService;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    // Defaults to every five minutes (300 seconds)
    @Scheduled(
            fixedRateString = "${jeap.reaction.observer.events.observed-event-rate-seconds:300}",
            initialDelayString = "${jeap.reaction.observer.events.observed-event-rate-seconds:300}",
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
    )
    public void sendEvent() {
        if (running) {
            Instant from = lastObservationTimeframeStart;
            Instant to = Instant.now();
            lastObservationTimeframeStart = to;
            produceEvent(from, to);
        }
    }

    private void produceEvent(Instant from, Instant to) {
        // Publication happens on a best-effort basis - in case anything goes wrong when publishing the count for the
        // current timeframe, the next one will be published with the next call to this method.
        Map<String, AtomicInteger> countByReactionId = reactionObserverService.getAndClearCountByReactionId();
        if (countByReactionId.isEmpty()) {
            log.trace("No reactions observed in last timeframe. Skipping event production.");
            return;
        }

        log.debug("Producing ReactionsObserved events for timeframe from {} to {}: {}", from, to, countByReactionId);
        kafkaEventProducer.sendReactionObservedEvent(countByReactionId, from, to);
    }

    @Override
    public void start() {
        if (!props.isObservedEventSchedulingEnabled()) {
            log.info("ReactionsObservedEventScheduler is disabled. Skipping start.");
            return;
        }

        log.info("Starting ReactionsObservedEventScheduler");
        if (lastObservationTimeframeStart == null) {
            lastObservationTimeframeStart = Instant.now();
        }
        running = true;
    }

    @Override
    public void stop() {
        if (!props.isObservedEventSchedulingEnabled()) {
            return;
        }

        log.info("Stopping ReactionsObservedEventScheduler");
        sendEvent();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
