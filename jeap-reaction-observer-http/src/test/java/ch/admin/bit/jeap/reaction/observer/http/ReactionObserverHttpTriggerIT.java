package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Incoming HTTP requests as triggers, with a real servlet container: the status of the response and the order of
 * the dispatches are those of production.
 * <p>
 * The container handles all requests in one thread, and every test ends with a probe for a trigger left open in
 * that thread, see {@link ContainerThreadProbe}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=order-service",
                "jeap.reaction.observer.model-version=2",
                "server.tomcat.threads.max=1",
                "server.tomcat.threads.min-spare=1"
        })
class ReactionObserverHttpTriggerIT {

    private static final Observation ORDER_CREATED = Observation.ofEvent("OrderCreated", "order-topic");
    private static final Observation ORDER_REJECTED = Observation.ofEvent("OrderRejected", "order-topic");
    private static final Observation CHECK_STOCK = Observation.ofHttpRequest("stock-service GET /api/stock/{}");
    private static final Observation AUDIT_WRITTEN = Observation.ofEvent("AuditWritten", "audit-topic");
    private static final HttpServer STOCK_SERVICE = startStockService();

    @LocalServerPort
    private int port;
    @Autowired
    private ObservedReactions reactions;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("stock-service.url", () -> "http://localhost:" + STOCK_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stopStockService() {
        STOCK_SERVICE.stop(0);
    }

    @BeforeEach
    void setUp() {
        reactions.clear();
    }

    @Test
    void requestThatSendsAnEvent_reactionOfTheRequestWithTheEvent() {
        assertThat(post("/api/orders")).isEqualTo(201);

        assertCounted(new Reaction(trigger("POST /api/orders"), List.of(ORDER_CREATED)));
    }

    @Test
    void requestThatCallsAProviderNamingItsOperation_reactionOfTheRequestWithTheHttpRequest() {
        assertThat(post("/api/orders/42/stock-check")).isEqualTo(204);

        // The reaction is identified the first time it occurs, and no other test calls this operation
        Reaction expected = new Reaction(trigger("POST /api/orders/{}/stock-check"), List.of(CHECK_STOCK));
        assertCounted(expected);
        assertThat(reactions.identified()).contains(expected);
    }

    @Test
    void successfulRequestWithoutActions_triggerOnlyReaction() {
        assertThat(get("/api/orders/42")).isEqualTo(200);

        assertCounted(new Reaction(trigger("GET /api/orders/{}"), List.of()));
    }

    /**
     * The trigger encloses the interceptors of the service: what they cause belongs to the reaction.
     */
    @Test
    void actionOfAnInterceptorOfTheService_belongsToTheReaction() {
        assertThat(get("/api/audited")).isEqualTo(200);

        assertCounted(new Reaction(trigger("GET /api/audited"), List.of(AUDIT_WRITTEN)));
    }

    @Test
    void functionalEndpoint_isObserved() {
        assertThat(get("/fn/ping")).isEqualTo(204);

        assertCounted(new Reaction(trigger("GET /fn/ping"), List.of()));
    }

    /**
     * Whether a 4xx is thrown or returned makes no difference. Spring turns the exceptions into the status in
     * three different ways, two of them with sendError: the status has to be on the response by then.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "annotated-exception, 409",
            "response-status-exception, 409",
            "controller-advice, 422",
            "returned, 409"
    })
    void status4xxWithAction_counts(String rejection, int expectedStatus) {
        assertThat(post("/api/rejections/" + rejection + "?action=true")).isEqualTo(expectedStatus);

        assertCounted(new Reaction(trigger("POST /api/rejections/" + rejection), List.of(ORDER_REJECTED)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "annotated-exception, 409",
            "response-status-exception, 409",
            "controller-advice, 422",
            "returned, 409"
    })
    void status4xxWithoutAction_doesNotCount(String rejection, int expectedStatus) {
        assertThat(post("/api/rejections/" + rejection + "?action=false")).isEqualTo(expectedStatus);

        assertNothingCounted();
    }

    @Test
    void invalidRequest_400WithoutAction_doesNotCount() {
        assertThat(get("/api/orders?size=not-a-number")).isEqualTo(400);

        assertNothingCounted();
    }

    @Test
    void requestRejectedByALaterInterceptor_429WithoutAction_doesNotCount() {
        assertThat(get("/api/throttled")).isEqualTo(429);

        assertNothingCounted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/failures/unhandled-exception", "/api/failures/returned"})
    void status5xx_doesNotCount_actionsAreDiscarded(String path) {
        assertThat(post(path)).isGreaterThanOrEqualTo(500);

        assertNothingCounted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/async/callable", "/api/async/deferred-result", "/api/async/sse",
            "/api/async/servlet"})
    void asyncRequest_headerOnly_noTrigger_theActionBeforeTheHandoverIsAnActionOnlyReaction(String path) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path)).GET()
                .header("JEAP-REACTION-MODEL-VERSION", "2"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("done");
        assertThat(response.headers().firstValue("JEAP-REACTION-OBSERVATION"))
                .contains("%\"order-service GET " + path + "\"");
        assertCounted(Reaction.actionOnly(ORDER_CREATED));
    }

    /**
     * The name of the route contains a zero-width non-joiner, which a caller does not accept as the name of an
     * operation. The provider does not name the operation either: no response header and no trigger, so that no
     * trigger is reported that a caller never links its call to. What the request causes is reported without a
     * trigger, and the caller treats its call like any response without the header, see
     * ReactionObserverHttpProviderIT.
     */
    @Test
    void routeWhoseNameACallerDoesNotAccept_noHeader_noTrigger_theActionIsAnActionOnlyReaction() {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/zero%E2%80%8Cwidth")).GET()
                .header("JEAP-REACTION-MODEL-VERSION", "2"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("JEAP-REACTION-OBSERVATION")).isEmpty();
        assertCounted(Reaction.actionOnly(ORDER_CREATED));
    }

    /**
     * A mapping without a method condition accepts any method a client sends. A request with a made-up method is
     * not named: no response header and no trigger, so that made-up methods cannot make an unbounded number of
     * triggers out of one mapping. What it causes is reported without a trigger. A standard method is named.
     */
    @Test
    void mappingWithoutMethodCondition_madeUpMethodIsNoTrigger_standardMethodIsATrigger() {
        HttpResponse<String> madeUpMethod = send(HttpRequest.newBuilder(uri("/api/any-method"))
                .method("FOO1", HttpRequest.BodyPublishers.noBody())
                .header("JEAP-REACTION-MODEL-VERSION", "2"));
        assertThat(madeUpMethod.statusCode()).isEqualTo(200);
        assertThat(madeUpMethod.headers().firstValue("JEAP-REACTION-OBSERVATION")).isEmpty();
        assertThat(post("/api/any-method")).isEqualTo(200);

        assertCounted(Reaction.actionOnly(ORDER_CREATED),
                new Reaction(trigger("POST /api/any-method"), List.of(ORDER_CREATED)));
    }

    @Test
    void failedRequest_asyncRequest_successfulRequest_inTheSameThread_doNotMix() {
        assertThat(post("/api/failures/unhandled-exception")).isEqualTo(500);
        assertThat(get("/api/async/callable")).isEqualTo(200);
        assertThat(post("/api/rejections/annotated-exception?action=false")).isEqualTo(409);
        assertThat(post("/api/orders")).isEqualTo(201);

        assertCounted(Reaction.actionOnly(ORDER_CREATED), new Reaction(trigger("POST /api/orders"), List.of(ORDER_CREATED)));
    }

    private static Observation trigger(String methodAndPattern) {
        return Observation.ofHttpRequest("order-service " + methodAndPattern);
    }

    private void assertNothingCounted() {
        assertCounted();
    }

    private void assertCounted(Reaction... expected) {
        ContainerThreadProbe.assertCounted(reactions, get(ContainerThreadProbe.PATH), expected);
    }

    private int get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET()).statusCode();
    }

    private int post(String path) {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody())).statusCode();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /**
     * A provider that names its operation with the response header, as the header module does.
     */
    private static HttpServer startStockService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/stock", exchange -> {
                exchange.getResponseHeaders().add("JEAP-REACTION-OBSERVATION", "%\"stock-service GET /api/stock/{}\"");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Nested classes of a test class are not found by the component scan, so they are imported
    @SpringBootApplication
    @Import({OrderController.class, RejectionController.class, FailureController.class, AsyncController.class,
            RejectionAdvice.class})
    static class OrderApplication {

        @Bean
        ObservedReactions observedReactions() {
            return new ObservedReactions();
        }

        @Bean
        ReactionRecorder reactionRecorder(ObservedReactions observedReactions) {
            return observedReactions.recorder();
        }

        @Bean
        RouterFunction<ServerResponse> pingRoute() {
            return RouterFunctions.route().GET("/fn/ping", request -> ServerResponse.noContent().build()).build();
        }

        /**
         * Interceptors of the service, after the one of the library: one causes an action, one rejects a request.
         */
        @Bean
        WebMvcConfigurer interceptorsOfTheService(ReactionRecorder reactionRecorder) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                            reactionRecorder.onAction(AUDIT_WRITTEN);
                            return true;
                        }
                    }).addPathPatterns("/api/audited");
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                            return false;
                        }
                    }).addPathPatterns("/api/throttled");
                }
            };
        }

        @Bean
        FilterRegistrationBean<Filter> probeFilter(ReactionRecorder reactionRecorder) {
            return ContainerThreadProbe.filter(reactionRecorder);
        }
    }

    @RestController
    static class OrderController {

        private final ReactionRecorder reactionRecorder;
        private final RestClient stockService;

        OrderController(ReactionRecorder reactionRecorder, RestClient.Builder restClientBuilder,
                        @Value("${stock-service.url}") String stockServiceUrl) {
            this.reactionRecorder = reactionRecorder;
            this.stockService = restClientBuilder.baseUrl(stockServiceUrl).build();
        }

        @PostMapping("/api/orders")
        @ResponseStatus(HttpStatus.CREATED)
        void create() {
            reactionRecorder.onAction(ORDER_CREATED);
        }

        @GetMapping("/api/orders/{id}")
        String order(@PathVariable String id) {
            return "order " + id;
        }

        @GetMapping("/api/orders")
        String orders(@RequestParam int size) {
            return size + " orders";
        }

        @PostMapping("/api/orders/{id}/stock-check")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void checkStock(@PathVariable String id) {
            stockService.get().uri("/api/stock/{id}", id).retrieve().toBodilessEntity();
        }

        @RequestMapping("/api/any-method")
        String anyMethod() {
            reactionRecorder.onAction(ORDER_CREATED);
            return "any method";
        }

        @GetMapping("/api/zero\u200cwidth")
        String zeroWidth() {
            reactionRecorder.onAction(ORDER_CREATED);
            return "zero width";
        }

        @GetMapping("/api/audited")
        String audited() {
            return "audited";
        }

        @GetMapping("/api/throttled")
        void throttled() {
            throw new IllegalStateException("The request is rejected by an interceptor before it gets here");
        }
    }

    /**
     * The ways to answer with a 4xx. With the parameter action the request causes an action before.
     */
    @RestController
    static class RejectionController {

        private final ReactionRecorder reactionRecorder;

        RejectionController(ReactionRecorder reactionRecorder) {
            this.reactionRecorder = reactionRecorder;
        }

        @PostMapping("/api/rejections/annotated-exception")
        void annotatedException(@RequestParam boolean action) {
            recordIf(action);
            throw new LimitExceededException();
        }

        @PostMapping("/api/rejections/response-status-exception")
        void responseStatusException(@RequestParam boolean action) {
            recordIf(action);
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @PostMapping("/api/rejections/controller-advice")
        void controllerAdvice(@RequestParam boolean action) {
            recordIf(action);
            throw new InvalidOrderException();
        }

        @PostMapping("/api/rejections/returned")
        ResponseEntity<Void> returned(@RequestParam boolean action) {
            recordIf(action);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        private void recordIf(boolean action) {
            if (action) {
                reactionRecorder.onAction(ORDER_REJECTED);
            }
        }
    }

    @RestController
    static class FailureController {

        private final ReactionRecorder reactionRecorder;

        FailureController(ReactionRecorder reactionRecorder) {
            this.reactionRecorder = reactionRecorder;
        }

        @PostMapping("/api/failures/unhandled-exception")
        void unhandledException() {
            reactionRecorder.onAction(ORDER_CREATED);
            throw new IllegalStateException("simulated failure");
        }

        @PostMapping("/api/failures/returned")
        ResponseEntity<Void> returned() {
            reactionRecorder.onAction(ORDER_CREATED);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Requests that are completed in another thread. Each records an action before it hands the request over:
     * the action is reported without the trigger.
     */
    @RestController
    static class AsyncController implements DisposableBean {

        private final ReactionRecorder reactionRecorder;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        AsyncController(ReactionRecorder reactionRecorder) {
            this.reactionRecorder = reactionRecorder;
        }

        @GetMapping("/api/async/callable")
        Callable<String> callable() {
            reactionRecorder.onAction(ORDER_CREATED);
            return () -> "done";
        }

        @GetMapping("/api/async/deferred-result")
        DeferredResult<String> deferredResult() {
            reactionRecorder.onAction(ORDER_CREATED);
            DeferredResult<String> result = new DeferredResult<>();
            executor.execute(() -> result.setResult("done"));
            return result;
        }

        @GetMapping("/api/async/sse")
        SseEmitter sse() {
            reactionRecorder.onAction(ORDER_CREATED);
            SseEmitter emitter = new SseEmitter();
            executor.execute(() -> {
                try {
                    emitter.send("done");
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        /**
         * Starts the asynchronous processing of the servlet container itself: Spring MVC does not know about it
         * and completes the request as one handled synchronously, with the status 200.
         */
        @GetMapping("/api/async/servlet")
        void servletAsync(HttpServletRequest request) {
            reactionRecorder.onAction(ORDER_CREATED);
            AsyncContext asyncContext = request.startAsync();
            executor.execute(() -> {
                try {
                    asyncContext.getResponse().getWriter().write("done");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    asyncContext.complete();
                }
            });
        }

        @Override
        public void destroy() {
            executor.shutdownNow();
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    static class LimitExceededException extends RuntimeException {
    }

    static class InvalidOrderException extends RuntimeException {
    }

    @RestControllerAdvice
    static class RejectionAdvice {

        @ExceptionHandler(InvalidOrderException.class)
        ResponseEntity<String> invalidOrder() {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("invalid order");
        }
    }
}
