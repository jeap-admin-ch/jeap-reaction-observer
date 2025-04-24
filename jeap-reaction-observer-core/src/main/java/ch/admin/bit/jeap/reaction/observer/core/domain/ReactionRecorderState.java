package ch.admin.bit.jeap.reaction.observer.core.domain;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import lombok.Data;

@Data
public class ReactionRecorderState {
    private Observation trigger = null;
    private boolean reactionPublished = false;
}
