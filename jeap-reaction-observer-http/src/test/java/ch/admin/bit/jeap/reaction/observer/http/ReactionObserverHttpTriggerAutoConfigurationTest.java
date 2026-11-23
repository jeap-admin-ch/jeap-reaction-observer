package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ReactionObserverHttpTriggerAutoConfigurationTest {

    private static final String MODEL_VERSION_2 = "jeap.reaction.observer.model-version=2";
    private static final String COMPONENT_NAME_WARNING = "carry another name than the component";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ReactionObserverHeaderAutoConfiguration.class, ReactionObserverHttpTriggerAutoConfiguration.class))
            .withUserConfiguration(RecorderConfiguration.class)
            .withPropertyValues("spring.application.name=order-service");

    @Test
    void default_modelVersion1_incomingRequestsAreNotObserved() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ReactionObservingHandlerInterceptor.class));
    }

    @Test
    void modelVersion2_incomingRequestsAreObserved_rightAfterTheInterceptorOfTheHeaderModule() {
        contextRunner.withPropertyValues(MODEL_VERSION_2).run(context -> {
            assertThat(context).hasSingleBean(ReactionObservingHandlerInterceptor.class);
            assertThat(interceptorsInOrder(context))
                    .containsExactly("ReactionObservationHeaderInterceptor", "ReactionObservingHandlerInterceptor");
        });
    }

    @Test
    void modelVersion2_serviceInterceptorsOfTheHighestPrecedence_areEnclosedByTheTrigger() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withUserConfiguration(ServiceInterceptorConfiguration.class)
                .run(context -> assertThat(interceptorsInOrder(context)).containsExactly(
                        "ReactionObservationHeaderInterceptor", "ReactionObservingHandlerInterceptor",
                        "HighestPrecedenceServiceInterceptor", "NextPrecedenceServiceInterceptor"));
    }

    @Test
    void modelVersion2_reactionObserverSwitchedOff_incomingRequestsAreNotObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.reaction.observer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReactionObservingHandlerInterceptor.class));
    }

    @Test
    void modelVersion2_headerSwitchedOff_incomingRequestsAreObserved() {
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.reaction.observer.header.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(ReactionObservingHandlerInterceptor.class));
    }

    @Test
    void modelVersion2_withoutTheConfigurationOfTheHeaderModule_incomingRequestsAreObserved() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ReactionObserverHttpTriggerAutoConfiguration.class))
                .withUserConfiguration(RecorderConfiguration.class)
                .withPropertyValues("spring.application.name=order-service", MODEL_VERSION_2)
                .run(context -> assertThat(context).hasSingleBean(ReactionObservingHandlerInterceptor.class));
    }

    @Test
    void modelVersion2_notAWebApplication_nothingIsConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ReactionObserverHeaderAutoConfiguration.class, ReactionObserverHttpTriggerAutoConfiguration.class))
                .withPropertyValues(MODEL_VERSION_2)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ReactionObservingHandlerInterceptor.class);
                });
    }

    @Test
    void modelVersion2_withoutSpringMvc_nothingIsConfigured() {
        contextRunner.withPropertyValues(MODEL_VERSION_2)
                .withClassLoader(new FilteredClassLoader(DispatcherServlet.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ReactionObservingHandlerInterceptor.class);
                });
    }

    @Test
    void modelVersion2_componentNameDiffersFromTheProvider_warnsAtStart(CapturedOutput output) {
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.messaging.kafka.service-name=order")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactionObservingHandlerInterceptor.class);
                    assertThat(output).contains(COMPONENT_NAME_WARNING).contains("'order-service'").contains("'order'");
                });
    }

    @Test
    void modelVersion2_componentNameEqualsTheProvider_doesNotWarn(CapturedOutput output) {
        contextRunner.withPropertyValues(MODEL_VERSION_2, "jeap.messaging.kafka.service-name=order-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactionObservingHandlerInterceptor.class);
                    assertThat(output).doesNotContain(COMPONENT_NAME_WARNING);
                });
    }

    /**
     * The simple class names of the interceptors in the order Spring MVC applies them: the configurers are
     * called in the order Spring MVC injects them, which {@code orderedStream} reproduces.
     */
    private static List<String> interceptorsInOrder(ApplicationContext context) {
        ExposingInterceptorRegistry registry = new ExposingInterceptorRegistry();
        context.getBeanProvider(WebMvcConfigurer.class).orderedStream()
                .forEach(configurer -> configurer.addInterceptors(registry));
        return registry.interceptorsInOrder().stream().map(interceptor -> interceptor.getClass().getSimpleName()).toList();
    }

    /**
     * Gives access to the interceptors of a registry in the order Spring MVC applies them.
     */
    private static class ExposingInterceptorRegistry extends InterceptorRegistry {

        List<Object> interceptorsInOrder() {
            return getInterceptors().stream()
                    .map(interceptor -> interceptor instanceof MappedInterceptor mapped ? mapped.getInterceptor() : interceptor)
                    .toList();
        }
    }

    /**
     * A service registers interceptors with the highest precedence and the one after it, through a configurer
     * without an order of its own. Its configuration is registered before the auto-configurations.
     */
    @Configuration(proxyBeanMethods = false)
    static class ServiceInterceptorConfiguration {

        @Bean
        WebMvcConfigurer serviceWebMvcConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new NextPrecedenceServiceInterceptor()).order(Ordered.HIGHEST_PRECEDENCE + 1);
                    registry.addInterceptor(new HighestPrecedenceServiceInterceptor()).order(Ordered.HIGHEST_PRECEDENCE);
                }
            };
        }
    }

    static class HighestPrecedenceServiceInterceptor implements HandlerInterceptor {
    }

    static class NextPrecedenceServiceInterceptor implements HandlerInterceptor {
    }

    @Configuration(proxyBeanMethods = false)
    static class RecorderConfiguration {

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }
    }
}
