package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ReactionObserverAutoConfiguration {

    @Bean
    ReactionObserverService reactionService(ReactionIdentifiedListener reactionIdentifiedListener) {
        return new ReactionObserverService(reactionIdentifiedListener);
    }
}
