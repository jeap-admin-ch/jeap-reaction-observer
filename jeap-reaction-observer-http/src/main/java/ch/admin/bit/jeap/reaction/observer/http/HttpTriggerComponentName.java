package ch.admin.bit.jeap.reaction.observer.http;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * An incoming HTTP request is named after the provider of the header module: jeap.rest.tracing.application-name,
 * or else spring.application.name. The reactions of the service are reported for the component named by
 * jeap.messaging.kafka.service-name. If the two differ, the operations of the service carry another name in the
 * reaction graph than the component they belong to. Nothing breaks, but the graph is harder to read - the
 * service is told at its start.
 * <p>
 * The name of the component is read as a property, this module does not depend on jEAP Messaging.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HttpTriggerComponentName {

    static final String COMPONENT_NAME_PROPERTY = "jeap.messaging.kafka.service-name";

    /**
     * Logs a warning if both names are set and differ. Without one of them there is nothing to compare: without
     * a provider no request is named at all, which the header module reports itself.
     *
     * @return whether the warning was logged
     */
    static boolean warnIfDifferentFromProvider(String provider, Environment environment) {
        String componentName = Binder.get(environment).bind(COMPONENT_NAME_PROPERTY, String.class).orElse("");
        if (provider == null || provider.isBlank() || componentName.isBlank() || provider.equals(componentName)) {
            return false;
        }
        log.warn("The incoming HTTP requests of this service are observed as triggers of the provider '{}' " +
                "(jeap.rest.tracing.application-name, or else spring.application.name), but its reactions are " +
                "reported for the component '{}' ({}). In the reaction graph the operations of the service carry " +
                "another name than the component they belong to. Set both to the same name.",
                provider, componentName, COMPONENT_NAME_PROPERTY);
        return true;
    }
}
