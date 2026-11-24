package ch.admin.bit.jeap.reaction.observer.core.domain.listener;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;

import java.time.Instant;

/**
 * Internal interface between the modules of the reaction observer library: the core hands the identifications
 * over to the module that publishes them. It is not an extension point and can change in any release. A service
 * that replaces the listener of the library keeps publishing counts, but no identifications any more, and a
 * Reaction Observer Service skips the counts of reactions it does not know.
 */
public interface ReactionIdentifiedListener {

    /**
     * @param reaction            the reaction the instance identified
     * @param identificationStart the start of the identification period the reaction was identified in. An
     *                            instance identifies each of its reactions once per period, so reaction and
     *                            identification start together name one identification of the instance
     */
    void onReactionIdentified(Reaction reaction, Instant identificationStart);
}
