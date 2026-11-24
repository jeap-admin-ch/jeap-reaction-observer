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
 * <p>
 * A service whose HTTP requests are observed does not start if it combines this module with another version of
 * a module it uses, see {@link ReactionObserverModuleVersions}. That is checked here and not where the
 * observation is set up: a condition is evaluated at the start of every service, also of one that creates its
 * beans lazily ({@code spring.main.lazy-initialization}) and would otherwise fail in its first call.
 */
class OnHttpRequestObservationCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ReactionModel reactionModel;
        boolean observesHttpRequests;
        try {
            reactionModel = ReactionObserverProperties.of(context.getEnvironment()).reactionModel();
            observesHttpRequests = reactionModel.observes(ObservationType.HTTP_REQUEST);
        } catch (LinkageError e) {
            // A module is too old to even tell the reaction model: say which modules do not fit, if they do not
            ReactionObserverModuleVersions.requireSameVersion(e);
            throw e;
        }
        ConditionMessage.Builder message = ConditionMessage.forCondition("Reaction model " + reactionModel);
        if (!observesHttpRequests) {
            return ConditionOutcome.noMatch(message.because("does not observe HTTP requests"));
        }
        ReactionObserverModuleVersions.requireSameVersion();
        return ConditionOutcome.match(message.because("observes HTTP requests"));
    }
}
