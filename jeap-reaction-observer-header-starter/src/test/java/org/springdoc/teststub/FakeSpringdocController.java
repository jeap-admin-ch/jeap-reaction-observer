package org.springdoc.teststub;

/**
 * Stand-in for a real springdoc controller, placed under the {@code org.springdoc} package purely so
 * {@link ch.admin.bit.jeap.reaction.observer.header.OperationNameResolverTest} can verify the
 * package-prefix exclusion without depending on springdoc internals.
 */
public class FakeSpringdocController {

    public String openApiJson() {
        return "{}";
    }
}
