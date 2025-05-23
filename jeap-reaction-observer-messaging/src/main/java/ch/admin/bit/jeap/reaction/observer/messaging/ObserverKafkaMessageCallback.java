package ch.admin.bit.jeap.reaction.observer.messaging;

import ch.admin.bit.jeap.command.Command;
import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.messaging.kafka.interceptor.JeapKafkaMessageCallback;
import ch.admin.bit.jeap.messaging.model.Message;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.event.identified.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;

public class ObserverKafkaMessageCallback implements JeapKafkaMessageCallback {

    private final ReactionRecorder reactionRecorder;

    public ObserverKafkaMessageCallback(ReactionRecorder reactionRecorder) {
        this.reactionRecorder = reactionRecorder;
    }

    @Override
    public void onSend(Message message, String topicName) {
        if (filtered(message)) {
            return;
        }
        Observation action = createObservation(message, topicName);
        reactionRecorder.onAction(action);
    }

    @Override
    public void beforeConsume(Message message, String topicName) {
        if (filtered(message)) {
            return;
        }
        reactionRecorder.onTriggerStart(createObservation(message, topicName));
    }

    @Override
    public void afterConsume(Message message, String topicName) {
        if (filtered(message)) {
            return;
        }
        reactionRecorder.onTriggerHandled();
    }

    @Override
    public void afterRecord(Message message, String topicName) {
        if (filtered(message)) {
            return;
        }
        reactionRecorder.afterTrigger();
    }

    private Observation createObservation(Message message, String topicName) {
        String messageType = message.getType().getName();
        return message instanceof Command ? Observation.ofCommand(messageType, topicName) : Observation.ofEvent(messageType, topicName);
    }

    private boolean filtered(Message message) {
        return message instanceof ReactionsObservedEvent || message instanceof ReactionIdentifiedEvent ||
                message instanceof MessageProcessingFailedEvent;
    }
}
