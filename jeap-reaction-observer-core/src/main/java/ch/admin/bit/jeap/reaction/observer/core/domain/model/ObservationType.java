package ch.admin.bit.jeap.reaction.observer.core.domain.model;

public enum ObservationType {
    EVENT, COMMAND,
    /**
     * An HTTP request sent as an action. Part of reaction model version 2.
     */
    HTTP_REQUEST
}
