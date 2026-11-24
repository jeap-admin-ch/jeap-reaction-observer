# jEAP Reaction Observer Library

jEAP Reaction Observer is a library that detects, tracks, and documents how a microservice reacts to
incoming events and commands. It hooks into [jEAP Messaging](https://github.com/jeap-admin-ch/jeap-messaging)
and records, per consumed message (the *trigger*), which events and commands the service produces in
response (the *actions*). This runtime behaviour is published as Kafka events that feed the jEAP
Reaction Observer Service and, through it, the [jEAP ArchRepo](https://github.com/jeap-admin-ch/jeap-archrepo-service)
system-behaviour documentation. It provides:

* Automatic observation of reactions (produced events/commands) per consumed message, with no
  business-code changes; with reaction model version 2 outgoing HTTP requests are actions, too
* A `Reaction Identified Event` emitted when a new behavioural pattern is first observed after startup
* A `Reactions Observed Event` emitted on a schedule with aggregated reaction counts over a timeframe
* Spring Boot auto-configuration via a single starter; enabled by default, toggled with one property
* Best-effort, low-overhead recording that never interferes with business logic

## Documentation

Start with [Getting started](docs/getting-started.md), then follow the links below.

| Topic                                                       | File                                                       |
|-------------------------------------------------------------|------------------------------------------------------------|
| Getting started (add the starter, configure topics)         | [docs/getting-started.md](docs/getting-started.md)         |
| How it works (triggers, actions, reactions, observed events, reidentification, limits, metrics) | [docs/how-it-works.md](docs/how-it-works.md) |
| Configuration reference (`jeap.reaction.observer.*`)        | [docs/configuration.md](docs/configuration.md)             |
| System behaviour documentation (the bigger picture)         | [docs/system-behaviour-documentation.md](docs/system-behaviour-documentation.md) |
| HTTP requests as actions (reaction model version 2, fine and coarse actions, failed calls, metrics) | [docs/http-requests.md](docs/http-requests.md) |
| HTTP requests as triggers (reaction model version 2, which requests are observed, when a request counts) | [docs/http-triggers.md](docs/http-triggers.md) |
| Response header (`JEAP-REACTION-OBSERVATION`, operation naming and mapping versions, delivered to every service by the jEAP parent) | [docs/response-header.md](docs/response-header.md) |

## Modules

The artifact consuming services depend on is `jeap-reaction-observer-starter`. Group id for all
modules is `ch.admin.bit.jeap`; the version is managed by the jEAP Spring Boot parent.

| Module                            | Purpose                                                                                          |
|-----------------------------------|--------------------------------------------------------------------------------------------------|
| `jeap-reaction-observer-core`     | Domain model and recording logic: `Observation`, `Reaction`, `ReactionRecorder`, `ReactionObserverService` |
| `jeap-reaction-observer-configuration` | Settings of the library as a whole (`jeap.reaction.observer.enabled`, `jeap.reaction.observer.model-version`), shared by the modules below; no dependency on jEAP Messaging |
| `jeap-reaction-observer-messaging` | jEAP Messaging callback (`ObserverKafkaMessageCallback`) that feeds the recorder from Kafka send/consume hooks |
| `jeap-reaction-observer-http`     | Observes outgoing HTTP requests of `RestClient`s as actions and incoming HTTP requests as triggers; no dependency on jEAP Messaging, used through the starter - see [docs/http-requests.md](docs/http-requests.md) and [docs/http-triggers.md](docs/http-triggers.md) |
| `jeap-reaction-observer-events`   | Builds and publishes the `ReactionIdentifiedEvent` / `ReactionsObservedEvent` Kafka events, plus the scheduler |
| `jeap-reaction-observer-starter`  | Spring Boot auto-configuration; the main consumer-facing artifact                                |
| `jeap-reaction-observer-test`     | Test fixtures (`ReactionKafkaTestBase`, test event builders, consumers)                          |
| `jeap-reaction-observer-header-starter` | Sets the `JEAP-REACTION-OBSERVATION` response header; no dependency on jEAP Messaging, Kafka or the other modules, delivered to every service by the jEAP Spring Boot parent - see [docs/response-header.md](docs/response-header.md) |

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
