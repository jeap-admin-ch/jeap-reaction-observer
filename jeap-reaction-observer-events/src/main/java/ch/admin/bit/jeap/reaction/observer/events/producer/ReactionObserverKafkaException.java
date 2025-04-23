package ch.admin.bit.jeap.reaction.observer.events.producer;

public class ReactionObserverKafkaException extends RuntimeException {

    private ReactionObserverKafkaException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ReactionObserverKafkaException producingEventFailed(Exception cause) {
        return new ReactionObserverKafkaException("Failed to produce event", cause);
    }
}
