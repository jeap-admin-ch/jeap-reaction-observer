package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Supplier;

/**
 * Looks the {@link ReactionRecorder} up on first use and keeps it.
 */
class LazyReactionRecorder implements Supplier<ReactionRecorder> {

    private final ObjectProvider<ReactionRecorder> provider;
    private volatile ReactionRecorder reactionRecorder;

    LazyReactionRecorder(ObjectProvider<ReactionRecorder> provider) {
        this.provider = provider;
    }

    @Override
    public ReactionRecorder get() {
        ReactionRecorder recorder = reactionRecorder;
        if (recorder == null) {
            recorder = provider.getObject();
            reactionRecorder = recorder;
        }
        return recorder;
    }
}
