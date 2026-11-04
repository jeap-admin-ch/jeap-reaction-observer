package ch.admin.bit.jeap.reaction.observer.header;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared test cases documented in {@code docs/response-header.md}: values with {@code %}, {@code "},
 * a comma, non-ASCII characters, {@code {*}}, {@code ;v=2} and a shortened FQN. The parser in the Reaction
 * Observer HTTP module round-trips these same cases.
 */
class ReactionObservationEncoderTest {

    @Test
    void encode_plainFqn_wrapsInDisplayString() {
        assertThat(ReactionObservationEncoder.encode("partner-service GET /api/partners/{}"))
                .isEqualTo("%\"partner-service GET /api/partners/{}\"");
    }

    @Test
    void encode_percentSign_isPercentEncoded() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/100%"))
                .isEqualTo("%\"svc GET /api/100%25\"");
    }

    @Test
    void encode_doubleQuote_isPercentEncoded() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/\"quoted\""))
                .isEqualTo("%\"svc GET /api/%22quoted%22\"");
    }

    @Test
    void encode_comma_isKeptLiteral() {
        // A comma does not need escaping: it is inside the quoted display string, so a structured-field
        // parser does not mistake it for a list separator.
        assertThat(ReactionObservationEncoder.encode("svc GET /api/a,b"))
                .isEqualTo("%\"svc GET /api/a,b\"");
    }

    @Test
    void encode_catchAllPlaceholder_isKeptLiteral() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/{*}"))
                .isEqualTo("%\"svc GET /api/{*}\"");
    }

    @Test
    void encode_apiVersionSuffix_isKeptLiteral() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/x ;v=2"))
                .isEqualTo("%\"svc GET /api/x ;v=2\"");
    }

    @Test
    void encode_nonAscii_isPercentEncodedAsLowercaseUtf8Bytes() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/café"))
                .isEqualTo("%\"svc GET /api/caf%c3%a9\"");
    }

    @Test
    void encode_controlCharacter_isPercentEncoded() {
        assertThat(ReactionObservationEncoder.encode("svc GET /api/a\tb"))
                .isEqualTo("%\"svc GET /api/a%09b\"");
    }

    @Test
    void encode_alreadyShortenedFqn_withSha256Suffix_isKeptLiteral() {
        String shortened = "a".repeat(447) + "~" + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertThat(ReactionObservationEncoder.encode(shortened))
                .isEqualTo("%\"" + shortened + "\"");
    }
}
