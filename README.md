# jEAP Reaction Observer Library

jEAP Reaction Observer is a library that detects, tracks, and documents how a microservice reacts to
incoming events and commands. It hooks into [jEAP Messaging](https://github.com/jeap-admin-ch/jeap-messaging)
and records, per consumed message (the *trigger*), which events and commands the service produces in
response (the *actions*). This runtime behaviour is published as Kafka events that feed the jEAP
Reaction Observer Service and, through it, the [jEAP ArchRepo](https://github.com/jeap-admin-ch/jeap-archrepo-service)
system-behaviour documentation. It provides:

* Automatic observation of reactions (produced events/commands) per consumed message, with no
  business-code changes
* A `Reaction Identified Event` emitted when a new behavioural pattern is first observed after startup
* A `Reactions Observed Event` emitted on a schedule with aggregated reaction counts over a timeframe
* Spring Boot auto-configuration via a single starter; enabled by default, toggled with one property
* Best-effort, low-overhead recording that never interferes with business logic

## Documentation

Start with [Getting started](docs/getting-started.md), then follow the links below.

| Topic                                                       | File                                                       |
|-------------------------------------------------------------|------------------------------------------------------------|
| Getting started (add the starter, configure topics)         | [docs/getting-started.md](docs/getting-started.md)         |
| How it works (triggers, actions, reactions, observed events) | [docs/how-it-works.md](docs/how-it-works.md)               |
| Configuration reference (`jeap.reaction.observer.*`)        | [docs/configuration.md](docs/configuration.md)             |
| System behaviour documentation (the bigger picture)         | [docs/system-behaviour-documentation.md](docs/system-behaviour-documentation.md) |

## Modules

The artifact consuming services depend on is `jeap-reaction-observer-starter`. Group id for all
modules is `ch.admin.bit.jeap`; the version is managed by the jEAP Spring Boot parent.

| Module                            | Purpose                                                                                          |
|-----------------------------------|--------------------------------------------------------------------------------------------------|
| `jeap-reaction-observer-core`     | Domain model and recording logic: `Observation`, `Reaction`, `ReactionRecorder`, `ReactionObserverService` |
| `jeap-reaction-observer-messaging` | jEAP Messaging callback (`ObserverKafkaMessageCallback`) that feeds the recorder from Kafka send/consume hooks |
| `jeap-reaction-observer-events`   | Builds and publishes the `ReactionIdentifiedEvent` / `ReactionsObservedEvent` Kafka events, plus the scheduler |
| `jeap-reaction-observer-starter`  | Spring Boot auto-configuration; the main consumer-facing artifact                                |
| `jeap-reaction-observer-test`     | Test fixtures (`ReactionKafkaTestBase`, test event builders, consumers)                          |

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
