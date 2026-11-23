package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.HandlerFunction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the fully qualified name (FQN) of the REST operation a request is routed to, or decides that the
 * operation is not observed.
 * <p>
 * This is the single place that encapsulates the operation-naming rule and the fixed exclusions, so that the
 * trigger-capturing interceptor of the module {@code jeap-reaction-observer-http} can reuse it unchanged
 * instead of re-implementing the rule. It only depends on {@link PathPatternNormalizer} within this module:
 * given a request and its resolved handler, it reads the matched path and mapping annotations and returns
 * a name, nothing more.
 *
 * <pre>
 *   fqn = "&lt;provider&gt; &lt;METHOD&gt; &lt;pattern&gt;[ ;v=&lt;apiVersion&gt;]"
 * </pre>
 *
 * Only {@link HandlerMethod} (controller methods) and {@link HandlerFunction} (functional endpoints,
 * WebMvc.fn) are considered observable; everything else - most notably static resources - is not. Among
 * {@code HandlerMethod}s, the error page controller and springdoc/Swagger are fixed exclusions.
 * The {@code org.springframework.boot.actuate.} and {@code org.springframework.boot.webmvc.actuate.}
 * packages are also excluded. Standard Boot 4 Actuator endpoints use a separate handler mapping that
 * does not receive this module's interceptor, nor the trigger-capturing one, which is registered the same
 * way; the package exclusions additionally protect callers that reach that mapping, such as an interceptor
 * registered as a {@code MappedInterceptor} bean.
 * These exclusions are never configurable.
 * <p>
 * Only requests with one of the standard methods Spring knows ({@link HttpMethod#values()}) are named, compared
 * exactly as sent: methods are case-sensitive, and Spring routes {@code post} to no {@code @PostMapping}. A
 * mapping without a method condition accepts any method a client sends, and the method is part of the name:
 * names of made-up methods would make an unbounded number of operations, and of triggers, out of one mapping. A
 * request with another method is named like a request no mapping handles: not at all, and without a warning.
 * <p>
 * An operation whose name a consumer would not accept, see {@link OperationNameRule}, is not named either: no
 * response header, and its requests are no triggers, so that provider and consumer never identify the operation
 * differently. A consumer treats a call to it like any call answered without the header: a coarse action named
 * by the called target for a status below 500, a failed call for a status of 500 or above. A warning names such
 * an operation once, for at most {@value #MAX_WARNED_NAMES} operations.
 */
@Slf4j
public final class OperationNameResolver {

    /**
     * Request attribute under which the resolved FQN is stored as soon as it is known, for reuse by the
     * trigger-capturing interceptor and so that this module can re-apply the header after an error dispatch
     * that resets response headers. Not part of the frozen contract: the header module and the module with the
     * trigger-capturing interceptor are released from the same repository in lockstep.
     */
    public static final String OPERATION_NAME_ATTRIBUTE = OperationNameResolver.class.getName() + ".OPERATION_NAME";

    /**
     * FQNs longer than this are shortened to {@code MAX_LENGTH} characters total, with the last
     * {@code 1 + 64} characters replaced by {@code ~} followed by the lowercase hex SHA-256 of the untruncated
     * FQN, producing stable names with an extremely low risk of collisions for different long FQNs.
     * The limit counts Java {@code char}s of the unencoded FQN; the cut never splits a surrogate pair (the
     * result is then one character shorter). The encoded header value can be longer for non-ASCII names.
     */
    static final int MAX_LENGTH = 512;

    private static final String BASIC_ERROR_CONTROLLER_CLASS_NAME =
            "org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController";
    private static final String SPRINGDOC_PACKAGE_PREFIX = "org.springdoc.";
    private static final String ACTUATOR_PACKAGE_PREFIX = "org.springframework.boot.actuate.";
    private static final String WEBMVC_ACTUATOR_PACKAGE_PREFIX = "org.springframework.boot.webmvc.actuate.";

    private static final Set<String> STANDARD_METHODS =
            Stream.of(HttpMethod.values()).map(HttpMethod::name).collect(Collectors.toUnmodifiableSet());

    /**
     * At most this many operations whose names a consumer would not accept are warned about, so that memory and
     * log volume have a fixed bound, also for a service that registers mappings at runtime.
     */
    static final int MAX_WARNED_NAMES = 20;

    private final String provider;

    /**
     * The names warned about, each once, at most {@link #MAX_WARNED_NAMES}. Guarded by itself, so that the limit
     * holds also for concurrent requests: checking the size and adding a name are one step.
     */
    private final Set<String> unacceptableNamesWarnedAbout = new HashSet<>();

    /**
     * @param provider the name of the provider every operation name starts with, without the spaces at its start
     *                 and end, see {@link OperationNameRule#stripSpaces(String)}. A name of spaces only is no name.
     */
    public OperationNameResolver(String provider) {
        this.provider = provider == null ? null : OperationNameRule.stripSpaces(provider);
    }

    /**
     * Creates a resolver using the configured tracing application name, falling back to the Spring application
     * name. An empty name disables operation naming.
     * <p>
     * Properties are read with the Spring Boot {@link Binder}, so relaxed binding applies exactly as for the
     * jEAP REST tracing {@code @ConfigurationProperties} that sets {@code JEAP-APPLICATION-NAME}. Unresolvable
     * placeholders (e.g. the tracing default {@code ${spring.application.name}} without an application name)
     * are treated as not set instead of failing application startup.
     */
    public static OperationNameResolver fromEnvironment(Environment environment) {
        Binder binder = Binder.get(environment);
        String provider = bindName(binder, "jeap.rest.tracing.application-name")
                .or(() -> bindName(binder, "spring.application.name"))
                .orElse("");
        if (provider.isEmpty()) {
            log.warn("Neither jeap.rest.tracing.application-name nor spring.application.name is set; "
                    + "operation names cannot be resolved");
        }
        return new OperationNameResolver(provider);
    }

    private static Optional<String> bindName(Binder binder, String propertyName) {
        try {
            var result = binder.bind(propertyName, String.class);
            if (!result.isBound()) {
                return Optional.empty();
            }
            return Optional.of(OperationNameRule.stripSpaces(result.get())).filter(name -> !name.isBlank() && !name.contains("${"));
        } catch (RuntimeException e) {
            log.warn("Could not read {}: {}", propertyName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return the name of the provider every operation name starts with, empty if none is set and operation names
     * cannot be resolved
     */
    public String provider() {
        return provider == null ? "" : provider;
    }

    /**
     * @param request the current request; its {@code HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE}
     *                attribute must already be set, i.e. this
     *                must be called after handler mapping has run (Spring guarantees this before
     *                {@code HandlerInterceptor.preHandle}).
     * @param handler  the handler the request was mapped to.
     * @return the FQN of the operation, or empty if the operation is not observed or no provider name is set.
     */
    public Optional<String> resolve(HttpServletRequest request, Object handler) {
        if (provider == null || provider.isBlank() || !isObservable(handler)) {
            return Optional.empty();
        }
        Object patternAttribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(patternAttribute instanceof String pattern) || pattern.isBlank()) {
            return Optional.empty();
        }
        String method = request.getMethod();
        if (!STANDARD_METHODS.contains(method)) {
            return Optional.empty();
        }
        String normalizedPattern = PathPatternNormalizer.normalize(pattern);
        String apiVersion = extractApiVersion(handler);
        String fqn = buildFqn(method, normalizedPattern, apiVersion);
        Optional<String> violation = OperationNameRule.violation(fqn);
        if (violation.isPresent()) {
            warnOnce(fqn, handler, violation.get());
            return Optional.empty();
        }
        return Optional.of(fqn);
    }

    /**
     * Only requests to an operation that is not named come here, so the lock never delays the others. It is held
     * for the check alone, not while logging.
     */
    private void warnOnce(String fqn, Object handler, String violation) {
        if (isFirstWarningWithinTheLimit(fqn)) {
            String handlerDescription = handler instanceof HandlerMethod handlerMethod
                    ? handlerMethod.getShortLogMessage() : handler.toString();
            log.warn("The operation of the handler {} is not named and its requests are not observed as triggers: "
                            + "its name '{}' {}. Callers treat their calls to it like calls answered without the "
                            + "header. At most {} such operations are warned about.",
                    OperationNameRule.escapeForLog(handlerDescription), OperationNameRule.escapeForLog(fqn), violation,
                    MAX_WARNED_NAMES);
        }
    }

    private boolean isFirstWarningWithinTheLimit(String fqn) {
        synchronized (unacceptableNamesWarnedAbout) {
            return unacceptableNamesWarnedAbout.size() < MAX_WARNED_NAMES && unacceptableNamesWarnedAbout.add(fqn);
        }
    }

    /**
     * Use the declared mapping version, not the parsed client version: all requests matching a baseline
     * must have the same name. A non-empty method version overrides the type version, as in Spring MVC.
     * Functional endpoints have no mapping annotation and are named without a version suffix.
     */
    private static String extractApiVersion(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequestMapping.class);
            if (methodMapping != null && !methodMapping.version().isEmpty()) {
                return methodMapping.version();
            }
            RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequestMapping.class);
            if (typeMapping != null && !typeMapping.version().isEmpty()) {
                return typeMapping.version();
            }
        }
        return null;
    }

    private String buildFqn(String method, String normalizedPattern, String apiVersion) {
        StringBuilder fqn = new StringBuilder(provider)
                .append(' ')
                .append(method)
                .append(' ')
                .append(normalizedPattern);
        if (apiVersion != null) {
            fqn.append(" ;v=").append(apiVersion);
        }
        return shortenIfNeeded(OperationNameRule.stripSpaces(fqn.toString()));
    }

    private static String shortenIfNeeded(String fqn) {
        if (fqn.length() <= MAX_LENGTH) {
            return fqn;
        }
        String suffix = "~" + sha256Hex(fqn);
        int keep = MAX_LENGTH - suffix.length();
        if (keep > 0 && Character.isHighSurrogate(fqn.charAt(keep - 1))) {
            keep--;
        }
        return fqn.substring(0, keep) + suffix;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm on every JVM; this cannot happen in practice.
            throw new IllegalStateException("SHA-256 message digest not available", e);
        }
    }

    private static boolean isObservable(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return !isExcludedHandlerMethod(handlerMethod);
        }
        return handler instanceof HandlerFunction<?>;
    }

    private static boolean isExcludedHandlerMethod(HandlerMethod handlerMethod) {
        Class<?> beanType = handlerMethod.getBeanType();
        if (isBasicErrorController(beanType)) {
            return true;
        }
        String packageName = beanType.getPackageName();
        return packageName.startsWith(SPRINGDOC_PACKAGE_PREFIX)
                || packageName.startsWith(ACTUATOR_PACKAGE_PREFIX)
                || packageName.startsWith(WEBMVC_ACTUATOR_PACKAGE_PREFIX);
    }

    /**
     * Matched by name so this class does not require the optional {@code spring-boot-webmvc} dependency.
     */
    private static boolean isBasicErrorController(Class<?> beanType) {
        for (Class<?> type = beanType; type != null; type = type.getSuperclass()) {
            if (BASIC_ERROR_CONTROLLER_CLASS_NAME.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
