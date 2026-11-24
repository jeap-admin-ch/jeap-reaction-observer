package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverMetrics;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionObserverService;
import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.listener.ReactionIdentifiedListener;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.ReactionModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReactionObservingClientHttpRequestInterceptorTest {

    private static final String URL = "https://partner.example.org/partner-service/api/partners/42";
    private static final String OBSERVATION_HEADER = "JEAP-REACTION-OBSERVATION";
    private static final String PARTNER_OPERATION = "partner-service GET /api/partners/{}";
    private static final int MAX_TARGET_COUNT = 100;
    private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

    private final ObservedReactions reactions = new ObservedReactions();
    private final HttpActionObserver observer = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
            new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), HttpObservationMetrics.NONE, LOG_INTERVAL);
    private final RestClient.Builder restClientBuilder = RestClient.builder()
            .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observer, ReactionModel.V2, LOG_INTERVAL));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final RestClient restClient = restClientBuilder.build();

    @Test
    void request_carriesTheModelVersionSignal() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("JEAP-REACTION-MODEL-VERSION", "2"))
                .andRespond(withSuccess());

        restClient.get().uri(URL).retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void response_passesUnchanged_andNamesTheFineAction() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON)
                .header(OBSERVATION_HEADER, "%\"" + PARTNER_OPERATION + "\""));

        ResponseEntity<String> response = inTrigger(() -> restClient.get().uri(URL).retrieve().toEntity(String.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"id\":42}");
        assertThat(response.getHeaders().getFirst(OBSERVATION_HEADER)).isEqualTo("%\"" + PARTNER_OPERATION + "\"");
        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION);
    }

    @Test
    void severalHeaderLines_firstLineCounts() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(OBSERVATION_HEADER, "%\"svc GET /api/a,b\"");
        headers.add(OBSERVATION_HEADER, "%\"other GET /api/x\"");
        server.expect(requestTo(URL)).andRespond(withSuccess().headers(headers));

        inTrigger(() -> restClient.get().uri(URL).retrieve().toBodilessEntity());

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("svc GET /api/a,b");
    }

    @Test
    void responseWithoutHeader_coarseActionWithTheMethodOfTheRequest() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        inTrigger(() -> restClient.post().uri(URL).body("{}").retrieve().toBodilessEntity());

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//partner.example.org/partner-service POST /{*}");
    }

    @Test
    void clientError_exceptionOfTheCallPassesUnchanged_coarseAction() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        reactions.inTrigger(() -> assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpClientErrorException.NotFound.class));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly("//partner.example.org/partner-service GET /{*}");
    }

    @Test
    void serverErrorWithHeader_exceptionOfTheCallPassesUnchanged_fineAction() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(OBSERVATION_HEADER, "%\"" + PARTNER_OPERATION + "\""));

        reactions.inTrigger(() -> assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.InternalServerError.class));

        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION);
    }

    @Test
    void serverErrorWithoutHeader_exceptionOfTheCallPassesUnchanged_executionIsDiscarded() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        reactions.inTrigger(() -> assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
    }

    @Test
    void ioException_passesUnchanged_executionIsDiscarded() {
        IOException ioException = new IOException("connection reset");
        server.expect(requestTo(URL)).andRespond(withException(ioException));

        reactions.inTrigger(() -> assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(ResourceAccessException.class)
                .hasCause(ioException));

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
    }

    @Test
    void timeout_passesUnchanged_executionIsDiscarded() {
        SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");
        server.expect(requestTo(URL)).andRespond(withException(timeout));

        reactions.inTrigger(() -> assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(ResourceAccessException.class)
                .hasCause(timeout));

        assertThat(reactions.identified()).isEmpty();
    }

    @Test
    void runtimeExceptionOfAnInnerInterceptor_passesUnchanged_executionIsDiscarded() {
        IllegalStateException failure = new IllegalStateException("no access token");
        RestClient failingClient = RestClient.builder()
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observer, ReactionModel.V2, LOG_INTERVAL))
                .requestInterceptor((request, body, execution) -> {
                    throw failure;
                })
                .build();

        reactions.inTrigger(() -> assertThatThrownBy(failingClient.get().uri(URL).retrieve()::toBodilessEntity)
                .isSameAs(failure));

        assertThat(reactions.identified()).isEmpty();
    }

    /**
     * The library describes business flows, it does not monitor HTTP requests: what happens inside the client
     * before it returns to the service is part of the call. A request repeated successfully by an interceptor
     * of the client is one successful call - the failed attempt is deliberately not observed and the
     * execution counts. Do not "fix" this by observing every attempt.
     */
    @Test
    void retryInsideOfTheClient_onlyTheFinalOutcomeCounts_executionIsNotDiscarded() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpActionObserver observerWithMetrics = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), new MicrometerHttpObservationMetrics(meterRegistry), LOG_INTERVAL);
        ClientHttpRequestInterceptor retryOnceOnServerError = (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            if (!response.getStatusCode().is5xxServerError()) {
                return response;
            }
            response.close();
            return execution.execute(request, body);
        };
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observerWithMetrics, ReactionModel.V2, LOG_INTERVAL))
                // Added after the observation, as a service does with the builder of Spring Boot: runs inside of it
                .requestInterceptor(retryOnceOnServerError);
        MockRestServiceServer retryServer = MockRestServiceServer.bindTo(builder).build();
        retryServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        retryServer.expect(requestTo(URL)).andRespond(withSuccess().header(OBSERVATION_HEADER, "%\"" + PARTNER_OPERATION + "\""));
        RestClient client = builder.build();

        reactions.inTrigger(() -> client.get().uri(URL).retrieve().toBodilessEntity());

        retryServer.verify();
        assertThat(reactions.actionFqnsOfSingleReaction()).containsExactly(PARTNER_OPERATION);
        assertThat(reactions.countsSinceLastCall()).containsValue(1);
        assertThat(meterRegistry.get("jeap.reaction.observer.dropped.http.calls").tag("reason", "call_failed").counter().count()).isZero();
        assertThat(meterRegistry.get("jeap.reaction.observer.unidentified.http.calls").counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    /**
     * The counterpart: a retry the service makes itself is a second call. Each attempt is observed, and the
     * failed one discards the execution although the retry succeeds.
     */
    @Test
    void retryByTheService_eachAttemptIsObserved_executionIsDiscarded() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess().header(OBSERVATION_HEADER, "%\"" + PARTNER_OPERATION + "\""));

        reactions.inTrigger(() -> {
            assertThatThrownBy(restClient.get().uri(URL).retrieve()::toBodilessEntity)
                    .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
            restClient.get().uri(URL).retrieve().toBodilessEntity();
        });

        server.verify();
        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
    }

    /**
     * A known limit, see docs/http-requests.md: Spring runs the request initializers of a client before its
     * interceptors, so a call that fails in an initializer never reaches the observation. The OAuth2 clients of
     * jEAP get their token in an initializer. If the service catches the exception and carries on, the execution
     * counts without the call.
     */
    @Test
    void failureInARequestInitializer_isNotObserved_executionCountsWithoutTheCall() {
        RestClient clientWithFailingInitializer = RestClient.builder()
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observer, ReactionModel.V2, LOG_INTERVAL))
                .requestInitializer(request -> {
                    throw new IllegalStateException("simulated failure of the token request");
                })
                .build();

        reactions.inTrigger(() -> assertThatThrownBy(clientWithFailingInitializer.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(IllegalStateException.class));

        assertThat(reactions.identified()).hasSize(1);
        assertThat(reactions.identified().getFirst().actions()).isEmpty();
    }

    /**
     * Some request factories read the response only when its status is asked for, for example Spring's simple
     * request factory for a request with a body. A timeout then shows up while the observation reads the
     * status: that is a call without a response, not a failure of the observation. The service gets a timeout
     * as the cause, as without the observation.
     */
    @Test
    void timeoutWhileReadingTheStatus_isAFailedCall() throws IOException {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HttpActionObserver observerWithMetrics = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), new MicrometerHttpObservationMetrics(meterRegistry), LOG_INTERVAL);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(300));
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observerWithMetrics, ReactionModel.V2, LOG_INTERVAL))
                .build();

        // The server accepts the connection and the request, but never answers
        try (ServerSocket silentServer = new ServerSocket(0)) {
            String url = "http://localhost:" + silentServer.getLocalPort() + "/api/jobs";
            reactions.inTrigger(() -> assertThatThrownBy(client.post().uri(url).body("{}").retrieve()::toBodilessEntity)
                    .isInstanceOf(ResourceAccessException.class)
                    .cause()
                    .isInstanceOf(SocketTimeoutException.class));
        }

        assertThat(reactions.identified()).isEmpty();
        assertThat(meterRegistry.get("jeap.reaction.observer.dropped.http.calls").tag("reason", "call_failed").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("jeap.reaction.observer.unidentified.http.calls").tag("reason", "no_response").counter().count()).isEqualTo(1);
    }

    /**
     * An invalid status code: Spring rejects it with a runtime exception, while the rest of the response can be
     * read. The response passes unchanged: the caller gets the failure from the response when it reads the status
     * code, and status text, headers and body can be read. A fallback of the caller works, the response is closed
     * by the caller, not by the observation, and the call is observed once as a failed call.
     */
    @Test
    void invalidStatusCode_theResponsePassesUnchanged_isAFailedCall() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ResponseWithInvalidStatusCode invalidResponse = new ResponseWithInvalidStatusCode();
        RestClient client = clientReturning(invalidResponse, meterRegistry);

        String result = inTrigger(() -> client.get().uri(URL).exchange((request, response) -> {
            assertThat(catchThrowable(response::getStatusCode)).isSameAs(invalidResponse.failures().getLast());
            assertThat(response.getStatusText()).isEqualTo(ResponseWithInvalidStatusCode.STATUS_TEXT);
            assertThat(response.getHeaders().getFirst(ResponseWithInvalidStatusCode.HEADER)).isEqualTo("1");
            assertThat(response.getBody()).hasContent(ResponseWithInvalidStatusCode.BODY);
            assertThat(invalidResponse.closeCount()).describedAs("not closed by the observation").isZero();
            return "fallback";
        }));

        assertThat(result).isEqualTo("fallback");
        assertThat(invalidResponse.closeCount()).describedAs("closed by the caller").isEqualTo(1);
        assertThat(reactions.identified()).isEmpty();
        assertThat(count(meterRegistry, "unidentified.http.calls", "no_response")).isEqualTo(1);
        assertThat(count(meterRegistry, "dropped.http.calls", "call_failed")).isEqualTo(1);
    }

    @Test
    void invalidStatusCode_retrieve_theCallerGetsTheFailureFromTheResponse() {
        ResponseWithInvalidStatusCode invalidResponse = new ResponseWithInvalidStatusCode();
        RestClient client = clientReturning(invalidResponse, new SimpleMeterRegistry());

        reactions.inTrigger(() -> assertThatThrownBy(client.get().uri(URL).retrieve()::toBodilessEntity)
                .describedAs("the failure of the caller's own read, not the one of the observation")
                .isSameAs(invalidResponse.failures().getLast())
                .isNotSameAs(invalidResponse.failures().getFirst()));

        assertThat(invalidResponse.closeCount()).describedAs("closed by the caller, retrieve closes it more than once").isPositive();
        assertThat(reactions.identified()).isEmpty();
    }

    /**
     * A real timeout while the response is read: a POST with a body over HttpURLConnection reads the response
     * only when it is asked for, and the observation reads the status code first. Whatever the caller reads first
     * and then, it gets an exception of the same class and with the same message as without the observation. The
     * instance differs: HttpURLConnection gives every read after the first a new exception, with the original one
     * as its cause.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("readsAfterATimeout")
    void timeoutWhileReadingTheResponse_theCallerReadsItAsWithoutTheObservation(List<Read> reads) throws IOException {
        List<String> withoutObservation = readAfterATimeout(reads, false);
        List<String> withObservation = readAfterATimeout(reads, true);

        assertThat(withoutObservation).describedAs("the reads run into the timeout")
                .anyMatch(outcome -> outcome.startsWith(SocketTimeoutException.class.getSimpleName()));
        assertThat(withObservation).isEqualTo(withoutObservation);
    }

    static Stream<List<Read>> readsAfterATimeout() {
        return Stream.of(
                List.of(Read.STATUS_CODE, Read.STATUS_CODE),
                List.of(Read.STATUS_TEXT, Read.STATUS_CODE),
                List.of(Read.BODY, Read.STATUS_CODE),
                List.of(Read.HEADERS, Read.STATUS_CODE));
    }

    private List<String> readAfterATimeout(List<Read> reads, boolean observed) throws IOException {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(300));
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (observed) {
            builder.requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observer, ReactionModel.V2, LOG_INTERVAL));
        }
        RestClient client = builder.build();

        // The server accepts the connection and the request, but never answers
        try (ServerSocket silentServer = new ServerSocket(0)) {
            String url = "http://localhost:" + silentServer.getLocalPort() + "/api/jobs";
            return client.post().uri(url).body("{}").exchange((request, response) ->
                    reads.stream().map(read -> read.outcome(response)).toList());
        }
    }

    /**
     * A read of a response by the caller.
     */
    enum Read {
        STATUS_CODE, STATUS_TEXT, HEADERS, BODY;

        /**
         * @return what the read gives, or the class and the message of its failure
         */
        String outcome(ClientHttpResponse response) {
            try {
                return switch (this) {
                    case STATUS_CODE -> "status code " + response.getStatusCode();
                    case STATUS_TEXT -> "status text " + response.getStatusText();
                    case HEADERS -> "headers " + response.getHeaders();
                    case BODY -> "body " + new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                };
            } catch (IOException | RuntimeException e) {
                return e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
    }

    /**
     * A fallback of the caller works with a real timeout while the status is read: a POST with a body over
     * HttpURLConnection reads the response only when the status is asked for.
     */
    @Test
    void timeoutWhileReadingTheStatus_fallbackOfTheCallerWorks() throws IOException {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMillis(300));
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observer, ReactionModel.V2, LOG_INTERVAL))
                .build();

        // The server accepts the connection and the request, but never answers
        try (ServerSocket silentServer = new ServerSocket(0)) {
            String url = "http://localhost:" + silentServer.getLocalPort() + "/api/jobs";
            String result = inTrigger(() -> client.post().uri(url).body("{}").exchange((request, response) -> {
                try {
                    return "status " + response.getStatusCode();
                } catch (SocketTimeoutException e) {
                    return "fallback";
                }
            }));

            assertThat(result).isEqualTo("fallback");
        }
        assertThat(reactions.identified()).isEmpty();
    }

    @Test
    void failureOfTheObservation_doesNotAffectTheCall() {
        HttpActionObserver failingObserver = new HttpActionObserver(() -> {
            throw new IllegalStateException("no recorder");
        }, new HttpTargetNaming(false), new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), HttpObservationMetrics.NONE, LOG_INTERVAL);
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(failingObserver, ReactionModel.V2, LOG_INTERVAL));
        MockRestServiceServer failingObserverServer = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        IOException ioException = new IOException("connection reset");
        failingObserverServer.expect(requestTo(URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        failingObserverServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        failingObserverServer.expect(requestTo(URL)).andRespond(withException(ioException));

        assertThat(client.get().uri(URL).retrieve().body(String.class)).isEqualTo("ok");
        assertThatThrownBy(client.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
        assertThatThrownBy(client.get().uri(URL).retrieve()::toBodilessEntity)
                .isInstanceOf(ResourceAccessException.class)
                .hasCause(ioException);
        failingObserverServer.verify();
    }

    @Test
    void blockingReportOfTheIdentification_doesNotDelayTheCallOutsideOfATrigger() throws InterruptedException {
        CountDownLatch reported = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ReactionIdentifiedListener blockingListener = (reaction, identificationStart) -> {
            reported.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try (ReactionObserverService blockingService = new ReactionObserverService(blockingListener,
                ReactionObserverService.DEFAULT_REIDENTIFICATION_PERIOD, Clock.systemUTC(), ReactionObserverMetrics.NONE)) {
            ReactionRecorder recorder = new ReactionRecorder(blockingService);
            HttpActionObserver blockingObserver = new HttpActionObserver(() -> recorder, new HttpTargetNaming(false),
                    new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), HttpObservationMetrics.NONE, LOG_INTERVAL);
            RestClient.Builder builder = RestClient.builder()
                    .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(blockingObserver, ReactionModel.V2, LOG_INTERVAL));
            MockRestServiceServer blockingServer = MockRestServiceServer.bindTo(builder).build();
            RestClient client = builder.build();
            blockingServer.expect(requestTo(URL)).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

            try {
                String body = assertTimeoutPreemptively(Duration.ofSeconds(10),
                        () -> client.get().uri(URL).retrieve().body(String.class));

                assertThat(body).isEqualTo("ok");
                assertThat(reported.await(10, TimeUnit.SECONDS))
                        .describedAs("The identification of the action-only reaction is reported, in a thread of its own")
                        .isTrue();
                blockingServer.verify();
            } finally {
                release.countDown(); // before closing the service, which waits for the listener
            }
        }
    }

    private <T> T inTrigger(Supplier<T> call) {
        AtomicReference<T> result = new AtomicReference<>();
        reactions.inTrigger(() -> result.set(call.get()));
        return result.get();
    }

    private static HttpOperationRegistry unlimitedOperations() {
        return new HttpOperationRegistry(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * @return a client whose call is observed and answered with the response
     */
    private RestClient clientReturning(ClientHttpResponse response, MeterRegistry meterRegistry) {
        HttpActionObserver observerWithMetrics = new HttpActionObserver(reactions::recorder, new HttpTargetNaming(false),
                new HttpTargetRegistry(MAX_TARGET_COUNT), unlimitedOperations(), new MicrometerHttpObservationMetrics(meterRegistry), LOG_INTERVAL);
        return RestClient.builder()
                .requestInterceptor(new ReactionObservingClientHttpRequestInterceptor(observerWithMetrics, ReactionModel.V2, LOG_INTERVAL))
                .requestInterceptor((request, body, execution) -> response)
                .build();
    }

    private static double count(MeterRegistry meterRegistry, String metric, String reason) {
        return meterRegistry.get("jeap.reaction.observer." + metric).tag("reason", reason).counter().count();
    }

    /**
     * A response with an invalid status code: like the responses of Spring, it rejects the status code with a new
     * exception on every read, while the rest of the response can be read.
     */
    private static final class ResponseWithInvalidStatusCode implements ClientHttpResponse {

        static final String STATUS_TEXT = "Upstream error";
        static final String HEADER = "X-Test";
        static final String BODY = "partial";

        private final List<IllegalArgumentException> failures = new ArrayList<>();
        private int closeCount;

        @Override
        public HttpStatusCode getStatusCode() {
            IllegalArgumentException failure = new IllegalArgumentException("Status code '99' should be a three-digit positive integer");
            failures.add(failure);
            throw failure;
        }

        @Override
        public String getStatusText() {
            return STATUS_TEXT;
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HEADER, "1");
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            closeCount++;
        }

        /**
         * @return the failures to read the status code so far
         */
        List<IllegalArgumentException> failures() {
            return List.copyOf(failures);
        }

        int closeCount() {
            return closeCount;
        }
    }
}
