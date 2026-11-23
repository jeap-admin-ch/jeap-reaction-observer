package ch.admin.bit.jeap.reaction.observer.http;

import ch.admin.bit.jeap.reaction.observer.core.domain.ReactionRecorder;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Observation;
import ch.admin.bit.jeap.reaction.observer.core.domain.model.Reaction;
import ch.admin.bit.jeap.reaction.observer.header.OperationNameResolver;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ReactionObservingHandlerInterceptorTest {

    private static final String FQN = "provider-app POST /api/orders/{}";
    private static final Observation TRIGGER = Observation.ofHttpRequest(FQN);
    private static final Observation EVENT = Observation.ofEvent("OrderCreated", "order-topic");
    private static final Observation OTHER_EVENT = Observation.ofEvent("OtherEvent", "other-topic");
    private static final String FAILURE_LOG = "Failed to observe an incoming HTTP request as a trigger";

    private final ObservedReactions reactions = new ObservedReactions();
    private final ReactionObservingHandlerInterceptor interceptor = interceptor(reactions::recorder);
    private final MockHttpServletRequest request = request();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Object handler = handlerMethod();

    @Test
    void requestHandledSuccessfully_reactionWithTheActions() {
        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        reactions.recorder().onAction(EVENT);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).containsExactly(new Reaction(TRIGGER, List.of(EVENT)));
        assertThat(reactions.countsSinceLastCall()).containsValue(1);
        assertNoTriggerIsOpen();
    }

    @Test
    void requestHandledSuccessfullyWithoutActions_triggerOnlyReaction() {
        interceptor.preHandle(request, response, handler);
        response.setStatus(204);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).containsExactly(new Reaction(TRIGGER, List.of()));
        assertNoTriggerIsOpen();
    }

    @Test
    void status4xxWithAction_reaction() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        response.setStatus(409);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).containsExactly(new Reaction(TRIGGER, List.of(EVENT)));
        assertNoTriggerIsOpen();
    }

    @Test
    void status4xxWithoutAction_noReaction() {
        interceptor.preHandle(request, response, handler);
        response.setStatus(409);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).isEmpty();
        assertThat(reactions.countsSinceLastCall()).isEmpty();
        assertNoTriggerIsOpen();
    }

    @Test
    void status5xx_noReaction_actionsAreDiscarded() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        response.setStatus(500);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).isEmpty();
        assertNoTriggerIsOpen();
    }

    @Test
    void unhandledException_noReaction_thoughTheStatusIsStill200() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        interceptor.afterCompletion(request, response, handler, new IllegalStateException("unhandled"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(reactions.identified()).isEmpty();
        assertNoTriggerIsOpen();
    }

    @Test
    void failedActionDiscardedTheExecution_noReaction() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        reactions.recorder().onActionFailed();
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).isEmpty();
        assertNoTriggerIsOpen();
    }

    @Test
    void headerModuleStoredTheName_triggerHasThatName() {
        request.setAttribute(OperationNameResolver.OPERATION_NAME_ATTRIBUTE, "name-of-the-header-module");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified())
                .containsExactly(new Reaction(Observation.ofHttpRequest("name-of-the-header-module"), List.of()));
    }

    @Test
    void handlerTheHeaderModuleDoesNotName_notObserved() {
        Object staticResourceHandler = new ResourceHttpRequestHandler();

        interceptor.preHandle(request, response, staticResourceHandler);
        assertThat(request.getAttribute(ReactionObservingHandlerInterceptor.TRIGGER_OPEN_ATTRIBUTE)).isNull();
        assertNoTriggerIsOpen();
        interceptor.afterCompletion(request, response, staticResourceHandler, null);

        assertThat(reactions.identified()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = "REQUEST", mode = EnumSource.Mode.EXCLUDE)
    void otherDispatchThanRequest_notObserved(DispatcherType dispatcherType) {
        request.setDispatcherType(dispatcherType);

        interceptor.preHandle(request, response, handler);
        assertNoTriggerIsOpen();
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).isEmpty();
    }

    /**
     * A forward inside the request is dispatched in the same thread, with the attributes of the request.
     */
    @Test
    void forwardInsideTheRequest_neitherStartsNorEndsATrigger() {
        interceptor.preHandle(request, response, handler);

        request.setDispatcherType(DispatcherType.FORWARD);
        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);
        request.setDispatcherType(DispatcherType.REQUEST);

        reactions.recorder().onAction(EVENT);
        interceptor.afterCompletion(request, response, handler, null);
        assertThat(reactions.identified()).containsExactly(new Reaction(TRIGGER, List.of(EVENT)));
        assertNoTriggerIsOpen();
    }

    @Test
    void asyncHandling_noReactionOfTheRequest_theActionsBecomeActionOnlyReactions() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        reactions.recorder().onAction(OTHER_EVENT);
        reactions.recorder().onAction(EVENT);

        interceptor.afterConcurrentHandlingStarted(request, response, handler);

        assertThat(reactions.identified()).containsExactly(Reaction.actionOnly(EVENT), Reaction.actionOnly(OTHER_EVENT));
        assertThat(reactions.countsSinceLastCall())
                .describedAs("every occurrence of an action counts, as without a trigger")
                .containsEntry(Reaction.actionOnly(EVENT).id(), 2)
                .containsEntry(Reaction.actionOnly(OTHER_EVENT).id(), 1);
        assertThat(request.getAttribute(ReactionObservingHandlerInterceptor.TRIGGER_OPEN_ATTRIBUTE)).isNull();
        assertNoTriggerIsOpen();
    }

    @Test
    void asyncHandling_insideAnotherTrigger_theActionsStayWithTheOuterTrigger_andTheAsyncDispatchEndsNoTrigger() {
        // given: the request thread hands the request over while it is inside another trigger
        reactions.recorder().onTriggerStart(ObservedReactions.TRIGGER);
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);

        // when: concurrent handling starts
        interceptor.afterConcurrentHandlingStarted(request, response, handler);

        // then: the trigger of the request is gone, the thread is back in the outer trigger with the action
        assertThat(request.getAttribute(ReactionObservingHandlerInterceptor.TRIGGER_OPEN_ATTRIBUTE)).isNull();
        // and: the later async dispatch of the same request does not end the outer trigger
        request.setDispatcherType(DispatcherType.ASYNC);
        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);
        reactions.recorder().onAction(OTHER_EVENT);
        reactions.recorder().onTriggerHandled();
        reactions.recorder().afterTrigger();

        assertThat(reactions.identified())
                .containsExactly(new Reaction(ObservedReactions.TRIGGER, List.of(EVENT, OTHER_EVENT)));
        assertNoTriggerIsOpen();
    }

    /**
     * A handler can start the asynchronous processing of the servlet container itself. Spring MVC does not
     * know about it and completes the request as if it had been handled synchronously.
     */
    @Test
    void asyncProcessingStartedByTheHandlerItself_noReactionOfTheRequest_theActionBecomesAnActionOnlyReaction() {
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        request.setAsyncStarted(true);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(reactions.identified()).containsExactly(Reaction.actionOnly(EVENT));
        assertThat(request.getAttribute(ReactionObservingHandlerInterceptor.TRIGGER_OPEN_ATTRIBUTE)).isNull();
        assertNoTriggerIsOpen();
    }

    /**
     * If the handling fails after concurrent handling has started, Spring calls both callbacks: first
     * {@code afterCompletion} with the exception, then {@code afterConcurrentHandlingStarted}.
     */
    @Test
    void exceptionAfterConcurrentHandlingStarted_bothCallbacks_theTriggerIsEndedOnce() {
        // given: the thread is inside another trigger
        reactions.recorder().onTriggerStart(ObservedReactions.TRIGGER);
        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        request.setAsyncStarted(true);

        // when
        interceptor.afterCompletion(request, response, handler, new IllegalStateException("test"));
        interceptor.afterConcurrentHandlingStarted(request, response, handler);

        // then: only the trigger of the request has been ended, the thread is still inside the outer trigger
        reactions.recorder().onAction(OTHER_EVENT);
        reactions.recorder().onTriggerHandled();
        reactions.recorder().afterTrigger();
        assertThat(reactions.identified())
                .containsExactly(new Reaction(ObservedReactions.TRIGGER, List.of(EVENT, OTHER_EVENT)));
        assertNoTriggerIsOpen();
    }

    @Test
    void errorDispatchAfterTheRequest_endsNoTrigger() {
        // given: the thread is inside another trigger, and the request is completed with an error status
        reactions.recorder().onTriggerStart(ObservedReactions.TRIGGER);
        interceptor.preHandle(request, response, handler);
        response.setStatus(500);
        interceptor.afterCompletion(request, response, handler, null);

        // when: the container dispatches the same request to the error page
        request.setDispatcherType(DispatcherType.ERROR);
        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        // then: the thread is still inside the outer trigger
        reactions.recorder().onAction(OTHER_EVENT);
        reactions.recorder().onTriggerHandled();
        reactions.recorder().afterTrigger();
        assertThat(reactions.identified()).containsExactly(new Reaction(ObservedReactions.TRIGGER, List.of(OTHER_EVENT)));
    }

    @Test
    void requestInsideAnotherTrigger_reactionOfItsOwn_theOuterTriggerKeepsItsActions() {
        reactions.recorder().onTriggerStart(ObservedReactions.TRIGGER);
        reactions.recorder().onAction(OTHER_EVENT);

        interceptor.preHandle(request, response, handler);
        reactions.recorder().onAction(EVENT);
        interceptor.afterCompletion(request, response, handler, null);

        reactions.recorder().onTriggerHandled();
        reactions.recorder().afterTrigger();
        assertThat(reactions.identified()).containsExactly(
                new Reaction(TRIGGER, List.of(EVENT)),
                new Reaction(ObservedReactions.TRIGGER, List.of(OTHER_EVENT)));
        assertNoTriggerIsOpen();
    }

    @Test
    void triggerCannotBeMarkedAsOpen_theTriggerIsEndedRightAway_theRequestContinues(CapturedOutput output) {
        MockHttpServletRequest failingRequest = new MockHttpServletRequest("POST", "/api/orders/42") {
            @Override
            public void setAttribute(String name, Object value) {
                if (ReactionObservingHandlerInterceptor.TRIGGER_OPEN_ATTRIBUTE.equals(name)) {
                    throw new IllegalStateException("simulated failure");
                }
                super.setAttribute(name, value);
            }
        };
        failingRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders/{id}");

        assertThat(interceptor.preHandle(failingRequest, response, handler)).isTrue();
        interceptor.afterCompletion(failingRequest, response, handler, null);

        assertThat(output).contains(FAILURE_LOG);
        assertThat(reactions.identified()).isEmpty();
        assertNoTriggerIsOpen();
    }

    @Test
    void recorderIsNotAvailable_theRequestContinues(CapturedOutput output) {
        ReactionObservingHandlerInterceptor withoutRecorder = interceptor(() -> {
            throw new IllegalStateException("no recorder");
        });

        assertThat(withoutRecorder.preHandle(request, response, handler)).isTrue();
        withoutRecorder.afterCompletion(request, response, handler, null);
        withoutRecorder.afterConcurrentHandlingStarted(request, response, handler);

        assertThat(output).contains(FAILURE_LOG);
    }

    @Test
    void publishingTheReactionFails_theTriggerIsEnded_theRequestContinues(CapturedOutput output) {
        ReactionRecorder failingRecorder = new ReactionRecorder(null) {
            @Override
            public void onTriggerHandled() {
                throw new IllegalStateException("simulated failure");
            }

            @Override
            public void onTriggerStart(Observation trigger) {
                reactions.recorder().onTriggerStart(trigger);
            }

            @Override
            public boolean currentTriggerHasActions() {
                return reactions.recorder().currentTriggerHasActions();
            }

            @Override
            public void afterTrigger() {
                reactions.recorder().afterTrigger();
            }
        };
        ReactionObservingHandlerInterceptor failing = interceptor(() -> failingRecorder);

        failing.preHandle(request, response, handler);
        failing.afterCompletion(request, response, handler, null);

        assertThat(output).contains(FAILURE_LOG);
        assertNoTriggerIsOpen();
    }

    /**
     * Outside a trigger an action is published as an action-only reaction, inside a trigger it is not.
     */
    private void assertNoTriggerIsOpen() {
        Observation probe = Observation.ofEvent("Probe" + System.nanoTime(), "probe-topic");
        reactions.recorder().onAction(probe);
        assertThat(reactions.identified()).contains(Reaction.actionOnly(probe));
        reactions.forget(Reaction.actionOnly(probe));
    }

    private static ReactionObservingHandlerInterceptor interceptor(Supplier<ReactionRecorder> recorder) {
        return new ReactionObservingHandlerInterceptor(recorder, new OperationNameResolver("provider-app"), Duration.ZERO);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders/{id}");
        return request;
    }

    private static HandlerMethod handlerMethod() {
        try {
            return new HandlerMethod(new OrderController(), OrderController.class.getDeclaredMethod("order"));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static class OrderController {

        @PostMapping("/api/orders/{id}")
        void order() {
            // Nothing to do, the tests are about the interceptor
        }
    }
}
