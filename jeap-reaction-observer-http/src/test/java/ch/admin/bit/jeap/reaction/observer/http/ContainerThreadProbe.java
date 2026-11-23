package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test fixture for a servlet container that handles all requests in one thread
 * ({@code server.tomcat.threads.max=1}). A trigger left open by a request would be the trigger of everything the
 * thread does afterwards. The probe is a request to {@value #PATH} that records an action in the thread of the
 * container, outside the dispatcher servlet: outside a trigger the action counts as a reaction of its own, inside
 * a trigger left open it would not.
 * <p>
 * Because there is only one thread, the probe is also handled only after the request before it has been
 * completed, whatever the client has received by then. What has been counted can be asserted right after it.
 */
final class ContainerThreadProbe {

    static final String PATH = "/probe";
    static final int STATUS = HttpServletResponse.SC_NO_CONTENT;
    private static final Observation PROBE = Observation.ofEvent("Probe", "probe-topic");

    private ContainerThreadProbe() {
    }

    /**
     * The filter answering the probe, in front of all other filters.
     */
    static FilterRegistrationBean<Filter> filter(ReactionRecorder reactionRecorder) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            reactionRecorder.onAction(PROBE);
            ((HttpServletResponse) response).setStatus(STATUS);
        });
        registration.addUrlPatterns(PATH);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Asserts that no trigger is left open in the thread of the container, and that exactly the given reactions
     * have been counted once since the last call.
     *
     * @param probeStatus the status of the response to the probe, sent after the requests under test
     */
    static void assertCounted(ObservedReactions reactions, int probeStatus, Reaction... expected) {
        assertThat(probeStatus).isEqualTo(STATUS);
        Map<String, Integer> counts = reactions.countsSinceLastCall();
        String probeReactionId = Reaction.actionOnly(PROBE).id();
        assertThat(counts)
                .describedAs("A trigger is left open in the thread of the container: the action of the probe "
                        + "has not been counted as a reaction of its own")
                .containsEntry(probeReactionId, 1);
        counts.remove(probeReactionId);
        assertThat(counts).isEqualTo(Stream.of(expected).collect(Collectors.toMap(Reaction::id, reaction -> 1)));
    }
}
