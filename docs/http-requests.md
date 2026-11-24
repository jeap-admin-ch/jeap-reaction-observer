# HTTP requests as actions

With reaction model version 2 the Reaction Observer records the outgoing HTTP requests of a service as
actions, next to the events and commands it sends. A reaction chain then no longer ends where a service
calls another one over REST instead of sending a message.

This page describes the consumer side. The provider side is described in
[Response header](response-header.md) - the response header naming the called operation - and in
[HTTP requests as triggers](http-triggers.md) - the provider observing the request as a trigger.

The consumer side is the module `jeap-reaction-observer-http`. It is part of `jeap-reaction-observer-starter`
and is to be used through it: on its own, without the recorder of the starter, it does not work.

## Purpose

The Reaction Observer exists to describe the flow of business cases through a system automatically: which
trigger makes a service do what. The reactions it reports feed the system behaviour documentation.

It is **not** a monitoring of HTTP requests. What counts is what a service does in normal operation, not how
its calls went technically:

- A temporary failure is of no interest for a business flow, and a reaction that describes one would even be
  harmful - it would document a flow that does not exist. That is why an execution with a failed call is not
  counted at all (see [Failed calls](#failed-calls-discard-the-execution)), instead of being reported with or
  without that call.
- The library judges the outcome of a call as the client returns it to the service. What happens inside the
  client before that - for example a retry by an interceptor of the client - is part of the call and not
  observed. A call that succeeds after such a retry is a normal call of the business flow.
- The metrics exist to show what the observation missed, not to monitor the called targets.

## Choosing reaction model version 2

```yaml
jeap:
  reaction:
    observer:
      model-version: 2
```

`jeap.reaction.observer.model-version` chooses the version of the reaction model the service observes by:
`1` or `2`. The default is `1`: a Reaction Observer Service that does not support reaction model version 2
cannot process observations of the type `http_request`. Choose `2` once the Reaction Observer Service of your
platform is of version 12.26.0 or later: model version 2 also observes incoming requests as triggers, and the id
of a reaction with such a trigger can be longer than 1024 characters, which the service stores as of version
12.26.0. A service that already observes by model version 2 with a version of this library before 11.39.0
has to wait the same way: upgrade the Reaction Observer Service of the platform first, then deploy the new
version of the library. A version that does not exist fails the start of the service.

All modules of the Reaction Observer have to be of the same version, including
`jeap-reaction-observer-header-starter`, which the jEAP parent adds to every service. Choose the version with
the property `jeap-reaction-observer.version` of the jEAP parent, not with a version at a single dependency:
the jEAP parent chooses the version of some of the modules by that property, whatever version a single
dependency states. A service that observes by reaction model version 2 does not start if
`jeap-reaction-observer-http` and one of the modules it uses - `jeap-reaction-observer-header-starter`,
`jeap-reaction-observer-core` and `jeap-reaction-observer-configuration` - are of different versions; the
failure names the modules and their versions. A `jeap-reaction-observer-core` before 11.37.0 does not know
the reaction models: with it a service that has an HTTP client or Spring MVC does not start either, whatever
its reaction model version, and the failure names the versions as well.

| `jeap.reaction.observer.enabled` | `jeap.reaction.observer.model-version` | Behaviour |
|---|---|---|
| any value but `true` | any | Nothing is observed, no events are published, and `model-version` is not read |
| `true` (default) | `1` (default) | Reaction model version 1: no request header, no HTTP request actions. Both reaction events report `reactionModelVersion` 1 |
| `true` | `2` | Reaction model version 2: HTTP requests are observed, outgoing ones as actions and incoming ones as [triggers](http-triggers.md). Both reaction events report `reactionModelVersion` 2 |

## What is observed

The requests of every `RestClient` built with the `RestClient.Builder` provided by Spring Boot. That includes

- clients built on a clone of that builder: HTTP interface clients (`@HttpExchange`) on top of such a
  `RestClient` and the OAuth2 clients of jEAP (`JeapOAuth2RestClientBuilderFactory`)
- declarative HTTP service clients that Spring Boot builds itself - HTTP service groups declared with
  `@ImportHttpServices` and configured with `spring.http.serviceclient.<group>.*`. The service never touches
  the builder for them, but Spring Boot applies the same customizers

**Create HTTP clients with the builder of Spring Boot.** Not observed are:

- clients created without that builder (`RestClient.create()`, `RestClient.builder()`), Feign, OkHttp, the
  AWS SDK
- `WebClient` and `RestTemplate` - jEAP does not support the reactive stack, and `RestTemplate` is deprecated
- technical calls of the jEAP security starters: the token request of an OAuth2 client and the token
  introspection of a resource server
- clients created on purpose without the observation, see
  [Clients without observation](#clients-without-observation)

## Clients without observation

The observation adds an interceptor to a client, and Spring buffers the request body of a client with an
interceptor in memory. For most clients that does not matter. A client that streams large request bodies - the
upload of a file, for example - can be created without the observation, with a builder of the
`UnobservedRestClientBuilderFactory`:

```java
@Bean
RestClient uploadClient(UnobservedRestClientBuilderFactory unobservedRestClientBuilderFactory) {
    return unobservedRestClientBuilderFactory.create()
            .baseUrl("https://documents.example.org")
            .build();
}
```

`create()` returns a new builder, configured like the `RestClient.Builder` provided by Spring Boot: all other
customizers are applied to it. For the clients built with it this means:

- Their requests are **no actions**: they are missing in the reactions of the service and so in the system
  behaviour documentation. Create only those clients without the observation that need it.
- The Reaction Observer no longer makes Spring buffer their request bodies. Whether a request body is
  streamed then depends on the rest of the client: it must not have any other interceptor, its request
  factory must not buffer, and a predicate set with `bufferContent(...)` on its builder must not apply to the
  request.

The factory is available with every configuration of the Reaction Observer, also with reaction model version 1
or with `jeap.reaction.observer.enabled=false`, and in tests with `@RestClientTest`, where its clients are bound
to the `MockRestServiceServer` like all others.

### HTTP service clients without observation

Where HTTP requests are observed, so are the clients of all HTTP service groups (`@ImportHttpServices`). A
service that needs an HTTP interface client (`@HttpExchange`) without the observation does not declare it in a
group, but builds the proxy itself on top of a `RestClient` without observation:

```java
@Bean
DocumentUploadClient documentUploadClient(UnobservedRestClientBuilderFactory unobservedRestClientBuilderFactory) {
    RestClient restClient = unobservedRestClientBuilderFactory.create()
            .baseUrl("https://documents.example.org")
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(DocumentUploadClient.class);
}
```

The settings of a group (`spring.http.serviceclient.<group>.*`) do not apply to such a client: set the base URL
and what else it needs on the builder.

## How a request is observed

For every request the consumer

1. sends the request header `JEAP-REACTION-MODEL-VERSION: 2`, asking the provider to name the operation.
   The header is sent with every observed request, also to targets outside of jEAP
2. reads the response header `JEAP-REACTION-OBSERVATION`. If there are several header lines or list
   members, the first one counts

and then decides by the outcome of that single request:

| Outcome of the request | Result |
|---|---|
| Valid `JEAP-REACTION-OBSERVATION` header with an operation name the instance accepts (see below), whatever the status (also 4xx and 5xx) | **Fine action**, named by the provider. The operation is identified and it took place |
| No valid header or an operation name over the limit, status below 500 - a provider without the header module, an external target, a 401 of a security filter, a 404 without a mapping | **Coarse action**, named by the called target and the method |
| No valid header or an operation name over the limit, status 500 or above - a 503 of an ingress, a 502 of a gateway, a 500 of an external target | **Failed call**: no action, and the execution is discarded |
| No response - I/O error, timeout, or any other exception thrown instead of a response, for example by another interceptor of the client (an open circuit breaker) | **Failed call**: no action, and the execution is discarded. The exception passes to the caller unchanged |

The name comes from another service and ends up in observation ids, events and logs, so the consumer
validates it. Spaces at the start and end of the name (also no-break spaces) are dropped first, so that they
neither make a name that looks the same a different one nor hide the mark of a coarse action. A header value
is invalid if it is not a well-formed Display String, if the name is empty after that or
longer than 600 characters, if it contains a control character, a line or paragraph separator or an
invisible format character (line break, tab, zero-width space, ...), or if it starts with `//` (the mark of a
coarse action). An oversized header line is invalid as a whole, also if its first member alone would be
valid. What follows the first member is ignored and not validated.

A valid name is accepted only as long as the instance has room for it: the number of new names a target may
contribute and the number of different names in total are limited, see
[Limits of the observation](#limits-of-the-observation). A response with a name over a limit counts like a
response without a valid header: with a status below 500 the request becomes a coarse action, with a status of
500 or above it is a failed call.

Like a message sent outside of a trigger, a request outside of a trigger (for example in a scheduler) becomes
an action-only reaction, unless it fails (see [Failed calls](#failed-calls-discard-the-execution)). An
incoming HTTP request is a trigger, too: the requests a service makes while it handles an incoming request are
actions of that request, see [HTTP requests as triggers](http-triggers.md).

The observation never affects the outcome of the request: request, response and exceptions pass unchanged,
and a failure of the observation itself is logged and otherwise ignored. The one difference concerns request
factories that read the response only when its status is asked for, see [Known limits](#known-limits).

The observation encloses all other interceptors of the client, however they are registered - with a
`RestClientCustomizer`, by a jEAP starter or on the builder: its customizer is applied after the others and
puts the observation in front of their interceptors. Only an interceptor that is put in front of all others
(`requestInterceptors(list -> list.addFirst(...))`) after that runs outside of it - by the service on the
builder, or by a customizer without an order of its own, which Spring may apply after this one.

### Fine action

```text
type  = http_request
fqn   = partner-service GET /api/partners/{}        the value of the response header, without surrounding spaces
props = {}
id    = http_request:partner-service GET /api/partners/{}
```

In the id, `%`, `:`, `#` and `&` are encoded as `%25`, `%3A`, `%23` and `%26`; everything else is kept. Unlike
events and commands, whose ids replace `:` and `#` by `_`, two operations that differ only in such a character
get different ids: `/api/jobs/{}:cancel` becomes `.../jobs/{}%3Acancel`, `/api/jobs/{}_cancel` stays as it is.
The ids of events and commands and the reaction id formula are unchanged.

### Coarse action

```text
type  = http_request
fqn   = //<host>[/<context>] <METHOD> /{*}          for example //foo.bar.admin.ch/some-service POST /{*}
props = {}
```

The target takes the place of the component name. The leading `//` marks the action as coarse; a component
name never starts with it. The path is always `/{*}`, "any path".

- **Host**: the host of the called URL in lowercase, without a trailing dot, an IPv6 address in square
  brackets. The scheme is not part of the name. It is the host of the request as sent, not the target of a
  redirect.
- **Port**: not part of the name by default. With `jeap.reaction.observer.http.target-host.include-port=true`
  a port stated in the URL that is not the default port of the scheme is added to the host
  (`//foo.bar.admin.ch:8443/some-service`). Switch it on if the service calls several targets on the same
  host that differ in the port only.
- **Context**: several services may share a host and differ in the first path segment only. The first path
  segment is the context of the called service if the second one is an API root: a segment named `api`,
  ending with `-api` (`ui-api`, `external-api`) or starting with `api-` (`api-internal`), in lowercase.
  The context consists of the characters `[A-Za-z0-9._-]`, has at most 100 characters and is not an API root
  itself. The path is taken as it is in the request, without decoding or normalising.

| Request | Coarse action |
|---|---|
| `POST https://foo.bar.admin.ch/some-service/api/some/path/12354` | `//foo.bar.admin.ch/some-service POST /{*}` |
| `GET https://jme-nivel.bazg.admin.ch/jme-blabla-service/ui-api/foo/134` | `//jme-nivel.bazg.admin.ch/jme-blabla-service GET /{*}` |
| `GET https://foo.bar.admin.ch/api/orders/12354` | `//foo.bar.admin.ch GET /{*}` - no context |
| `GET https://foo.bar.admin.ch/some-service/actuator/health` | `//foo.bar.admin.ch GET /{*}` - the second segment is no API root |
| `GET https://foo.bar.admin.ch/tenants/4711/api/orders` | `//foo.bar.admin.ch GET /{*}` - the API root is not the second segment |

A coarse action says that the service called the target and got an answer with a status below 500 that does
not name the operation, or that names one over the limit of operation names. It does not prove that the
provider itself answered or that an operation ran - the answer may be a 401 or 404 of a gateway. Once the
provider delivers the header, its operations take the place of the coarse action, within the
[limits of operation names](#limits-of-the-observation).

These rules are a fixed part of the library and not configurable: the target is part of the reaction id, so
changing a rule would rename coarse actions and the reactions they are part of.

### Failed calls discard the execution

A call has failed if it reached the interceptors of the client and there is no response - the call ends with
an exception, whatever its cause - or if the response has a status of 500 or above and no valid header (or a
header with an operation name over the limit, which counts like no valid header). A
failure to read the status of the response counts as no response: no response arrived, the call failed. That
holds also if the service catches the failure, for example in an `exchange` callback, and carries on with a
fallback, or if it never looks at the response and so does not notice the failure. Then

- there is no action for the call, and
- the execution of the current trigger is discarded as a whole: its reaction is neither identified nor
  counted - not with the failed call, and not without it. That includes the trigger and all other actions of
  that execution, also the messages sent.

A coarse action for an operational failure would produce a reaction that describes the failure, not what the
service does. Only what has been identified with certainty is counted.

- The mark lasts until the end of the execution. A later successful call does not undo it, also not a retry
  the service makes itself (for example with `@Retryable` around the call): each attempt is a call of its
  own. The next execution of the same trigger counts as usual.
- A retry inside the client is different: if an interceptor of the client repeats the request and the
  repetition succeeds, the service gets one successful call. The failed attempt is not observed, there is a
  fine or coarse action as for any successful call, and the execution counts (see [Purpose](#purpose)).
- With nested triggers only the execution of the innermost current trigger is discarded.
- Only the outcome of the single call counts. The library does not remember which targets delivered the
  header before. What it remembers serves the limits alone: the targets of coarse actions and the operation
  names accepted so far.
- Outside of a trigger a failed call simply does not become an action-only reaction.
- A failed call is visible in the loss metric (`call_failed`) and in the log (at most one entry per
  `jeap.reaction.observer.http.log-interval`, one minute by default).

## Limits of the observation

- **Targets**: an instance names coarse actions for at most 100 different targets (host and, where
  recognised, context), counted from its start. Requests to further targets that do not name their operation
  are not observed. This bounds the vocabulary of an instance if targets come from data (webhooks,
  callbacks). Which targets are among the first 100 depends on the instance and on the order of its calls.
  A service that calls more targets raises the limit with `jeap.reaction.observer.http.max-target-count`;
  with `0` an instance names no coarse actions at all, fine actions are not affected. As `0` is set on
  purpose, it writes no log entry about the limit; the loss metric still counts these requests as
  `target_limit`.
- **Operation names**: the operation names an instance has accepted form one stock that all targets share,
  counted from its start:
  - A target (the called host and, where recognised, context - as in the name of a coarse action) may
    contribute at most 100 new names to the stock.
  - A name that is already accepted may be named by every target, also by one that may not contribute a new
    name any more. It is an action already.
  - The instance accepts at most 1000 different names in total.

  The names come from the called targets, and each new name is a new action: a target that names a different
  operation with every response - by mistake or not - would otherwise create a new reaction per call, until the
  instance reaches its limit of reactions. A request whose response names an operation that is not accepted is
  observed like one whose operation is not named: as a coarse action of that target, which is subject to the
  limit of targets above, or as a failed call if the status is 500 or above. Accepted names stay accepted. A
  target at its limit leaves the other targets their share - as long as the limit in total is not reached:
  from then on no new name is accepted from any target until the instance restarts.
  The limits bound the names, not the reactions: the accepted names still combine with the triggers that call
  the target and with each other, see [Known limits](#known-limits).
  New names are counted by the called target, not by the component a name claims to belong to. The limit in
  total is there because the limit per target bounds nothing if the targets themselves come from data. Which
  names are among the accepted ones depends on the instance and on the order of its calls. A service that
  calls more operations raises the limits with
  `jeap.reaction.observer.http.max-new-operation-count-per-target` and
  `jeap.reaction.observer.http.max-operation-count`; with `0` for either an instance accepts no operation names
  at all and writes no warning about the limit. The diagnosis metric counts these requests as
  `operation_limit`.
- **Actions per trigger**: at most 100 HTTP request actions per trigger are recorded, in a budget of their
  own. The limit of 100 message actions per trigger is unchanged, so HTTP requests never push a message out
  of a reaction.
- **Reactions**: unchanged, at most 4096 distinct reactions per instance, see [Known limits](#known-limits).

## Metrics

If the service has a `MeterRegistry`, two counters are published. Neither carries the called host.

| Metric (Prometheus name) | `reason` | Counts |
|---|---|---|
| `jeap_reaction_observer_unidentified_http_calls_total` | `no_header`, `invalid_header`, `operation_limit`, `no_response` | Diagnosis: every request that was not identified by an operation name of the provider, exactly once and at the time of the request - also outside of a trigger and in a discarded execution |
| `jeap_reaction_observer_dropped_http_calls_total` | `call_failed`, `target_limit`, `http_action_limit` | Loss: every observed request that did not become an action, exactly once. The one exception: a request to a URI without a host has no action and no loss reason |

- `call_failed` counts the failed calls, not the discarded executions.
- A failed call is detected before the target limit is checked: it does not take one of the targets, and
  a request has at most one loss reason.
- A request without a header to a target over the limit counts once in each metric (`no_header` and
  `target_limit`); a failed call likewise (its diagnosis reason and `call_failed`).
- A request whose response names an operation that is not accepted counts as `operation_limit` in the
  diagnosis metric. It is not a loss as long as it becomes a coarse action; it counts in the loss metric too if
  the target is over the limit of targets (`target_limit`), if the trigger already has its maximum of HTTP
  request actions (`http_action_limit`) or if the status is 500 or above (`call_failed`).
- Only HTTP requests are counted: neither the limit of message actions per trigger, nor the limit of
  reactions per instance, nor the other actions of a discarded execution.

## Known limits

| Situation | Behaviour |
|---|---|
| A target fails permanently (no response or 5xx without header in every execution) | The service reports nothing for the triggers that call it, not even their message actions. Visible in the loss metric (`call_failed`) and in the log |
| Retries | A retry inside the client (an interceptor repeating the request) is not observed, only the final outcome of the call counts. A retry made by the service itself is observed attempt by attempt, and a failed attempt discards the execution even if the retry succeeds |
| A call fails before it reaches the interceptors of the client | Not observed: Spring runs the request initializers of a client before its interceptors. The OAuth2 clients of jEAP get their token in a request initializer, so a failed token request is not a failed call. If the service catches the exception and carries on, the execution counts and its reaction shows what the service did instead - unlike after a failed call, which discards the execution |
| Resilience outside of the client (a circuit breaker, bulkhead or time limiter around a method, for example Resilience4j `@CircuitBreaker` with a fallback method) | Not observed: while the circuit breaker is open, no call reaches the client, the fallback runs, and the execution counts as a normal reaction - one that describes an operational failure. Use resilience as an interceptor of the client instead: a call it aborts is a failed call and discards the execution |
| Response body | The observation decides when status and headers of the response are there; a timeout before that is a failed call, whichever HTTP client library is used. If reading the body fails afterwards, the call still counts as answered |
| Request factory that reads the response only when its status is asked for (`SimpleClientHttpRequestFactory` with a request body; Spring Boot chooses it only if no other HTTP client is available, for example on a runtime without the module `java.net.http`) | The observation reads the status first. If that fails, the call is a failed call and the response passes unchanged: reading it, the caller gets an exception of the same class and with the same message as without the observation, but HttpURLConnection gives a new instance with the original one as its cause. A runtime exception while the response is read (for example from a custom `CookieHandler`) reaches a caller that reads the headers first (as `retrieve().body(...)` does) wrapped in a `RuntimeException` |
| Request body | Spring buffers the request body of a `RestClient` in memory as soon as the client has an interceptor, and this library adds one. A client that streamed a large upload before no longer does so with reaction model version 2, unless it is created without the observation, see [Clients without observation](#clients-without-observation) |
| Calls in other threads (`@Async`, own executors) | Not assigned to the trigger of the calling thread; they become action-only reactions |
| Calls while an incoming HTTP request is handled | The request is the trigger of these calls, see [HTTP requests as triggers](http-triggers.md). If the request is handled asynchronously or is not observed for another reason, the calls are outside of a trigger and become action-only reactions |
| Conditional calls without header | Every conditional coarse action splits a reaction into variants; k independent ones yield up to 2^k reactions. A 401 of a provider that otherwise delivers the header yields a rare reaction with a coarse action |
| A target names many different operations, for example because its paths are not templated (`GET /api/partners/4711`) | The names are accepted up to the limits of operation names, each as an action of its own. Combined with the triggers that call the target and with each other they can still make up more reactions than the 4096 an instance identifies |
| Calls to a varying selection of targets (for example webhooks per subscriber) | Each selection is a reaction of its own. Once an instance has identified 4096 reactions, it identifies no new ones - of messages neither - until it restarts; logged once as a warning |
| Retry within a trigger, first answer without header (status below 500), then with header | The reaction contains the coarse and the fine action |
| Several providers behind one host without a recognisable context | The coarse action names the host, i.e. the gateway. The providers share the limit of new operation names per target |
| A target names operations that are not its own | The consumer cannot check the name a target gives its operation: a target can name operations of any component, and they appear in the system behaviour documentation as called by the service. Their number is limited, see [Limits of the observation](#limits-of-the-observation) |
| Context | Only recognised in front of an API root. The same service shows up as `//<host>/<context>` for calls to an API root and as `//<host>` for all other paths. A first segment in front of an API root that is no context (`/rest/api/...`, `/v3/api-docs`) is read as one; if it is variable, it takes up targets until the limit is reached |
| Stages | Hosts differ per stage, so the names of coarse actions and the ids of reactions containing them cannot be compared between environments |
| Actuator, `/error` and springdoc endpoints of a provider | The header module does not name them; calls to them are coarse actions if the status is below 500. A status of 500 or above discards the execution, see [Failed calls](#failed-calls-discard-the-execution) |
| Consumer and provider counts | The consumer records the action also for 4xx answers and for 5xx answers that name the operation in the header (a 5xx without an accepted name is a failed call), the provider counts a request only if it was handled successfully, see [When a request counts](http-triggers.md#when-a-request-counts); the numbers of both sides do not match |
| Shared HTTP caches between consumer and provider | Not supported, see [Response header](response-header.md) |
| Providers without this library | A provider reports reactions only with the Reaction Observer itself. With the header module alone, its operations appear as actions of its consumers, but the chain ends there; without the header module, calls to it can only be observed as coarse actions |

## Events

- `ReactionIdentifiedEvent` 2.1.0 and `ReactionsObservedEvent` 1.1.0 report `reactionModelVersion`: the
  version the service observes by (1 or 2, following the property above).
- The counters of unidentified actions in `ReactionsObservedEvent` are never set and stay 0. The value 0 does
  not prove that a reaction is complete.
- The idempotence id of `ReactionIdentifiedEvent` is
  `ri_<service>_<serviceInstanceIdentifier>_<reactionId>_v<reactionModelVersion>_<identificationStart>`. The
  service instance identifier is a UUID generated at the start of the instance, the same as in the idempotence
  id of `ReactionsObservedEvent`. The identification start is the time at which the instance began to report
  its reactions, in UTC (`20261026T041233Z`); currently that is the start of the instance. The id identifies
  one report: a service reports the same reaction once per instance, again after a restart, and with another
  model version after an upgrade or a rollback, each time with an id of its own. A consumer processing the
  events idempotently processes each of these reports once.

## Testing

`jeap-reaction-observer-test` contains helpers for tests with HTTP request observations:

- `HttpRequestObservations`: names and ids of fine and coarse actions, the header value a provider names an
  operation with, and `withObservation(...)` to stub a provider with a `MockRestServiceServer`
- `ReactionIdentifiedEvents`: reads the reaction of a `ReactionIdentifiedEvent`, for example
  `hasHttpRequestAction(fqn)` for `ReactionEventsTestConsumer.awaitReactionIdentifiedEvent(...)`

## Related

- [HTTP requests as triggers](http-triggers.md)
- [Response header](response-header.md)
- [How it works](how-it-works.md)
- [Configuration reference](configuration.md)
