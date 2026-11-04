package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Protects the {@code JEAP-REACTION-OBSERVATION} header this module sets in {@code preHandle} from being
 * overwritten afterwards - most notably by a BFF controller that builds its {@code ResponseEntity} from a
 * downstream response and copies that response's headers onto its own, including a downstream
 * {@code JEAP-REACTION-OBSERVATION} value. All calls to {@link #setHeader} / {@link #addHeader} for that
 * header name are silently dropped, whether or not this module sets its own value: only
 * {@link #setProtectedHeader(String)} can write it. A downstream value therefore never reaches the caller,
 * also when this module does not set the header itself (no signal, handler not observable, no provider name).
 */
final class ProtectedObservationHeaderResponse extends HttpServletResponseWrapper {

    private final String protectedHeaderName;

    ProtectedObservationHeaderResponse(HttpServletResponse response, String protectedHeaderName) {
        super(response);
        this.protectedHeaderName = protectedHeaderName;
    }

    /** Sets the protected header's value directly on the wrapped response. */
    void setProtectedHeader(String value) {
        super.setHeader(protectedHeaderName, value);
    }

    @Override
    public void setHeader(String name, String value) {
        if (isProtected(name)) {
            return;
        }
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        if (isProtected(name)) {
            return;
        }
        super.addHeader(name, value);
    }

    private boolean isProtected(String name) {
        return protectedHeaderName.equalsIgnoreCase(name);
    }
}
