package ch.admin.bit.jeap.reaction.observer.header;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.ApplicationContextAssertProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context tests for the auto-configuration itself: active (with its beans) in a servlet web application,
 * inactive when the kill switch is off, and inactive in an application that is not a servlet web
 * application at all.
 */
class ReactionObserverHeaderAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactionObserverHeaderAutoConfiguration.class));

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReactionObserverHeaderAutoConfiguration.class));

    @Test
    void servletWebApplication_byDefault_registersTheHeaderBeans() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReactionObservationHeaderInterceptor.class);
            assertThat(context).hasSingleBean(OperationNameResolver.class);
            assertThat(context).getBean("jeapReactionObserverHeaderProtectionFilter")
                    .isInstanceOf(FilterRegistrationBean.class);
        });
    }

    @Test
    void servletWebApplication_withUnrelatedAppBeanNamedLikeAGenericBean_startsWithoutClash() {
        webContextRunner
                .withAllowBeanDefinitionOverriding(false)
                .withBean("operationNameResolver", String.class, () -> "unrelated")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OperationNameResolver.class);
                });
    }

    @Test
    void servletWebApplication_withCustomOperationNameResolver_usesIt() {
        OperationNameResolver custom = new OperationNameResolver("custom-app");
        webContextRunner
                .withBean("customResolver", OperationNameResolver.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(OperationNameResolver.class);
                    assertThat(context.getBean(OperationNameResolver.class)).isSameAs(custom);
                    assertThat(context).hasSingleBean(ReactionObservationHeaderInterceptor.class);
                });
    }

    @Test
    void servletWebApplication_killSwitchOff_registersNoHeaderBeans() {
        webContextRunner.withPropertyValues("jeap.reaction.observer.header.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(OperationNameResolver.class);
            assertThat(context).doesNotHaveBean(ReactionObservationHeaderInterceptor.class);
            assertThat(context).doesNotHaveBean("jeapReactionObserverHeaderWebMvcConfigurer");
            assertThat(context).doesNotHaveBean("jeapReactionObserverHeaderProtectionFilter");
        });
    }

    @Test
    void servletWebApplication_tracingApplicationNameInKebabCase_isUsedAsProvider() {
        webContextRunner
                .withPropertyValues(
                        "jeap.rest.tracing.application-name=tracing-app",
                        "spring.application.name=spring-app")
                .run(context -> assertThat(resolveSampleOperation(context.getBean(OperationNameResolver.class)))
                        .contains("tracing-app GET /api/items"));
    }

    @Test
    void servletWebApplication_withoutApplicationName_andUnresolvableTracingDefault_startsWithoutProvider() {
        webContextRunner
                .withPropertyValues("jeap.rest.tracing.applicationName=${spring.application.name}")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(resolveSampleOperation(context.getBean(OperationNameResolver.class))).isEmpty();
                });
    }

    @Test
    void notAWebApplication_registersNoHeaderBeans() {
        nonWebContextRunner.run(this::assertNoHeaderBeans);
    }

    @Test
    void servletWebApplication_withoutSpringMvc_registersNoHeaderBeans() {
        webContextRunner.withClassLoader(new FilteredClassLoader(DispatcherServlet.class)).run(this::assertNoHeaderBeans);
    }

    private static Optional<String> resolveSampleOperation(OperationNameResolver resolver) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/items");
        HandlerFunction<ServerResponse> handler = req -> ServerResponse.ok().build();
        return resolver.resolve(request, handler);
    }

    private void assertNoHeaderBeans(ApplicationContextAssertProvider<?> context) {
        assertThat(context).doesNotHaveBean(ReactionObservationHeaderInterceptor.class);
        assertThat(context).doesNotHaveBean(OperationNameResolver.class);
        assertThat(context).doesNotHaveBean("jeapReactionObserverHeaderProtectionFilter");
    }
}
