# System behaviour documentation

The Reaction Observer Library is one half of a larger effort to generate **system-behaviour
documentation** automatically. This page gives just enough context to understand where the library
fits.

## The problem

The jEAP ArchRepo already documents runtime components (microservices) and their dependencies,
including interface specifications. What it cannot see from static information alone is *how* a
component reacts when it receives an event — the fine-grained, fast-changing functional behaviour of
components and their interactions. Documenting this by hand tends to be incomplete or outdated. The
Reaction Observer closes that gap by capturing the behaviour at runtime.

## The two products

| Component                       | Role                                                                                                            |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------|
| jEAP Reaction Observer Library  | Runs inside each microservice; observes reactions to consumed messages and publishes them as Kafka events (this repository) |
| jEAP Reaction Observer Service  | A separate jEAP service that consumes those events, records and aggregates the reactions, and exposes them through a REST API |

The data flows: **library (per service) → reaction events on Kafka → Reaction Observer Service →
ArchRepo**, which includes the observed reactions in the generated architecture documentation.

## Preconditions

- An Application Platform runs at least one instance of `jeap-archrepo-service`.
- An instance of the jEAP Reaction Observer Service runs on that platform.
- Each microservice of the business application depends on this Reaction Observer Library (see
  [Getting started](getting-started.md)).

## What the library currently observes

The reactions tracked today are triggered by a consumed message. Their actions are the domain events and
commands produced through jEAP Messaging in response and, with reaction model version 2
(`jeap.reaction.observer.model-version=2`), the outgoing HTTP requests, see
[HTTP requests as actions](http-requests.md). For the recording details see [How it works](how-it-works.md).

## Related

- [Getting started](getting-started.md)
- [How it works](how-it-works.md)
- [Configuration reference](configuration.md)
