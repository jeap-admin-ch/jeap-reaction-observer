package ch.admin.bit.jeap.reaction.observer.http;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Names the target of an HTTP request whose operation the provider did not name. The request is then reported
 * as a coarse action with the FQN {@code //<host>[/<context>] <METHOD> /{*}}, for example
 * {@code //foo.bar.admin.ch/some-service POST /{*}}.
 * <p>
 * The target takes the place of the component name. The leading {@code //} marks the action as coarse: a
 * component name never starts with it. The path is always {@code /{*}}, "any path".
 * <p>
 * The target is part of the reaction id. Changing these rules renames coarse actions and with them the
 * reactions they are part of.
 */
final class HttpTargetNaming {

    static final String COARSE_PATH = "/{*}";

    private static final Pattern CONTEXT = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern API_ROOT_CHARACTERS = Pattern.compile("[a-z0-9._-]+");
    private static final String API = "api";
    private static final String API_SUFFIX = "-api";
    private static final String API_PREFIX = "api-";

    private final boolean includePort;

    /**
     * @param includePort whether a port that is not the default port of the scheme is part of the target
     */
    HttpTargetNaming(boolean includePort) {
        this.includePort = includePort;
    }

    /**
     * @return the FQN of the coarse action for a request with the method to the target
     */
    static String coarseFqn(String target, String method) {
        return target + " " + method.toUpperCase(Locale.ROOT) + " " + COARSE_PATH;
    }

    /**
     * @return the target of a request to the URI as {@code //<host>[:<port>][/<context>]}, empty if the URI has
     * no host
     */
    Optional<String> target(URI uri) {
        HostAndPort hostAndPort = HostAndPort.of(uri);
        if (hostAndPort == null) {
            return Optional.empty();
        }
        StringBuilder target = new StringBuilder(ReactionObservationHeader.COARSE_PROVIDER_PREFIX)
                .append(hostAndPort.host());
        if (includePort && hostAndPort.port() >= 0 && hostAndPort.port() != defaultPort(uri.getScheme())) {
            target.append(':').append(hostAndPort.port());
        }
        String context = context(uri.getRawPath());
        if (context != null) {
            target.append('/').append(context);
        }
        return Optional.of(target.toString());
    }

    /**
     * Several services may share a host and differ in the first path segment only, for example
     * {@code https://host/some-service/api/...}. The first path segment is the context of the called service if
     * the second one is an API root. The path is taken as it is in the request, without decoding or normalising.
     *
     * @return the context, null if the path does not show one
     */
    static String context(String rawPath) {
        if (rawPath == null || !rawPath.startsWith("/")) {
            return null;
        }
        int firstEnd = rawPath.indexOf('/', 1);
        if (firstEnd < 0) {
            return null;
        }
        String first = rawPath.substring(1, firstEnd);
        int secondEnd = rawPath.indexOf('/', firstEnd + 1);
        String second = secondEnd < 0 ? rawPath.substring(firstEnd + 1) : rawPath.substring(firstEnd + 1, secondEnd);
        return isContext(first) && isApiRoot(second) ? first : null;
    }

    private static boolean isContext(String segment) {
        return CONTEXT.matcher(segment).matches()
                && !segment.equals(".") && !segment.equals("..")
                && !isApiRoot(segment);
    }

    /**
     * An API root is a path segment named "api", ending with "-api" (ui-api, external-api) or starting with
     * "api-" (api-internal), in lowercase.
     */
    static boolean isApiRoot(String segment) {
        if (!API_ROOT_CHARACTERS.matcher(segment).matches()) {
            return false;
        }
        return segment.equals(API)
                || (segment.endsWith(API_SUFFIX) && segment.length() > API_SUFFIX.length())
                || (segment.startsWith(API_PREFIX) && segment.length() > API_PREFIX.length());
    }

    private static int defaultPort(String scheme) {
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        return -1;
    }

    /**
     * @param host the host in lowercase and without a trailing dot, an IPv6 address in square brackets
     * @param port the port, -1 if the URI does not state one
     */
    private record HostAndPort(String host, int port) {

        static HostAndPort of(URI uri) {
            if (uri.getHost() != null) {
                return normalized(uri.getHost(), uri.getPort());
            }
            // java.net.URI does not accept every host name in use, for example one with an underscore.
            return ofAuthority(uri.getRawAuthority());
        }

        private static HostAndPort ofAuthority(String authority) {
            if (authority == null) {
                return null;
            }
            String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
            int portSeparator = hostAndPort.lastIndexOf(':');
            // A colon before the closing bracket belongs to an IPv6 address
            if (portSeparator <= hostAndPort.lastIndexOf(']')) {
                return normalized(hostAndPort, -1);
            }
            return normalized(hostAndPort.substring(0, portSeparator), parsePort(hostAndPort.substring(portSeparator + 1)));
        }

        private static int parsePort(String port) {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private static HostAndPort normalized(String host, int port) {
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.endsWith(".")) {
                normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
            }
            return normalizedHost.isEmpty() ? null : new HostAndPort(normalizedHost, port);
        }
    }
}
