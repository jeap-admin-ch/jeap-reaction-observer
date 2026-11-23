package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.header.OperationNameResolver;
import ch.admin.bit.jeap.reaction.observer.http.ReactionObservationHeader.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionObservationHeaderTest {

    /**
     * The reaction graph joins the call of a consumer with the request of the provider by the id of the
     * observation. Both sides have to make the same id of the name of the operation, also if the provider is
     * configured with a name that starts or ends with a space, or if the path of the operation ends with one.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
            'order-service'        | '/api/orders'
            ' order-service'       | '/api/orders'
            'order-service '       | '/api/orders'
            '\u00a0order-service'  | '/api/orders'
            'order-service'        | '/api/orders\u00a0'
            '\u2003order-service ' | '/api/orders\u2003/'
            """)
    void consumerAndProvider_identifyTheOperationAlike(String configuredProviderName, String pathPattern) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pathPattern);
        HandlerFunction<ServerResponse> handler = serverRequest -> ServerResponse.ok().build();
        String providerFqn = new OperationNameResolver(configuredProviderName).resolve(request, handler).orElseThrow();

        ReactionObservationHeader header = ReactionObservationHeader.parse("%\"" + providerFqn + "\"");

        assertThat(header.isValid()).isTrue();
        assertThat(Observation.ofHttpRequest(header.fqn()).id())
                .describedAs("the id of the action of the consumer and of the trigger of the provider")
                .isEqualTo(Observation.ofHttpRequest(providerFqn).id());
    }

    /**
     * The shared test cases of docs/response-header.md: the encoder of the header module produces these values,
     * and this parser reads them back.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\0', textBlock = """
            %"partner-service GET /api/partners/{}" | partner-service GET /api/partners/{}
            %"svc GET /api/100%25"                  | svc GET /api/100%
            %"svc GET /api/%22quoted%22"            | svc GET /api/"quoted"
            %"svc GET /api/a,b"                     | svc GET /api/a,b
            %"svc GET /api/{*}"                     | svc GET /api/{*}
            %"svc GET /api/x ;v=2"                  | svc GET /api/x ;v=2
            %"svc GET /api/caf%c3%a9"               | svc GET /api/café
            """)
    void parse_sharedTestCases(String headerValue, String expectedFqn) {
        ReactionObservationHeader header = ReactionObservationHeader.parse(headerValue);

        assertThat(header.status()).isEqualTo(Status.VALID);
        assertThat(header.isValid()).isTrue();
        assertThat(header.fqn()).isEqualTo(expectedFqn);
    }

    /**
     * The header module encodes a control character correctly, but no operation name has one. The consumer
     * does not let it into observation ids, events and logs.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "%\"svc GET /api/a%09b\"",
            "%\"svc GET /api/a%0ab\"",
            "%\"svc GET /api/a%0d%0ab\"",
            "%\"svc GET /api/a%00b\"",
            "%\"svc GET /api/a%1bb\"",
            "%\"svc GET /api/a%7fb\"",
            "%\"svc GET /api/a%c2%85b\"",
            "%\"%0asvc GET /api/a\"",
            // Unicode line separator, paragraph separator, right-to-left override, zero-width space, BOM
            "%\"svc GET /api/a%e2%80%a8b\"",
            "%\"svc GET /api/a%e2%80%a9b\"",
            "%\"svc GET /api/a%e2%80%aeb\"",
            "%\"svc GET /api/a%e2%80%8bb\"",
            "%\"svc GET /api/a%ef%bb%bfb\"",
            // Only spaces, also no-break spaces
            "%\"   \"",
            "%\"%c2%a0\"",
            "%\" %c2%a0%e2%80%af\""
    })
    void parse_nameWithControlOrInvisibleCharacter_isInvalid(String headerValue) {
        assertInvalid(headerValue);
    }

    @Test
    void parse_shortenedFqnOfTheHeaderModule_isValid() {
        String shortened = "test-app GET /" + "x".repeat(433) + "~24a4cf8ed5134d5a7a78c86193c1390e24d4468f664c56b40b3c8c38c3e3e8d6";

        ReactionObservationHeader header = ReactionObservationHeader.parse("%\"" + shortened + "\"");

        assertThat(shortened).hasSize(512);
        assertThat(header.fqn()).isEqualTo(shortened);
    }

    @Test
    void parse_noHeader_isAbsent() {
        ReactionObservationHeader header = ReactionObservationHeader.parse(null);

        assertThat(header.status()).isEqualTo(Status.ABSENT);
        assertThat(header.isValid()).isFalse();
        assertThat(header.fqn()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\0', textBlock = """
            %"svc GET /api/a,b", %"other GET /api/x" | svc GET /api/a,b
            %"svc GET /api/a",%"other GET /api/x"    | svc GET /api/a
            %"svc GET /api/a";p=1                    | svc GET /api/a
            %"svc GET /api/a" ; p=1, %"other"        | svc GET /api/a
            %"svc GET /api/a", not-a-display-string  | svc GET /api/a
            """)
    void parse_listWithSeveralMembersOrParameters_firstMemberCounts(String headerValue, String expectedFqn) {
        assertThat(ReactionObservationHeader.parse(headerValue).fqn()).isEqualTo(expectedFqn);
    }

    /**
     * Spaces at the start and end of the name - also no-break spaces - are dropped: they neither make a name
     * that looks the same a different one nor hide the prefix of a coarse action.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '\0', textBlock = """
            %" svc GET /api/a"           | svc GET /api/a
            %"svc GET /api/a  "          | svc GET /api/a
            %"%c2%a0svc GET /api/a%c2%a0" | svc GET /api/a
            %"%e2%80%afsvc GET /api/a"    | svc GET /api/a
            """)
    void parse_spacesAroundTheName_areDropped(String headerValue, String expectedFqn) {
        ReactionObservationHeader header = ReactionObservationHeader.parse(headerValue);

        assertThat(header.status()).isEqualTo(Status.VALID);
        assertThat(header.fqn()).isEqualTo(expectedFqn);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "%\" //evil.host GET /{*}\"",
            "%\"%c2%a0//evil.host GET /{*}\""
    })
    void parse_coarseActionBehindSpaces_isInvalid(String headerValue) {
        assertThat(ReactionObservationHeader.parse(headerValue).status()).isEqualTo(Status.INVALID);
    }

    @Test
    void parse_surroundingWhitespace_isIgnored() {
        assertThat(ReactionObservationHeader.parse("  %\"svc GET /api/a\" \t").fqn()).isEqualTo("svc GET /api/a");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "svc GET /api/a",
            "\"svc GET /api/a\"",
            "%svc GET /api/a",
            "%\"svc GET /api/a",
            "%\"svc GET /api/a\"x",
            "%\"svc GET /api/a\" %\"other\"",
            "%\"svc GET /api/100%\"",
            "%\"svc GET /api/100%2\"",
            "%\"svc GET /api/caf%C3%A9\"",
            "%\"svc GET /api/caf%zz\"",
            "%\"svc GET /api/caf%c3\"",
            "%\"svc GET /api/caf%e9\"",
            "%\"svc GET /api/café\"",
            "%\"svc GET /api/a\tb\"",
            "%\"\"",
            "%\"   \"",
            "not-a-display-string, %\"svc GET /api/a\""
    })
    void parse_malformedValue_isInvalid(String headerValue) {
        assertInvalid(headerValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "%\"//foo.bar.admin.ch GET /{*}\"",
            "%\"//foo.bar.admin.ch/some-service POST /api/x\"",
            "%\"// GET /api/x\""
    })
    void parse_providerStartingWithTheCoarsePrefix_isInvalid(String headerValue) {
        assertThat(ReactionObservationHeader.parse(headerValue).status()).isEqualTo(Status.INVALID);
    }

    /**
     * The length limit protects the consumer and applies to the whole header line: an oversized line is not
     * parsed at all, also if its first member would be a valid name.
     */
    @Test
    void parse_oversizedLineWithValidFirstMember_isInvalidOnPurpose() {
        String validFirstMember = "%\"svc GET /api/a\"";
        String oversizedLine = validFirstMember + (", " + validFirstMember).repeat(400);

        assertThat(ReactionObservationHeader.parse(validFirstMember).isValid()).isTrue();
        assertThat(oversizedLine).hasSizeGreaterThan(7264);
        assertThat(ReactionObservationHeader.parse(oversizedLine).status()).isEqualTo(Status.INVALID);
    }

    @Test
    void parse_fqnAtTheMaximumLength_isValid() {
        String fqn = "svc GET /" + "x".repeat(ReactionObservationHeader.MAX_FQN_LENGTH - 9);

        assertThat(ReactionObservationHeader.parse("%\"" + fqn + "\"").fqn()).isEqualTo(fqn);
    }

    @Test
    void parse_oversizedValue_isInvalid() {
        String tooLong = "svc GET /" + "x".repeat(ReactionObservationHeader.MAX_FQN_LENGTH - 8);
        String wayTooLong = "svc GET /" + "x".repeat(100_000);

        assertThat(ReactionObservationHeader.parse("%\"" + tooLong + "\"").status()).isEqualTo(Status.INVALID);
        assertThat(ReactionObservationHeader.parse("%\"" + wayTooLong + "\"").status()).isEqualTo(Status.INVALID);
    }

    private static void assertInvalid(String headerValue) {
        ReactionObservationHeader header = ReactionObservationHeader.parse(headerValue);

        assertThat(header.status()).isEqualTo(Status.INVALID);
        assertThat(header.fqn()).isNull();
    }
}
