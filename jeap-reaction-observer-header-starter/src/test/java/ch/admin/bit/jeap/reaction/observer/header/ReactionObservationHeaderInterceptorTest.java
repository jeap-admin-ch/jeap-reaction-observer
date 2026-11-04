package ch.admin.bit.jeap.reaction.observer.header;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_MODEL_VERSION;
import static ch.admin.bit.jeap.reaction.observer.header.ReactionObserverHeaderNames.REACTION_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of the interceptor's branching that is awkward to pin down through a real embedded
 * container: merging with a {@code Vary} header some earlier component already set, re-applying the
 * header after an {@code ERROR} dispatch, delegating to a {@link ProtectedObservationHeaderResponse} when
 * one is present, and the fail-safe guarantee that a failure here never fails the request.
 */
@ExtendWith(MockitoExtension.class)
class ReactionObservationHeaderInterceptorTest {

    @Mock
    private OperationNameResolver operationNameResolver;

    private ReactionObservationHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Built here, not as a field initializer: @Mock fields are injected by MockitoExtension after
        // construction, so a field initializer would have captured null instead of the mock.
        interceptor = new ReactionObservationHeaderInterceptor(operationNameResolver);
    }

    @Test
    void preHandle_requestDispatch_withSignal_setsHeaderAndVary() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse response = responseWithoutExistingVary();
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        boolean proceed = interceptor.preHandle(request, response, "handler");

        assertThat(proceed).isTrue();
        verify(response).setHeader(REACTION_OBSERVATION, ReactionObservationEncoder.encode("test-app GET /api/x"));
        verify(response).addHeader("Vary", REACTION_MODEL_VERSION);
    }

    @Test
    void preHandle_requestDispatch_withoutSignal_setsVaryButNoObservationHeader() {
        HttpServletRequest request = requestDispatch(null, null);
        HttpServletResponse response = responseWithoutExistingVary();
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, response, "handler");

        verify(response, never()).setHeader(eq(REACTION_OBSERVATION), anyString());
        verify(response).addHeader("Vary", REACTION_MODEL_VERSION);
    }

    @Test
    void preHandle_operationNotObserved_touchesNeitherHeader() {
        HttpServletRequest request = requestDispatch(null, null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.empty());

        interceptor.preHandle(request, response, "handler");

        verify(response, never()).addHeader(eq("Vary"), anyString());
        verify(response, never()).setHeader(eq(REACTION_OBSERVATION), anyString());
    }

    @Test
    void preHandle_existingVaryHeader_appendsInsteadOfReplacing() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeaders("Vary")).thenReturn(List.of("Accept-Language"));
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, response, "handler");

        verify(response).addHeader("Vary", REACTION_MODEL_VERSION);
        verify(response, never()).setHeader(eq("Vary"), anyString());
    }

    @Test
    void preHandle_existingVaryHeader_alreadyContainingTheToken_isLeftAsIs() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeaders("Vary")).thenReturn(List.of(REACTION_MODEL_VERSION));
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, response, "handler");

        verify(response, never()).addHeader(eq("Vary"), anyString());
        verify(response, never()).setHeader(eq("Vary"), anyString());
    }

    @Test
    void preHandle_tokenOnLaterVaryLine_isNotDuplicated() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeaders("Vary")).thenReturn(List.of("Accept-Language", "Origin, " + REACTION_MODEL_VERSION));
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, response, "handler");

        verify(response, never()).addHeader(eq("Vary"), anyString());
    }

    @Test
    void preHandle_usesProtectedResponse_whenOneIsPresent() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse underlying = mock(HttpServletResponse.class);
        ProtectedObservationHeaderResponse protectedResponse =
                new ProtectedObservationHeaderResponse(underlying, REACTION_OBSERVATION);
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, protectedResponse, "handler");

        verify(underlying).setHeader(REACTION_OBSERVATION, ReactionObservationEncoder.encode("test-app GET /api/x"));
        // Locked now: a later attempt to overwrite it (the BFF scenario) must be dropped.
        protectedResponse.setHeader(REACTION_OBSERVATION, "some-downstream-value");
        verify(underlying, never()).setHeader(REACTION_OBSERVATION, "some-downstream-value");
    }

    @Test
    void preHandle_usesProtectedResponse_whenWrappedByALaterFilter() {
        HttpServletRequest request = requestDispatch(REACTION_MODEL_VERSION, "2");
        HttpServletResponse underlying = mock(HttpServletResponse.class);
        ProtectedObservationHeaderResponse protectedResponse =
                new ProtectedObservationHeaderResponse(underlying, REACTION_OBSERVATION);
        HttpServletResponse outerWrapper = new HttpServletResponseWrapper(protectedResponse);
        when(operationNameResolver.resolve(request, "handler")).thenReturn(Optional.of("test-app GET /api/x"));

        interceptor.preHandle(request, outerWrapper, "handler");

        outerWrapper.addHeader(REACTION_OBSERVATION, "some-downstream-value");
        verify(underlying, never()).addHeader(REACTION_OBSERVATION, "some-downstream-value");
    }

    @Test
    void preHandle_errorDispatch_reappliesStoredFqn_whenHeaderIsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ERROR);
        when(request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE)).thenReturn("test-app GET /api/x");
        when(request.getHeader(REACTION_MODEL_VERSION)).thenReturn("2");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeader(REACTION_OBSERVATION)).thenReturn(null);

        interceptor.preHandle(request, response, "errorHandler");

        verify(response).setHeader(REACTION_OBSERVATION, ReactionObservationEncoder.encode("test-app GET /api/x"));
        verify(response).addHeader("Vary", REACTION_MODEL_VERSION);
    }

    @Test
    void preHandle_errorDispatch_withoutSignal_reappliesVaryOnly() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ERROR);
        when(request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE)).thenReturn("test-app GET /api/x");
        HttpServletResponse response = mock(HttpServletResponse.class);

        interceptor.preHandle(request, response, "errorHandler");

        verify(response).addHeader("Vary", REACTION_MODEL_VERSION);
        verify(response, never()).setHeader(eq(REACTION_OBSERVATION), anyString());
    }

    @Test
    void preHandle_errorDispatch_headerAlreadyPresent_doesNotOverwriteIt() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ERROR);
        when(request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE)).thenReturn("test-app GET /api/x");
        when(request.getHeader(REACTION_MODEL_VERSION)).thenReturn("2");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeader(REACTION_OBSERVATION)).thenReturn("already-there");

        interceptor.preHandle(request, response, "errorHandler");

        verify(response, never()).setHeader(eq(REACTION_OBSERVATION), anyString());
    }

    @Test
    void preHandle_errorDispatch_withoutAStoredFqn_doesNothing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ERROR);
        when(request.getAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE)).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        boolean proceed = interceptor.preHandle(request, response, "errorHandler");

        assertThat(proceed).isTrue();
        verify(response, never()).setHeader(eq(REACTION_OBSERVATION), anyString());
    }

    @Test
    void preHandle_resolverThrows_doesNotPropagate_andStillLetsTheRequestProceed() {
        HttpServletRequest request = requestDispatch(null, null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(operationNameResolver.resolve(request, "handler")).thenThrow(new RuntimeException("boom"));

        boolean proceed = interceptor.preHandle(request, response, "handler");

        assertThat(proceed).isTrue();
    }

    private static HttpServletRequest requestDispatch(String signalHeaderName, String signalValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
        if (signalHeaderName != null) {
            when(request.getHeader(signalHeaderName)).thenReturn(signalValue);
        }
        return request;
    }

    private static HttpServletResponse responseWithoutExistingVary() {
        return mock(HttpServletResponse.class);
    }
}
