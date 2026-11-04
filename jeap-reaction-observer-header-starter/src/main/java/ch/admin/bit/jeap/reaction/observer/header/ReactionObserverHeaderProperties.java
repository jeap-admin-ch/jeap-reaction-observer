package ch.admin.bit.jeap.reaction.observer.header;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@code JEAP-REACTION-OBSERVATION} response header.
 */
@ConfigurationProperties(prefix = "jeap.reaction.observer.header")
@Data
public class ReactionObserverHeaderProperties {

    /**
     * Emergency kill switch for the header, default on. Independent of the library's global
     * {@code jeap.reaction.observer.enabled} switch: a service that disables its own reaction recording
     * should still stay visible to its callers. Meant for an emergency in the request path, not for normal
     * operation.
     */
    private boolean enabled = true;
}
