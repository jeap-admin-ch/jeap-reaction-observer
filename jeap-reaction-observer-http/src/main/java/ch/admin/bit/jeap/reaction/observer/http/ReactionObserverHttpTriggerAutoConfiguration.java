package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.configuration.ConditionalOnReactionObserverEnabled;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.header.OperationNameResolver;
import ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * Observes the incoming HTTP requests of a servlet web application with Spring MVC as triggers. Only active if
 * the service is configured with a reaction model that observes HTTP requests
 * ({@code jeap.reaction.observer.model-version}), and like the rest of the library not at all if
 * {@code jeap.reaction.observer.enabled} is false. See docs/http-triggers.md.
 * <p>
 * The response header that names the operation to the caller is set by the header module alone, whatever the
 * reaction model and also with the Reaction Observer switched off.
 */
@AutoConfiguration(after = ReactionObserverHeaderAutoConfiguration.class)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnReactionObserverEnabled
@Conditional(OnHttpRequestObservationCondition.class)
@EnableConfigurationProperties(ReactionObserverHttpProperties.class)
public class ReactionObserverHttpTriggerAutoConfiguration {

    /**
     * The operation is named by the resolver of the header module, so that trigger and response header never
     * differ. The header module provides the resolver also if its header is switched off; without the header
     * module's configuration the same resolver is created here.
     * <p>
     * The recorder is looked up when the first request is observed, not when the bean is created: the recorder
     * depends on the infrastructure that reports reactions.
     */
    @Bean
    ReactionObservingHandlerInterceptor jeapReactionObservingHandlerInterceptor(
            ObjectProvider<ReactionRecorder> reactionRecorder, ObjectProvider<OperationNameResolver> operationNameResolver,
            ReactionObserverHttpProperties properties, Environment environment) {
        OperationNameResolver resolver = operationNameResolver
                .getIfAvailable(() -> OperationNameResolver.fromEnvironment(environment));
        HttpTriggerComponentName.warnIfDifferentFromProvider(resolver.provider(), environment);
        return new ReactionObservingHandlerInterceptor(new LazyReactionRecorder(reactionRecorder), resolver,
                properties.getLogInterval());
    }

    /**
     * Registered like the interceptor of the header module, with a {@link WebMvcConfigurer}: both reach the same
     * handler mappings, so a request is observed as a trigger exactly if the header module names it.
     * <p>
     * The trigger encloses the other interceptors registered through a {@code WebMvcConfigurer}: what they cause
     * belongs to the reaction, and a request they reject is completed by the rule for incoming requests like any
     * other. Interceptor and configurer therefore both have the highest precedence: Spring MVC keeps interceptors
     * of the same order in the order of their configurers. Only an interceptor of the highest precedence whose
     * configurer is ordered with the highest precedence as well, or implements {@code PriorityOrdered}, can come
     * first: Spring sorts {@code PriorityOrdered} configurers before all others, whatever their order value. The
     * order does not apply to the interceptors Spring MVC puts in front of the registered ones:
     * {@code MappedInterceptor} beans and the interceptor for CORS.
     * <p>
     * The interceptor of the header module has the same precedence, and its configurer comes first because this
     * auto-configuration follows the one of the header module. The trigger then takes the operation name the
     * header module stored at the request; in the other order it would resolve the same name once more.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebMvcConfigurer jeapReactionObserverHttpTriggerWebMvcConfigurer(ReactionObservingHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE);
            }
        };
    }
}
