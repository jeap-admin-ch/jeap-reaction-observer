package ch.admin.bit.jeap.reaction.observer.header;

/**
 * Normalises a Spring {@code HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE} path pattern into the form used
 * in the FQN: {@code {name}} and {@code {name:regex}} become {@code {}}, {@code {*name}} becomes {@code {*}},
 * and a trailing slash is dropped, unless the whole path is {@code /}.
 * <p>
 * This is deliberately close to, but not identical with, {@code RelationPaths.normalised} (jEAP ArchRepo /
 * Doc-Service): that rule turns every path variable - including a {@code {*name}} catch-all - into {@code
 * {}}. This module keeps {@code {*name}} distinguishable as {@code {*}} because that is what Spring itself
 * returns from a wildcard capture, and collapsing it into {@code {}} would make two different operations
 * indistinguishable.
 */
final class PathPatternNormalizer {

    private PathPatternNormalizer() {
    }

    static String normalize(String pattern) {
        if (pattern == null) {
            return "";
        }
        String stripped = pattern.strip();
        if (stripped.isEmpty()) {
            return "";
        }
        String replaced = replaceVariables(stripped);
        if (!replaced.endsWith("/") || replaced.length() == 1) {
            return replaced;
        }
        return replaced.substring(0, replaced.length() - 1);
    }

    /**
     * Replaces every {@code {...}} path-variable segment with {@code {}} (or {@code {*}} for a catch-all
     * {@code {*name}}), scanning brace depth so that a regex constraint containing its own braces (e.g.
     * {@code {id:[0-9]{3}}}) is consumed as a single variable instead of being split apart.
     */
    private static String replaceVariables(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length());
        int length = pattern.length();
        int i = 0;
        while (i < length) {
            char c = pattern.charAt(i);
            if (c != '{') {
                result.append(c);
                i++;
                continue;
            }
            int depth = 1;
            int j = i + 1;
            while (j < length && depth > 0) {
                char cj = pattern.charAt(j);
                if (cj == '{') {
                    depth++;
                } else if (cj == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                j++;
            }
            if (depth != 0) {
                // Unmatched opening brace: not a well-formed variable, keep the rest of the pattern as-is.
                result.append(pattern, i, length);
                return result.toString();
            }
            String variableSpec = pattern.substring(i + 1, j);
            result.append(variableSpec.startsWith("*") ? "{*}" : "{}");
            i = j + 1;
        }
        return result.toString();
    }
}
