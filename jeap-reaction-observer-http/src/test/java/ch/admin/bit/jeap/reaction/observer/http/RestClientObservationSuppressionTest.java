package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientObservationSuppressionTest {

    private final List<RestClient.Builder> customized = new ArrayList<>();
    private final RestClientCustomizer customizer = RestClientObservationSuppression.unlessSuppressed(customized::add);

    @Test
    void notSuppressed_customizerIsApplied() {
        RestClient.Builder builder = RestClient.builder();

        customizer.customize(builder);

        assertThat(customized).containsExactly(builder);
    }

    @Test
    void suppressedBuilder_customizerIsNotApplied() {
        RestClient.Builder builder = RestClient.builder();

        RestClient.Builder configured = RestClientObservationSuppression.configureSuppressed(builder, this::customize);

        assertThat(configured).isSameAs(builder);
        assertThat(customized).isEmpty();
    }

    /**
     * A customizer applied to the suppressed builder may create a builder of its own, which has to be
     * observed as usual.
     */
    @Test
    void otherBuilderConfiguredMeanwhile_customizerIsApplied() {
        RestClient.Builder other = RestClient.builder();

        RestClientObservationSuppression.configureSuppressed(RestClient.builder(), suppressed -> {
            customizer.customize(other);
            return customize(suppressed);
        });

        assertThat(customized).containsExactly(other);
    }

    /**
     * A builder that another thread configures at the same time has to be observed as usual.
     */
    @Test
    void otherBuilderConfiguredMeanwhileInAnotherThread_customizerIsApplied() {
        RestClient.Builder other = RestClient.builder();

        RestClientObservationSuppression.configureSuppressed(RestClient.builder(), suppressed -> {
            CompletableFuture.runAsync(() -> customizer.customize(other)).join();
            return customize(suppressed);
        });

        assertThat(customized).containsExactly(other);
    }

    /**
     * The end of a nested suppression must not end the enclosing one.
     */
    @Test
    void nestedSuppression_enclosingBuilderStaysSuppressed() {
        RestClientObservationSuppression.configureSuppressed(RestClient.builder(), enclosing -> {
            RestClientObservationSuppression.configureSuppressed(RestClient.builder(), this::customize);
            return customize(enclosing);
        });

        assertThat(customized).isEmpty();
    }

    @Test
    void afterTheSuppression_customizerIsAppliedAgain() {
        RestClient.Builder builder = RestClient.builder();
        RestClientObservationSuppression.configureSuppressed(builder, this::customize);

        customizer.customize(builder);

        assertThat(customized).containsExactly(builder);
    }

    @Test
    void configurationFails_suppressionEndsAndTheFailurePasses() {
        RestClient.Builder builder = RestClient.builder();
        IllegalStateException failure = new IllegalStateException("not configured");

        assertThatThrownBy(() -> RestClientObservationSuppression.configureSuppressed(builder, suppressed -> {
            throw failure;
        })).isSameAs(failure);

        customizer.customize(builder);
        assertThat(customized).containsExactly(builder);
    }

    private RestClient.Builder customize(RestClient.Builder builder) {
        customizer.customize(builder);
        return builder;
    }
}
