package ch.admin.bit.jeap.reaction.observer.header.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Exercises the "error response via @ControllerAdvice" case from the module's documented special cases. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomBusinessException.class)
    ResponseEntity<String> handleBusinessException(CustomBusinessException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("business-error");
    }
}
