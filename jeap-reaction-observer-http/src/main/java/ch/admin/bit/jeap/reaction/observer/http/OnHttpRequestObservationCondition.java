package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.configuration.ReactionObserverProperties;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ObservationType;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches if the reaction model the service is configured with observes HTTP requests. The condition asks the
 * model instead of comparing the value of the property, so it holds for every later model version as well.
 */
class OnHttpRequestObservationCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ReactionModel reactionModel = ReactionObserverProperties.of(context.getEnvironment()).reactionModel();
        ConditionMessage.Builder message = ConditionMessage.forCondition("Reaction model " + reactionModel);
        return reactionModel.observes(ObservationType.HTTP_REQUEST)
                ? ConditionOutcome.match(message.because("observes HTTP requests"))
                : ConditionOutcome.noMatch(message.because("does not observe HTTP requests"));
    }
}
