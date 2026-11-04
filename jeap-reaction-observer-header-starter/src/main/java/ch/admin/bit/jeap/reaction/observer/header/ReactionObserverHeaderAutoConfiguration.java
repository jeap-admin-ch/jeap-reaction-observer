package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.EnumSet;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * Auto-configuration for the {@code JEAP-REACTION-OBSERVATION} response header. Only active in a servlet web
 * application with Spring MVC on the classpath (not reactive, not a plain batch/messaging service); does
 * nothing otherwise. The web dependencies are optional, so this starter never brings them in itself. See
 * {@code docs/response-header.md} for the header protocol, the operation-naming rule, the kill switch and
 * documented limits.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = SERVLET)
@EnableConfigurationProperties(ReactionObserverHeaderProperties.class)
public class ReactionObserverHeaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OperationNameResolver jeapReactionObserverHeaderOperationNameResolver(Environment environment) {
        return OperationNameResolver.fromEnvironment(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jeap.reaction.observer.header", name = "enabled", matchIfMissing = true)
    ReactionObservationHeaderInterceptor jeapReactionObserverHeaderInterceptor(OperationNameResolver operationNameResolver) {
        return new ReactionObservationHeaderInterceptor(operationNameResolver);
    }

    /**
     * Registered with the highest precedence so that an application interceptor returning {@code false} from
     * {@code preHandle} (auth, rate limiting, ...) cannot prevent the header and {@code Vary} from being set.
     */
    @Bean
    @ConditionalOnProperty(prefix = "jeap.reaction.observer.header", name = "enabled", matchIfMissing = true)
    WebMvcConfigurer jeapReactionObserverHeaderWebMvcConfigurer(ReactionObservationHeaderInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE);
            }
        };
    }

    /**
     * Registered with the highest possible precedence so the wrapped response reaches every later filter,
     * the dispatcher servlet and the handler - see {@link ReactionObservationHeaderProtectionFilter}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "jeap.reaction.observer.header", name = "enabled", matchIfMissing = true)
    FilterRegistrationBean<ReactionObservationHeaderProtectionFilter> jeapReactionObserverHeaderProtectionFilter() {
        FilterRegistrationBean<ReactionObservationHeaderProtectionFilter> registration =
                new FilterRegistrationBean<>(new ReactionObservationHeaderProtectionFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        registration.setAsyncSupported(true);
        return registration;
    }
}
