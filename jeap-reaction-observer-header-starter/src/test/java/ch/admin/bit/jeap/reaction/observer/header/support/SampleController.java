package ch.admin.bit.jeap.reaction.observer.header.support;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
public class SampleController {

    @GetMapping("/api/items/{id}")
    public String getItem(@PathVariable String id) {
        return "item-" + id;
    }

    @GetMapping("/api/wild/{*rest}")
    public String getWild(@PathVariable String rest) {
        return "wild-" + rest;
    }

    @GetMapping("/api/trailing/")
    public String getTrailing() {
        return "trailing";
    }

    @GetMapping("/api/regex/{id:[0-9]+}")
    public String getRegex(@PathVariable String id) {
        return "regex-" + id;
    }

    @GetMapping("/api/advice-error")
    public String adviceError() {
        throw new CustomBusinessException();
    }

    @GetMapping("/api/response-status-error")
    public String responseStatusError() {
        throw new ResponseStatusException(CONFLICT, "conflict");
    }

    @GetMapping("/api/unhandled")
    public String unhandled() {
        throw new IllegalStateException("boom");
    }

    /**
     * Stands in for a BFF controller that builds its response from a downstream call and copies that
     * downstream response's headers onto its own - including a downstream
     * {@code JEAP-REACTION-OBSERVATION} value, which must not overwrite the value this service's own
     * header module already set in {@code preHandle}.
     */
    @GetMapping("/api/bff")
    public ResponseEntity<String> bff() {
        return ResponseEntity.ok()
                .header("JEAP-REACTION-OBSERVATION", "downstream-service GET /fake-downstream")
                .body("bff-result");
    }
}
