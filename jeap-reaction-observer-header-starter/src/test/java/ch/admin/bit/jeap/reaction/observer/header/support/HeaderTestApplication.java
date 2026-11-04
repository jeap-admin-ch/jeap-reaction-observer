package ch.admin.bit.jeap.reaction.observer.header.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application for the header-starter integration tests. Placed in its own
 * {@code support} sub-package (component-scanned by {@code @SpringBootApplication}) so it never picks up
 * the production classes of the parent {@code header} package by accident; those are wired in solely via
 * the module's {@code AutoConfiguration.imports}, exactly as in a real consuming service.
 */
@SpringBootApplication
public class HeaderTestApplication {
}
