package ch.admin.bit.jeap.reaction.observer.messaging;

import ch.admin.bit.jeap.messaging.avro.errorevent.MessageProcessingFailedEvent;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ObserverKafkaMessageCallbackFilterTest {

    @Mock
    private ReactionRecorder reactionRecorder;
    @Mock
    private MessageProcessingFailedEvent failedEvent;
    @Mock
    private ReactionIdentifiedEvent identifiedEvent;
    @Mock
    private ReactionsObservedEvent observedEvent;

    @Test
    void messageProcessingFailedEvent_filtered() {
        ObserverKafkaMessageCallback callback = new ObserverKafkaMessageCallback(reactionRecorder);

        callback.onSend(failedEvent, "topic");
        callback.beforeConsume(failedEvent, "topic");
        callback.afterConsume(failedEvent, "topic");
        callback.afterRecord(failedEvent, "topic");

        verifyNoInteractions(reactionRecorder);
    }

    @Test
    void reactionEvents_filtered() {
        ObserverKafkaMessageCallback callback = new ObserverKafkaMessageCallback(reactionRecorder);

        callback.onSend(identifiedEvent, "topic");
        callback.beforeConsume(identifiedEvent, "topic");
        callback.afterConsume(identifiedEvent, "topic");
        callback.afterRecord(identifiedEvent, "topic");

        callback.onSend(observedEvent, "topic");
        callback.beforeConsume(observedEvent, "topic");
        callback.afterConsume(observedEvent, "topic");
        callback.afterRecord(observedEvent, "topic");

        verifyNoInteractions(reactionRecorder);
    }
}
