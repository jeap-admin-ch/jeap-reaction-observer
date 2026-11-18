package ch.admin.bit.jeap.reaction.observer.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The value of a {@code JEAP-REACTION-OBSERVATION} response header: the name (FQN) of the called operation as
 * the provider states it, or the finding that the provider did not name the operation.
 * <p>
 * The header value is an RFC 9651 Structured Field List whose first member is a Display String
 * ({@code %"..."}), see docs/response-header.md. Only the first member counts: what follows it in the list is
 * ignored and not validated. {@code %} and {@code "} and
 * all bytes outside the visible ASCII range are percent-encoded with lowercase hex digits; the bytes are the
 * UTF-8 encoding of the FQN.
 *
 * @param status whether the provider named the operation
 * @param fqn    the name of the operation if the status is {@link Status#VALID}, null otherwise
 */
record ReactionObservationHeader(Status status, String fqn) {

    enum Status {
        /**
         * The response names the operation.
         */
        VALID,
        /**
         * The response has no observation header.
         */
        ABSENT,
        /**
         * The response has an observation header, but its value cannot be used.
         */
        INVALID
    }

    /**
     * A longer FQN is invalid. Providers shorten their FQNs to 512 characters; the limit leaves a reserve and
     * protects the consumer against oversized values.
     */
    static final int MAX_FQN_LENGTH = 600;
    /**
     * Each character of an FQN takes at most 12 characters in the header (four percent-encoded UTF-8 bytes).
     * <p>
     * The limit applies to the whole header line, before anything is parsed. A longer line is invalid as a
     * whole, on purpose also in the one case where its first list member alone would be valid: an
     * intermediary merged so many header lines into one that the line exceeds the limit. No provider sends
     * such a value, and a consumer should not parse oversized input to find out.
     */
    private static final int MAX_VALUE_LENGTH = 12 * MAX_FQN_LENGTH + 64;
    /**
     * The provider part of a coarse action starts with this prefix. A component name never does, so a header
     * value starting with it is invalid - a provider cannot name a coarse action.
     */
    static final String COARSE_PROVIDER_PREFIX = "//";

    private static final ReactionObservationHeader ABSENT = new ReactionObservationHeader(Status.ABSENT, null);
    private static final ReactionObservationHeader INVALID = new ReactionObservationHeader(Status.INVALID, null);

    /**
     * @param value the value of the first header line, null if the response has no such header
     */
    static ReactionObservationHeader parse(String value) {
        if (value == null) {
            return ABSENT;
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return INVALID;
        }
        String displayString = decodeFirstDisplayString(value);
        if (displayString == null) {
            return INVALID;
        }
        String fqn = stripSpaces(displayString);
        if (!isAcceptableFqn(fqn)) {
            return INVALID;
        }
        return new ReactionObservationHeader(Status.VALID, fqn);
    }

    /**
     * No operation name starts or ends with a space; a space there is dropped, so that it neither makes a name
     * that looks the same a different one nor hides the prefix of a coarse action. Spaces are the Unicode space
     * separators, so no-break spaces too, which {@link String#strip()} keeps; a line break or tab is no space
     * and makes the name invalid, see {@link #isAcceptableFqn(String)}.
     */
    private static String stripSpaces(String name) {
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

    private static boolean isSpace(int codePoint) {
        return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    /**
     * The name comes from another service and ends up in observation ids, events and logs, so the consumer does
     * not take just anything: the name (without spaces at its start and end) must not be empty or oversized,
     * must not claim to be a coarse action and must not contain characters that no operation name has and that
     * break lines or are invisible: control characters (line breaks, tabs, ...), the Unicode line and paragraph
     * separators and Unicode format characters (zero-width characters, direction overrides, ...).
     */
    private static boolean isAcceptableFqn(String fqn) {
        return !fqn.isEmpty()
                && fqn.length() <= MAX_FQN_LENGTH
                && !fqn.startsWith(COARSE_PROVIDER_PREFIX)
                && fqn.codePoints().noneMatch(ReactionObservationHeader::isControlOrInvisible);
    }

    private static boolean isControlOrInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    boolean isValid() {
        return status == Status.VALID;
    }

    /**
     * @return the decoded content of the Display String the value starts with, null if the value does not start
     * with a well-formed Display String
     */
    private static String decodeFirstDisplayString(String value) {
        int start = skipWhitespace(value, 0);
        if (!value.startsWith("%\"", start)) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        int index = start + 2;
        while (index < value.length()) {
            char c = value.charAt(index);
            if (c == '"') {
                return isEndOfListMember(value, index + 1) ? decodeUtf8(bytes.toByteArray()) : null;
            }
            if (c == '%') {
                int decoded = decodeLowercaseHexPair(value, index + 1);
                if (decoded < 0) {
                    return null;
                }
                bytes.write(decoded);
                index += 3;
            } else if (c >= 0x20 && c <= 0x7E) {
                bytes.write(c);
                index++;
            } else {
                return null;
            }
        }
        // The closing quote is missing
        return null;
    }

    /**
     * After the Display String the value has to end, or to continue with the parameters of the member (";") or
     * with the next member of the list (","), both of which are ignored.
     */
    private static boolean isEndOfListMember(String value, int index) {
        int next = skipWhitespace(value, index);
        return next == value.length() || value.charAt(next) == ',' || value.charAt(next) == ';';
    }

    private static int skipWhitespace(String value, int index) {
        int next = index;
        while (next < value.length() && (value.charAt(next) == ' ' || value.charAt(next) == '\t')) {
            next++;
        }
        return next;
    }

    /**
     * @return the byte encoded by the two lowercase hex digits at the index, -1 if there are none
     */
    private static int decodeLowercaseHexPair(String value, int index) {
        if (index + 1 >= value.length()) {
            return -1;
        }
        int high = lowercaseHexDigit(value.charAt(index));
        int low = lowercaseHexDigit(value.charAt(index + 1));
        return high < 0 || low < 0 ? -1 : (high << 4) | low;
    }

    private static int lowercaseHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return -1;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
