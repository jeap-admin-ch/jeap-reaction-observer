package ch.admin.bit.jeap.reaction.observer.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTriggerSuccessRuleTest {

    private final HttpTriggerSuccessRule rule = new HttpTriggerSuccessRule();

    @ParameterizedTest(name = "status {0}, has actions: {1} -> counts: {2}")
    @CsvSource({
            "200, false, true",
            "200, true, true",
            "204, false, true",
            "302, false, true",
            "399, false, true",
            "400, false, false",
            "400, true, true",
            "404, false, false",
            "409, true, true",
            "429, false, false",
            "499, false, false",
            "499, true, true",
            "500, false, false",
            "500, true, false",
            "503, true, false"
    })
    void counts_byStatusAndActions(int status, boolean hasActions, boolean expected) {
        assertThat(rule.counts(status, null, hasActions)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "status {0}, has actions: {1}")
    @CsvSource({
            "200, false",
            "200, true",
            "409, true",
            "500, true"
    })
    void counts_neverWithAnUnhandledException(int status, boolean hasActions) {
        // With an unhandled exception the status is still the one set before, usually 200
        assertThat(rule.counts(status, new IllegalStateException("unhandled"), hasActions)).isFalse();
    }
}
