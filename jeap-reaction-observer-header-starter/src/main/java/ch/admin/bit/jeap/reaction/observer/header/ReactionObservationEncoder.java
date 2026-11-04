package ch.admin.bit.jeap.reaction.observer.header;

import java.nio.charset.StandardCharsets;

/**
 * Serialises the FQN of an operation as the value of the {@code JEAP-REACTION-OBSERVATION} header: an
 * RFC 9651 Structured Field List with exactly one member, a Display String ({@code %"..."}).
 * <p>
 * {@code %}, {@code "}, every non-printable ASCII character and every non-ASCII byte are percent-encoded as
 * their raw UTF-8 bytes, using lowercase hex digits; every other visible ASCII character (0x20-0x7E) is kept
 * as-is. This is the frozen wire format shared with the parser in the Reaction Observer HTTP module
 * - see docs/response-header.md for the shared test cases this encoder and that parser agree on.
 */
final class ReactionObservationEncoder {

    private ReactionObservationEncoder() {
    }

    static String encode(String fqn) {
        StringBuilder sb = new StringBuilder(fqn.length() + 8);
        sb.append("%\"");
        for (byte b : fqn.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = b & 0xFF;
            if (mustBeEncoded(unsigned)) {
                sb.append('%');
                appendHex(sb, unsigned);
            } else {
                sb.append((char) unsigned);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static boolean mustBeEncoded(int unsigned) {
        return unsigned == '%' || unsigned == '"' || unsigned < 0x20 || unsigned >= 0x7F;
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static void appendHex(StringBuilder sb, int unsigned) {
        sb.append(HEX_DIGITS[(unsigned >> 4) & 0xF]);
        sb.append(HEX_DIGITS[unsigned & 0xF]);
    }
}
