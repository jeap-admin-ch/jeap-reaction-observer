package ch.admin.bit.jeap.reaction.observer.header.support;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * {@code Callable}, {@code DeferredResult} and SSE endpoints - the header is expected on all three (set in
 * {@code preHandle} of the initial {@code REQUEST} dispatch, before async processing starts; the response
 * object is the same one used once the async result is ready).
 */
@RestController
public class AsyncController {

    @GetMapping("/api/callable")
    public Callable<String> callable() {
        return () -> {
            Thread.sleep(10);
            return "callable-result";
        };
    }

    @GetMapping("/api/deferred")
    public DeferredResult<String> deferred() {
        DeferredResult<String> result = new DeferredResult<>();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            result.setResult("deferred-result");
        });
        return result;
    }

    @GetMapping(path = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse() {
        SseEmitter emitter = new SseEmitter();
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send("hello");
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
