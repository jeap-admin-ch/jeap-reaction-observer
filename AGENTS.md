# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to *use* the library in a
consuming service, read [README.md](README.md) and the [docs/](docs/) folder instead.

## Project

jEAP Reaction Observer is a multi-module Maven library that detects, tracks, and documents how a
microservice reacts to incoming events and commands. It hooks into jEAP Messaging: for each consumed
message (the *trigger*) it records the events/commands the service produces in response (the
*actions*), forms a *reaction*, and publishes that runtime behaviour as Kafka events. With reaction model
version 2 an incoming HTTP request is a trigger and an outgoing HTTP request an action, too. The events are
consumed by the jEAP Reaction Observer Service and feed the jEAP ArchRepo system-behaviour
documentation. Recording is best-effort and must never affect business logic.

**Purpose - keep it in mind when changing or reviewing the code:** the library describes the *flow of business
cases* (which trigger makes a service do what), so that the system behaviour documentation can be generated.
It is not a monitoring of messages or HTTP requests. Reactions must show what a service does in normal
operation; a reaction caused by a temporary technical failure would document a flow that does not exist.
Hence an execution with a failed HTTP call is deliberately not reported at all, and what happens inside an
HTTP client before it returns to the service (for example a retry by a client interceptor) is deliberately
not observed - only the outcome of the call as the client returns it to the service counts. Missing a recovered temporary failure is intended,
not a defect. See [docs/http-requests.md](docs/http-requests.md). An incoming HTTP request counts as a reaction
by a fixed rule on the status of its response (below 400 always, 400 to 499 only with at least one action, 500
or above and unhandled exceptions never); the rule is deliberately not configurable, so that services
observing by the same reaction model version observe the same. See [docs/http-triggers.md](docs/http-triggers.md).

## Repository layout

```
pom.xml                              # Parent POM (packaging=pom); declares the modules below
jeap-reaction-observer-core/         # Domain model + recording: Observation, Reaction, ReactionRecorder, ReactionObserverService
jeap-reaction-observer-configuration/ # Settings of the library as a whole: ReactionObserverProperties, @ConditionalOnReactionObserverEnabled
jeap-reaction-observer-messaging/    # ObserverKafkaMessageCallback: JeapKafkaMessageCallback feeding the recorder
jeap-reaction-observer-http/         # Reaction model version 2: client interceptor observing outgoing RestClient requests as actions,
                                     # Spring MVC interceptor observing incoming requests as triggers
jeap-reaction-observer-events/       # Event builders, KafkaEventProducer, ReactionsObservedEventScheduler, config properties
jeap-reaction-observer-starter/      # @AutoConfiguration wiring; main consumer-facing artifact
jeap-reaction-observer-test/         # Test fixtures (ReactionKafkaTestBase, test event builders, consumers)
jeap-reaction-observer-header-starter/ # Standalone JEAP-REACTION-OBSERVATION response-header starter
Jenkinsfile, publiccode.yml, CHANGELOG.md, LICENSE
```

Dependency direction: `core` ← `configuration` ← `messaging` / `events` / `http` ← `starter`. The `http` module
and with it the `configuration` module must not depend on jEAP Messaging, Kafka or Avro (enforced by a Maven
Enforcer rule in `http`). The `http` module depends on the `header-starter` module: a trigger is named by its
`OperationNameResolver`, so that trigger and response header never differ - do not build operation names
anywhere else. The `messaging` module bridges
jEAP Messaging's `JeapKafkaMessageCallback` to the `core` recorder; the `events` module turns
recorded reactions into Avro Kafka events (`reaction-identified-event`, `reactions-observed-event`).
The `header-starter` module is independent: it must not depend on the other modules, jEAP Messaging,
Kafka or Avro (enforced by a Maven Enforcer rule), as it is meant to be added to every jEAP service.

## Build & test

```bash
./mvnw -pl <module> -am install      # build a module and its dependencies
./mvnw verify                        # full build incl. tests
./mvnw -pl jeap-reaction-observer-events test
```

- Parent: `ch.admin.bit.jeap:jeap-internal-spring-boot-parent` (Spring Boot 4 aligned).
- Integration tests use `@EmbeddedKafka`; extend `ReactionKafkaTestBase`
  (`jeap-reaction-observer-test`), which builds on `KafkaIntegrationTestBase` from jEAP Messaging.
- Spring Boot 3 maintenance happens on the `release/springboot3` branch; `master` targets Spring Boot 4.

## jEAP conventions

- Java packages live under `ch.admin.bit.jeap.reaction.observer...`.
- Configuration properties use the prefix `jeap.reaction.observer.*`; event/topic properties use
  `jeap.reaction.observer.events.*`. The master switch is `jeap.reaction.observer.enabled` (default `true`).
- Auto-configuration is registered via `@AutoConfiguration` and
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in each of the
  `messaging`, `events`, `http` and `starter` modules; all are gated on `jeap.reaction.observer.enabled`,
  except `UnobservedRestClientBuilderAutoConfiguration`: a service that uses the factory for clients without
  observation has to start with every configuration of the observer.
- The library publishes its events with jEAP Messaging and therefore requires a working jEAP Messaging
  configuration (`jeap.messaging.kafka.*`, including `systemName`/`serviceName`). The reaction events
  are exempt from message-contract validation, so consuming services do not declare contracts for them.
- Recording is **best-effort**: exceptions in `ReactionObserverService` are caught and logged, never
  propagated. The observer's own events (`ReactionIdentifiedEvent`, `ReactionsObservedEvent`) and
  `MessageProcessingFailedEvent` are filtered out so they are not observed as reactions.
- `ReactionObserverService` notifies the `ReactionIdentifiedListener` on a single thread of its own, never on
  the observing thread: a slow Kafka broker must not delay the observed code. A reaction is marked as
  identified before the notification and unmarked when the notification or its hand-off fails, so it is
  identified again the next time it occurs; only the first of consecutive failures is logged as a warning.
  Unit tests that expect the notification right away pass a direct executor (`Runnable::run`); `close()`
  shuts down only the executor the service created itself.
- Bounded by design: at most `MAX_REACTION_COUNT` (4096) distinct reactions and `MAX_MESSAGING_ACTION_COUNT`
  (100) message actions per trigger are tracked, to cap memory use; HTTP request actions have their own
  budget (`MAX_HTTP_ACTION_COUNT`, 100 per trigger) and coarse actions are named for a limited number of
  targets (`jeap.reaction.observer.http.max-target-count`, 100 by default). The operation names taken from
  response headers form one stock shared by all targets: a called target may contribute a limited number of
  new names (`jeap.reaction.observer.http.max-new-operation-count-per-target`, 100 by default), an accepted
  name may be named by every target, and the stock as a whole is limited (`...max-operation-count`, 1000 by
  default); a name that is not accepted is treated like a header that cannot be used. Whatever comes from
  another service and becomes part of an action name needs such a bound.
- The settings of the library as a whole are in the module `configuration`: `ReactionObserverProperties`
  (`jeap.reaction.observer.model-version`) and, for `jeap.reaction.observer.enabled`, the condition
  `@ConditionalOnReactionObserverEnabled` (a `@ConditionalOnProperty`: every value but `true` switches the
  observer off), which every auto-configuration of the observer carries (with the one exception named
  above). Do not read the switch anywhere else, neither with a `@ConditionalOnProperty` of its own nor from
  the `Environment`.
  `ReactionObserverProperties` is read with `ReactionObserverProperties.of(environment)`, also in
  conditions, where no bean exists yet. It is not registered as a bean: no part of the library needs one.
- The reaction model a service observes by is chosen with `jeap.reaction.observer.model-version`, the number
  of the model version (`1` or `2`, default `1`). The default is defined only by the initial value of the
  field in `ReactionObserverProperties`, a literal, so that it shows in the configuration metadata. The
  property is the single source of the model: `events` (reported model version) and `http` (condition, request header) both read it with
  `ReactionObserverProperties.of(environment).reactionModel()`, which looks the model up by its version
  (`ReactionModel.ofVersion`); a version without a model fails the start. Do not bind the property to the
  enum (Spring converts a number to an enum by the position of the constant, `1` would be `V2`), do not read
  it anywhere else and do not pass the model around as a bean (an application could replace it, and the
  reported version and the observation would disagree). With the default the library must behave towards
  the Reaction Observer Service as before. The names of coarse actions and the id encoding of HTTP requests
  are part of reaction ids - changing them is a migration.

## Docs

When changing public behaviour, update the matching focused file under [docs/](docs/) (one topic per
file) and the documentation index in the README.

## Versioning

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- Always keep the -SNAPSHOT postfix in the POMs, CI will remove it when releasing a version. Do not use the SNAPSHOT
  postfix in other places (CHANGELOG, publiccode.yml etc)
- Keep changelog entries concise and to the point, follow existing patterns
- Keep commit messages short, use the JIRA ID from the branch name as a prefix, do not use conventional commits (for
  example: "JEAP-1234 Added feature X")
- When bumping the version, also update the changelog, and update version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or
  patch version bump should be performed, and update the version accordingly.
