# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to *use* the library in a
consuming service, read [README.md](README.md) and the [docs/](docs/) folder instead.

## Project

jEAP Reaction Observer is a multi-module Maven library that detects, tracks, and documents how a
microservice reacts to incoming events and commands. It hooks into jEAP Messaging: for each consumed
message (the *trigger*) it records the events/commands the service produces in response (the
*actions*), forms a *reaction*, and publishes that runtime behaviour as Kafka events. The events are
consumed by the jEAP Reaction Observer Service and feed the jEAP ArchRepo system-behaviour
documentation. Recording is best-effort and must never affect business logic.

## Repository layout

```
pom.xml                              # Parent POM (packaging=pom); declares the modules below
jeap-reaction-observer-core/         # Domain model + recording: Observation, Reaction, ReactionRecorder, ReactionObserverService
jeap-reaction-observer-messaging/    # ObserverKafkaMessageCallback: JeapKafkaMessageCallback feeding the recorder
jeap-reaction-observer-events/       # Event builders, KafkaEventProducer, ReactionsObservedEventScheduler, config properties
jeap-reaction-observer-starter/      # @AutoConfiguration wiring; main consumer-facing artifact
jeap-reaction-observer-test/         # Test fixtures (ReactionKafkaTestBase, test event builders, consumers)
Jenkinsfile, publiccode.yml, CHANGELOG.md, LICENSE
```

Dependency direction: `core` ← `messaging` / `events` ← `starter`. The `messaging` module bridges
jEAP Messaging's `JeapKafkaMessageCallback` to the `core` recorder; the `events` module turns
recorded reactions into Avro Kafka events (`reaction-identified-event`, `reactions-observed-event`).

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
  `messaging`, `events` and `starter` modules; all are gated on `jeap.reaction.observer.enabled`.
- The library publishes its events with jEAP Messaging and therefore requires a working jEAP Messaging
  configuration (`jeap.messaging.kafka.*`, including `systemName`/`serviceName`). The reaction events
  are exempt from message-contract validation, so consuming services do not declare contracts for them.
- Recording is **best-effort**: exceptions in `ReactionObserverService` are caught and logged, never
  propagated. The observer's own events (`ReactionIdentifiedEvent`, `ReactionsObservedEvent`) and
  `MessageProcessingFailedEvent` are filtered out so they are not observed as reactions.
- Bounded by design: at most `MAX_REACTION_COUNT` (4096) distinct reactions and `MAX_ACTION_COUNT`
  (100) actions per trigger are tracked, to cap memory use.

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
