package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.teststub.FakeSpringdocController;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OperationNameResolverTest {

    private final OperationNameResolver resolver = new OperationNameResolver("test-app");

    @Test
    void fromEnvironment_prefersTracingApplicationName() {
        OperationNameResolver operationNameResolver = OperationNameResolver.fromEnvironment(new MockEnvironment()
                .withProperty("jeap.rest.tracing.applicationName", "tracing-app")
                .withProperty("spring.application.name", "spring-app"));
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("tracing-app GET /api/items");
    }

    @Test
    void fromEnvironment_fallsBackToSpringApplicationName() {
        OperationNameResolver operationNameResolver = OperationNameResolver.fromEnvironment(new MockEnvironment()
                .withProperty("spring.application.name", "spring-app"));
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("spring-app GET /api/items");
    }

    /**
     * A caller drops the spaces at the start and the end of the name it reads from the response header. The
     * provider has to name its operations the same way, or caller and provider report different operations.
     */
    @ParameterizedTest
    @ValueSource(strings = {" test-app", "test-app ", "\u00a0test-app\u2003", "  test-app  "})
    void spacesAtTheEdgesOfTheProviderName_areDropped(String provider) {
        OperationNameResolver operationNameResolver = new OperationNameResolver(provider);
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.provider()).isEqualTo("test-app");
        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("test-app GET /api/items");
    }

    @Test
    void fromEnvironment_dropsSpacesAtTheEdgesOfTheName() {
        OperationNameResolver operationNameResolver = OperationNameResolver.fromEnvironment(new MockEnvironment()
                .withProperty("spring.application.name", " spring-app "));
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("spring-app GET /api/items");
    }

    /**
     * A name of spaces only is no name, whatever kind of space: the fallback applies.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\u00a0", " \u2003 "})
    void fromEnvironment_fallsBackWhenTracingApplicationNameHasSpacesOnly(String tracingApplicationName) {
        OperationNameResolver operationNameResolver = OperationNameResolver.fromEnvironment(new MockEnvironment()
                .withProperty("jeap.rest.tracing.applicationName", tracingApplicationName)
                .withProperty("spring.application.name", "spring-app"));
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("spring-app GET /api/items");
    }

    /**
     * No operation name starts or ends with a space, wherever the space comes from.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/items\u00a0", "/api/items\u2003/", "/api/items "})
    void resolve_spacesAtTheEndOfThePattern_areDropped(String pattern) {
        HttpServletRequest request = requestWithPattern("GET", pattern);
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(resolver.resolve(request, handlerFunction)).contains("test-app GET /api/items");
    }

    @Test
    void fromEnvironment_fallsBackWhenTracingApplicationNameIsBlank() {
        OperationNameResolver operationNameResolver = OperationNameResolver.fromEnvironment(new MockEnvironment()
                .withProperty("jeap.rest.tracing.applicationName", " ")
                .withProperty("spring.application.name", "spring-app"));
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        assertThat(operationNameResolver.resolve(request, handlerFunction)).contains("spring-app GET /api/items");
    }

    @Test
    void resolve_controllerMethod_withPattern_buildsFqn() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        Optional<String> result = resolver.resolve(request, handler);

        assertThat(result).contains("test-app GET /api/items/{}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\u00a0"})
    void resolve_missingOrBlankProvider_isNotObserved(String provider) throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(new OperationNameResolver(provider).resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_withoutApiVersionAttribute_appendsNoSuffix() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).contains("test-app GET /api/items/{}");
    }

    @Test
    void resolve_withMethodVersion_appendsDeclaredVersionSuffix() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new VersionedController(), "getItem");

        Optional<String> result = resolver.resolve(request, handler);

        assertThat(result).contains("test-app GET /api/items/{} ;v=2");
    }

    @Test
    void resolve_withTypeVersion_appendsDeclaredBaselineSuffix() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new VersionedController(), "getBaseline");

        assertThat(resolver.resolve(request, handler)).contains("test-app GET /api/items/{} ;v=1.2+");
    }

    @Test
    void resolve_withInterfaceMapping_appendsDeclaredVersionSuffix() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items/{id}");
        HandlerMethod handler = handlerMethodOf(new InterfaceController(), "getItem");

        assertThat(resolver.resolve(request, handler)).contains("test-app GET /api/items/{} ;v=3");
    }

    /**
     * Methods are case-sensitive: Spring routes {@code post} to no {@code @PostMapping}, so it is not {@code POST}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"post", "Post", "get", "gEt"})
    void resolve_standardMethodInAnotherCase_isNotObserved(String method) throws Exception {
        HttpServletRequest request = requestWithPattern(method, "/api/items");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_functionalEndpoint_isObserved() {
        HttpServletRequest request = requestWithPattern("POST", "/functional/{id}");
        HandlerFunction<ServerResponse> handlerFunction = (ServerRequest req) -> ServerResponse.ok().build();

        Optional<String> result = resolver.resolve(request, handlerFunction);

        assertThat(result).contains("test-app POST /functional/{}");
    }

    @Test
    void resolve_noPatternAttribute_isNotObserved() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void resolve_blankPattern_isNotObserved(String pattern) throws Exception {
        HttpServletRequest request = requestWithPattern("GET", pattern);
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_unexpectedPatternAttributeType_isNotObserved() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn(new Object());
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    /**
     * A consumer would not accept the name, so the operation is not named: provider and consumer must not
     * identify it differently. The warning shows the invisible character as text, and only once.
     */
    @Test
    void resolve_nameAConsumerWouldNotAccept_isNotObserved_warnedOnce(CapturedOutput output) throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/zero\u200cwidth");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
        assertThat(resolver.resolve(request, handler)).isEmpty();

        assertThat(output.getAll().split("is not named", -1)).hasSize(2);
        assertThat(output).contains("SampleController#getItem[0 args]")
                .contains("'test-app GET /api/zero[U+200C]width'")
                .contains("contains a control character, a line or paragraph separator or an invisible format character")
                .doesNotContain("\u200c");
    }

    @Test
    void resolve_namesAConsumerWouldNotAccept_ofSeveralOperations_eachWarnedAboutOnce(CapturedOutput output)
            throws Exception {
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        for (int i = 0; i < 2; i++) {
            assertThat(resolver.resolve(requestWithPattern("GET", "/api/zero\u200cwidth/a"), handler)).isEmpty();
            assertThat(resolver.resolve(requestWithPattern("GET", "/api/zero\u200cwidth/b"), handler)).isEmpty();
            assertThat(resolver.resolve(requestWithPattern("POST", "/api/zero\u200cwidth/a"), handler)).isEmpty();
        }

        assertThat(output.getAll().split("is not named", -1)).hasSize(4);
        assertThat(output).contains("GET /api/zero[U+200C]width/a'", "GET /api/zero[U+200C]width/b'",
                "POST /api/zero[U+200C]width/a'");
    }

    /**
     * A service that registers mappings at runtime has no fixed number of operations: the number of warnings has
     * a fixed bound all the same.
     */
    @Test
    void resolve_namesAConsumerWouldNotAccept_ofMoreOperationsThanTheLimit_warnedAboutAtMostTheLimit(
            CapturedOutput output) throws Exception {
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        for (int i = 0; i < OperationNameResolver.MAX_WARNED_NAMES + 5; i++) {
            assertThat(resolver.resolve(requestWithPattern("GET", "/api/zero\u200cwidth/" + i), handler)).isEmpty();
        }

        assertThat(output.getAll().split("is not named", -1)).hasSize(OperationNameResolver.MAX_WARNED_NAMES + 1);
        assertThat(output).contains("width/0'").doesNotContain("width/" + OperationNameResolver.MAX_WARNED_NAMES + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "QUERY"})
    void resolve_standardMethod_isObserved(String method) throws Exception {
        HttpServletRequest request = requestWithPattern(method, "/api/items");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).contains("test-app " + method + " /api/items");
    }

    /**
     * A mapping without a method condition accepts any method a client sends. Made-up methods must not make an
     * unbounded number of operations out of one mapping; they are not named, and not warned about either.
     */
    @ParameterizedTest
    @ValueSource(strings = {"FOO", "PROPFIND", "M-SEARCH", "GETX"})
    void resolve_otherMethod_isNotObserved_notWarnedAbout(String method, CapturedOutput output) throws Exception {
        HttpServletRequest request = requestWithPattern(method, "/api/items");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(resolver.resolve(request, handler)).isEmpty();
        assertThat(output).doesNotContain("is not named");
    }

    @Test
    void resolve_providerNameOfACoarseAction_isNotObserved() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/api/items");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        assertThat(new OperationNameResolver("//test-app").resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_arbitraryHandler_likeAStaticResourceHandler_isNotObserved() {
        HttpServletRequest request = requestWithPattern("GET", "/robots.txt");

        assertThat(resolver.resolve(request, new Object())).isEmpty();
    }

    @Test
    void resolve_basicErrorController_isNotObserved() {
        HttpServletRequest request = requestWithPattern("GET", "/error");
        BasicErrorController errorController = mock(BasicErrorController.class);
        HandlerMethod handler = new HandlerMethod(errorController, errorController.getClass().getMethods()[0]);

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_basicErrorControllerSubclass_isNotObserved() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/error");
        CustomErrorController errorController = mock(CustomErrorController.class);
        HandlerMethod handler = handlerMethodOf(errorController, "customError");
        assertThat(handler.getBeanType()).isEqualTo(CustomErrorController.class);

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @Test
    void resolve_springdocHandler_isNotObserved() throws Exception {
        HttpServletRequest request = requestWithPattern("GET", "/v3/api-docs");
        HandlerMethod handler = handlerMethodOf(new FakeSpringdocController(), "openApiJson");

        assertThat(resolver.resolve(request, handler)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {512, 513})
    void resolve_fqnAtLengthBoundary_isShortenedOnlyAboveLimit(int length) throws Exception {
        String prefix = "test-app GET ";
        String pattern = "/" + "x".repeat(length - prefix.length() - 1);
        String fullFqn = prefix + pattern;
        assertThat(fullFqn).hasSize(length);
        HttpServletRequest request = requestWithPattern("GET", pattern);
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        String fqn = resolver.resolve(request, handler).orElseThrow();

        if (length == OperationNameResolver.MAX_LENGTH) {
            assertThat(fqn).isEqualTo(fullFqn);
        } else {
            assertThat(fqn).isEqualTo(fullFqn.substring(0, OperationNameResolver.MAX_LENGTH - 65)
                    + "~" + sha256Hex(fullFqn));
            assertThat(fqn).hasSize(OperationNameResolver.MAX_LENGTH);
        }
    }

    @Test
    void resolve_longFqnsDifferingAfterCut_haveDistinctStableSuffixes() throws Exception {
        String commonPattern = "/api/" + "x".repeat(600);
        HttpServletRequest firstRequest = requestWithPattern("GET", commonPattern + "a");
        HttpServletRequest secondRequest = requestWithPattern("GET", commonPattern + "b");
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");

        String firstFqn = resolver.resolve(firstRequest, handler).orElseThrow();
        String secondFqn = resolver.resolve(secondRequest, handler).orElseThrow();
        int suffixStart = OperationNameResolver.MAX_LENGTH - 65;

        assertThat(firstFqn).hasSize(OperationNameResolver.MAX_LENGTH);
        assertThat(secondFqn).hasSize(OperationNameResolver.MAX_LENGTH);
        assertThat(firstFqn.substring(0, suffixStart)).isEqualTo(secondFqn.substring(0, suffixStart));
        assertThat(firstFqn.substring(suffixStart)).isNotEqualTo(secondFqn.substring(suffixStart));
        assertThat(firstFqn).endsWith("~" + sha256Hex("test-app GET " + commonPattern + "a"));
        assertThat(secondFqn).endsWith("~" + sha256Hex("test-app GET " + commonPattern + "b"));
        assertThat(resolver.resolve(firstRequest, handler)).contains(firstFqn);
        assertThat(resolver.resolve(secondRequest, handler)).contains(secondFqn);
    }

    @Test
    void resolve_veryLongFqn_isShortenedWithSha256Suffix() throws Exception {
        String longPattern = "/api/" + "x".repeat(600);
        HttpServletRequest request = requestWithPattern("GET", longPattern);
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");
        String fullFqn = "test-app GET " + longPattern;
        assertThat(fullFqn.length()).isGreaterThan(OperationNameResolver.MAX_LENGTH);
        String expectedSuffix = "~" + sha256Hex(fullFqn);
        assertThat(expectedSuffix).hasSize(65);

        String fqn = resolver.resolve(request, handler).orElseThrow();

        assertThat(fqn).hasSize(OperationNameResolver.MAX_LENGTH);
        assertThat(fqn).endsWith(expectedSuffix);
        assertThat(fqn).startsWith(fullFqn.substring(0, OperationNameResolver.MAX_LENGTH - expectedSuffix.length()));
    }

    @Test
    void resolve_veryLongFqn_doesNotSplitSurrogatePairAtCut() throws Exception {
        // "test-app GET " (13) + "/api/" (5) + 428 = 446: a high surrogate lands on the last kept index
        String longPattern = "/api/" + "x".repeat(428) + "\uD83D\uDE00".repeat(100);
        HttpServletRequest request = requestWithPattern("GET", longPattern);
        HandlerMethod handler = handlerMethodOf(new SampleController(), "getItem");
        String expectedSuffix = "~" + sha256Hex("test-app GET " + longPattern);

        String fqn = resolver.resolve(request, handler).orElseThrow();

        assertThat(fqn)
                .hasSize(OperationNameResolver.MAX_LENGTH - 1)
                .endsWith(expectedSuffix);
        String kept = fqn.substring(0, fqn.length() - expectedSuffix.length());
        assertThat(Character.isHighSurrogate(kept.charAt(kept.length() - 1))).isFalse();
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static HttpServletRequest requestWithPattern(String method, String pattern) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn(pattern);
        return request;
    }

    private static HandlerMethod handlerMethodOf(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    static class SampleController {
        public String getItem() {
            return "item";
        }
    }

    @RequestMapping(version = "1.2+")
    static class VersionedController {
        @GetMapping(version = "2")
        public String getItem() {
            return "item";
        }

        @GetMapping
        public String getBaseline() {
            return "baseline";
        }
    }

    interface VersionedApi {
        @GetMapping(version = "3")
        String getItem();
    }

    static class InterfaceController implements VersionedApi {
        @Override
        public String getItem() {
            return "item";
        }
    }

    static class CustomErrorController extends BasicErrorController {
        CustomErrorController() {
            super(null, null);
        }

        public String customError() {
            return "error";
        }
    }
}
