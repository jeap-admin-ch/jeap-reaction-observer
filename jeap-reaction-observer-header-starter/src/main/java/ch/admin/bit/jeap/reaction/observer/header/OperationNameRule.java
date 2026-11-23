package ch.admin.bit.jeap.reaction.observer.header;

import java.util.Optional;

/**
 * The rule every operation name (FQN) follows, applied alike by the provider, which names its operations in the
 * {@code JEAP-REACTION-OBSERVATION} response header and as triggers, and by the consumer, which reads a name from
 * that header: a name the provider uses is always a name the consumer accepts, so that both identify an operation
 * alike.
 * <p>
 * Plain Java without Servlet or Spring MVC, so that it is also available to a service that only calls other
 * services. Not part of the frozen contract: the header module and the module {@code jeap-reaction-observer-http}
 * are released from the same repository in lockstep.
 */
public final class OperationNameRule {

    /**
     * A longer name is not accepted. Providers shorten their names to 512 characters; the limit leaves a reserve
     * and protects the consumer against oversized values.
     */
    public static final int MAX_LENGTH = 600;

    /**
     * The provider part of a coarse action starts with this prefix. A component name never does, so a name
     * starting with it is not accepted - a provider cannot name a coarse action.
     */
    public static final String COARSE_PROVIDER_PREFIX = "//";

    private OperationNameRule() {
    }

    /**
     * No operation name starts or ends with a space; a space there is dropped, so that it neither makes a name
     * that looks the same a different one nor hides the prefix of a coarse action. Spaces are the Unicode space
     * separators, so no-break spaces too, which {@link String#strip()} keeps; a line break or tab is no space
     * and makes the name unacceptable, see {@link #violation(String)}.
     *
     * @param name the name
     * @return the name without the spaces at its start and end
     */
    public static String stripSpaces(String name) {
        int start = 0;
        int end = name.length();
        while (start < end && isSpace(name.codePointAt(start))) {
            start += Character.charCount(name.codePointAt(start));
        }
        while (end > start && isSpace(name.codePointBefore(end))) {
            end -= Character.charCount(name.codePointBefore(end));
        }
        return name.substring(start, end);
    }

    /**
     * The name comes from another service and ends up in observation ids, events and logs, so it must not be
     * empty or oversized, must not claim to be a coarse action and must not contain characters that no
     * operation name has and that break lines or are invisible: control characters (line breaks, tabs, ...),
     * the Unicode line and paragraph separators and Unicode format characters (zero-width characters, direction
     * overrides, ...).
     *
     * @param name the name, without the spaces at its start and end, see {@link #stripSpaces(String)}
     * @return why the name is not accepted, empty if it is accepted
     */
    public static Optional<String> violation(String name) {
        if (name.isEmpty()) {
            return Optional.of("is empty");
        }
        if (name.length() > MAX_LENGTH) {
            return Optional.of("is longer than " + MAX_LENGTH + " characters");
        }
        if (name.startsWith(COARSE_PROVIDER_PREFIX)) {
            return Optional.of("starts with '" + COARSE_PROVIDER_PREFIX + "', the mark of a coarse action");
        }
        if (name.codePoints().anyMatch(OperationNameRule::isControlOrInvisible)) {
            return Optional.of("contains a control character, a line or paragraph separator or an invisible "
                    + "format character");
        }
        return Optional.empty();
    }

    /**
     * Makes a name safe to log: every character that {@link #violation(String)} does not accept because it
     * breaks lines or is invisible is replaced by its code point, e.g. {@code [U+200C]}, so that a line break
     * or a direction override shows as text.
     *
     * @param name the name
     * @return the name with the control and invisible characters replaced by their code points
     */
    public static String escapeForLog(String name) {
        StringBuilder escaped = new StringBuilder(name.length());
        name.codePoints().forEach(codePoint -> {
            if (isControlOrInvisible(codePoint)) {
                escaped.append(String.format("[U+%04X]", codePoint));
            } else {
                escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private static boolean isSpace(int codePoint) {
        return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    private static boolean isControlOrInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
