package ch.admin.bit.jeap.reaction.observer.header.support;

/** Thrown by a test endpoint to be caught by {@link GlobalExceptionHandler} (a {@code @ControllerAdvice}). */
public class CustomBusinessException extends RuntimeException {
}
