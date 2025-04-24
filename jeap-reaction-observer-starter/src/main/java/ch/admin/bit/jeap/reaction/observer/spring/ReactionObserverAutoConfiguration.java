package ch.admin.bit.jeap.reaction.observer.spring;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ReactionObserverAutoConfiguration {

    @Bean
    ReactionObserverService reactionObserverService(ReactionIdentifiedListener reactionIdentifiedListener) {
        return new ReactionObserverService(reactionIdentifiedListener);
    }

    @Bean
    ReactionRecorder reactionRecorder(ReactionObserverService reactionObserverService) {
        return new ReactionRecorder(reactionObserverService);
    }
}
