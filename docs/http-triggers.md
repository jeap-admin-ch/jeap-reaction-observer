# HTTP requests as triggers

With reaction model version 2 the Reaction Observer records the incoming HTTP requests of a service as
triggers, as it records the messages a service consumes. The events and commands the service sends and the
HTTP requests it makes while it handles a request become the actions of that trigger. Together with the
[HTTP requests as actions](http-requests.md) of the caller, a reaction chain continues across a REST call:
the caller reports "calls this operation", the provider reports "this operation causes that event".

This page describes the provider side of the observation. How the provider names its operations to its callers
is described in [Response header](response-header.md).

The triggers are recorded by the module `jeap-reaction-observer-http`, which is part of
`jeap-reaction-observer-starter` and is to be used through it.

## When requests are observed

Incoming requests are observed as triggers if all of this holds:

- `jeap.reaction.observer.model-version` is `2`, which needs a Reaction Observer Service of version 12.26.0 or
  later, see [Choosing reaction model version 2](http-requests.md#choosing-reaction-model-version-2), and the
  Reaction Observer is not switched off (`jeap.reaction.observer.enabled`)
- the service is a servlet web application with Spring MVC

With reaction model version 1, the default, no request is a trigger: a message sent while a request is handled
is reported as a reaction without a trigger, as before.

The response header is independent of all this. The header module sets it whatever the reaction model, and
also if the Reaction Observer is switched off. And if the header is switched off
(`jeap.reaction.observer.header.enabled=false`), requests are observed as triggers all the same, with the same
names.

## Which requests are observed

Every request that is handled synchronously and that the header module names. The trigger has the name of the
operation in the response header, `<provider> <METHOD> <path pattern>`, for example
`partner-service POST /api/partners/{}/credit-reservations` - see
[Operation name](response-header.md#operation-name-fqn). The library does not build names of its own, so
trigger and header never differ.

These requests are not observed as triggers:

| Request | Why |
|---|---|
| Rejected before it reaches a handler: 401 or 403 from a security filter, 404 without a mapping, 405, 415, a CORS preflight | No handler is called, so there is no operation to name |
| To a handler that is no controller method and no functional endpoint: static resources, Actuator, springdoc, the error page | The header module does not name them, see [What is observed](response-header.md#what-is-observed) |
| Of a service without `jeap.rest.tracing.application-name` and `spring.application.name` | Without a provider there is no name |
| With a method other than exactly `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `TRACE` and `QUERY` (methods are case-sensitive; before 11.41.0, which builds on Spring Framework 7.1, the first version that knows `QUERY`, a `QUERY` request is not observed either), to a mapping without a method condition | The header module does not name it, so that made-up methods cannot make an unbounded number of triggers out of one mapping, see [Operation name](response-header.md#operation-name-fqn) |
| To an operation whose name a caller does not accept | The header module does not name it, so that provider and caller never identify the operation differently, see [Known limits](response-header.md#known-limits) |
| Handled asynchronously (`Callable`, `DeferredResult`, `CompletableFuture`, SSE, `StreamingResponseBody`, `request.startAsync()`) | See [Asynchronous requests](#asynchronous-requests) |
| Of a WebFlux application | jEAP does not support the reactive stack |

## When a request counts

A trigger counts as a reaction if the request has been handled successfully. For a message that is simple: the
listener returns or throws. For a request the status of the response decides:

| Response | Counts |
|---|---|
| Status below 400 | Always, also without actions: a reaction with the trigger only |
| Status 400 to 499 | Only if the request caused at least one action |
| Status 500 or above, or an exception that leaves Spring MVC | Never. The actions recorded so far are discarded |

Whether a handler throws an exception that Spring MVC turns into the status (`@ResponseStatus`,
`ResponseStatusException`, a `@ControllerAdvice`) or returns the status makes no difference.

The rule is applied when Spring MVC has handled the request. An exception that leaves Spring MVC is an
exception the service did not handle, also if a filter turns it into a response afterwards. Spring Security
does so with the `AccessDeniedException` of method security (`@PreAuthorize` on a bean the handler calls): the
caller gets a 403, but the request does not count, also if it caused an action before it was denied. A service
that wants such a rejection observed like any other 4xx handles the exception itself, in a `@ControllerAdvice`.
A filter that changes the status after Spring MVC has handled the request is not seen either.

The reasons:

- **A 4xx is an answer.** In REST a 404, 409 or 422 is often the functional outcome of a request that was
  handled correctly. What corresponds to a failed message listener is the 5xx: the request was not handled.
- **A 4xx with an action counts**, so that such functional paths stay in the reaction graph with what they
  cause - for example an event that reports the rejection, or a call that was made before the request was
  rejected.
- **An exception that leaves Spring MVC never counts**, whatever becomes of it: a service that does not handle
  an exception itself did not foresee it, so it is not a functional outcome of the request.
- **A 4xx without an action does not count.** A reaction carries no status. If such requests counted, the
  reaction with the trigger only would mix the requests that were handled successfully and caused nothing with
  all rejected ones - validation errors, missing resources, conflicts, rate limiting. Without them it means
  exactly "handled successfully, nothing caused", as for a message.

What follows from the rule:

- A request that is rejected without any effect does not show up in the reaction graph. How often an operation
  answers with which status is what the HTTP metrics of the service are for.
- The rule is fixed for a reaction model version and cannot be configured: services observing by the same
  version observe the same.
- An execution with a failed outgoing call is not counted either, whatever the status of the response, see
  [Failed calls discard the execution](http-requests.md#failed-calls-discard-the-execution).

## Asynchronous requests

A request whose handler hands it over to another thread (`Callable`, `DeferredResult`, `CompletableFuture`,
SSE, `StreamingResponseBody`, or the asynchronous processing of the servlet container started with
`request.startAsync()`) is not observed as a trigger:

- When the request thread hands the request over, the outcome is not known yet.
- The recorder keeps a trigger per thread. What the request causes happens in another thread and would not be
  among the actions of the trigger - a reaction "this request causes nothing" would be wrong.

The trigger that was started is abandoned as if it had never been started. What the request thread caused
before the handover is reported like what the other thread causes: each message sent and each call made as a
reaction without a trigger, as with reaction model version 1. One limit applies: a trigger keeps at most 100
different messages and 100 different calls, so of more than that before the handover the further ones are not
reported. The response header is set all the same: the caller observes the operation as an action, and the
chain ends at the provider.

With virtual threads (`spring.threads.virtual.enabled`) nothing changes for requests handled synchronously: a
virtual thread stays the same thread while it is parked.

## How it is wired in

The module registers an interceptor of Spring MVC with the highest precedence, right after the interceptor of
the header module, which has the highest precedence as well. The `WebMvcConfigurer`s of both modules are
ordered with the highest precedence too, and Spring MVC keeps interceptors of the same order in the order of
their configurers. The interceptor therefore encloses the interceptors a service registers through a
`WebMvcConfigurer`:

- What such an interceptor causes belongs to the reaction of the request.
- A request that such an interceptor rejects is completed by the rule above, like any other.

A service can put an interceptor in front of the trigger, and in front of the interceptor of the header module,
only on purpose: with the highest precedence, through a configurer that is itself ordered with the highest
precedence (`@Order(Ordered.HIGHEST_PRECEDENCE)`) or implements `PriorityOrdered`. Spring sorts `PriorityOrdered`
configurers before all others, whatever their order value. Such an interceptor is outside of the trigger like a
`MappedInterceptor` bean (see below), and a request it rejects gets no response header either.

Spring MVC puts two kinds of interceptors in front of the registered ones, and so outside of the trigger:
`MappedInterceptor` beans and the interceptor for CORS. What a `MappedInterceptor` bean causes becomes a
reaction without a trigger, and a request it rejects is not observed.

The interceptor never affects a request: a failure of the observation is logged, at most once per
`jeap.reaction.observer.http.log-interval`, and otherwise ignored.

Nothing is left behind for the next request in the same thread, whether a request succeeds, fails or is handed
over to another thread.

### Name of the provider and name of the component

A trigger is named after the provider of the header module: `jeap.rest.tracing.application-name`, or else
`spring.application.name`. The reactions of a service are reported for the component named by
`jeap.messaging.kafka.service-name`. If the two differ, the operations of the service carry another name in
the reaction graph than the component they belong to. Nothing breaks, but the graph is harder to read: the
library logs a warning when the service starts. Set both to the same name.

## Known limits

| Situation | Behaviour |
|---|---|
| A message sent through the transactional outbox in a transaction that is rolled back, and the response has a status below 500 | The message is an action of the reaction although it was never sent: the outbox reports a message when it is stored, before the commit. With a status of 4xx the request even counts only because of that message. With a status of 500 or above the reaction is not counted, and the message does not show up |
| Error paths with actions (compensation, events reporting a failure) and a status of 500 or above | Not counted, their actions do not show up |
| Actions in other threads (`@Async`, own executors, parallel calls) | Not assigned to the trigger of the request thread; they become reactions without a trigger |
| Calls made in filters running before the `DispatcherServlet`, for example by Spring Security | Outside of the trigger; they become reactions without a trigger |
| Asynchronous requests | Not observed as triggers, their actions are reported without a trigger, see [Asynchronous requests](#asynchronous-requests) |
| A 4xx that a filter makes out of an exception that leaves Spring MVC, for example the 403 of Spring Security for a denied `@PreAuthorize` on a bean the handler calls | Not counted, also with actions, see [When a request counts](#when-a-request-counts) |
| Requests rejected without an action | Not counted, see [When a request counts](#when-a-request-counts) |
| Consumer and provider counts | The consumer records its call as an action also if the provider answers with a 4xx, or with a 5xx that names the operation in the header (a 5xx without an accepted name is a failed call for the consumer), the provider counts a request only by the rule above; the numbers of both sides do not match |
| More than 4096 distinct reactions in an instance | No further reaction is identified or counted until the instance identifies its reactions again, see [How it works](how-it-works.md#best-effort-and-bounded-by-design) |
| A Reaction Observer Service before version 12.26.0 | Cannot process the triggers of the type `http_request`: the id of a reaction with such a trigger can be longer than 1024 characters, which the service stores as of version 12.26.0. Choose model version 2 only once the Reaction Observer Service of your platform is of version 12.26.0 or later. A service that already observes by model version 2 with a version of this library before 11.39.0 upgrades the Reaction Observer Service first, then deploys the new version of the library, see [Choosing reaction model version 2](http-requests.md#choosing-reaction-model-version-2) |

## Related

- [HTTP requests as actions](http-requests.md)
- [Response header](response-header.md)
- [How it works](how-it-works.md)
- [Configuration reference](configuration.md)
