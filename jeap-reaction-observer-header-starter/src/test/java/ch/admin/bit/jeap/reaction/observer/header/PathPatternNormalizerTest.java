package ch.admin.bit.jeap.reaction.observer.header;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PathPatternNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "/api/openapi/{systemComponentName},/api/openapi/{}",
            "/api/openapi/{componentName}/,/api/openapi/{}",
            "/api/{one}/and/{two},/api/{}/and/{}",
            "/api/v4/businesspartner/,/api/v4/businesspartner",
            "/api/items/{id:[0-9]+},/api/items/{}",
            "/api/items/{id:[0-9]{3}},/api/items/{}",
    })
    void normalize_replacesVariablesAndDropsTrailingSlash(String pattern, String expected) {
        assertThat(PathPatternNormalizer.normalize(pattern)).isEqualTo(expected);
    }

    @Test
    void normalize_catchAllVariable_becomesStarPlaceholder() {
        assertThat(PathPatternNormalizer.normalize("/api/{*rest}")).isEqualTo("/api/{*}");
    }

    @Test
    void normalize_catchAllVariable_withTrailingSlash_dropsTheSlash() {
        assertThat(PathPatternNormalizer.normalize("/api/{*rest}/")).isEqualTo("/api/{*}");
    }

    @Test
    void normalize_rootPath_keepsItsSlash() {
        assertThat(PathPatternNormalizer.normalize("/")).isEqualTo("/");
    }

    @Test
    void normalize_null_isEmpty() {
        assertThat(PathPatternNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void normalize_blank_isEmpty() {
        assertThat(PathPatternNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    void normalize_unmatchedBrace_doesNotThrow() {
        assertThat(PathPatternNormalizer.normalize("/api/{unterminated"))
                .isEqualTo("/api/{unterminated");
    }

    @Test
    void normalize_noVariables_isUnchanged() {
        assertThat(PathPatternNormalizer.normalize("/actuator/health")).isEqualTo("/actuator/health");
    }
}
