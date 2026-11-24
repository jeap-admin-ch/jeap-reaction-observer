# Configuration reference

All Reaction Observer properties use the prefix `jeap.reaction.observer`. The library also requires a
working [jEAP Messaging](https://github.com/jeap-admin-ch/jeap-messaging) configuration
(`jeap.messaging.kafka.*`) to publish its events, including `systemName` and `serviceName`.

## Properties

| Name                                                  | Default | Description                                                                                                                  |
|-------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------|
| `jeap.reaction.observer.enabled`                      | `true`  | When `false` - or any other value but `true` - nothing is observed or published (e.g. in tests): no auto-configuration is activated but the one of the factory for [clients without observation](http-requests.md#clients-without-observation); the other `jeap.reaction.observer.*` settings are then not read. |
| `jeap.reaction.observer.events.reaction-identified-topic` | —   | Kafka topic for `ReactionIdentifiedEvent` messages. Required when the observer is enabled                                    |
| `jeap.reaction.observer.events.reactions-observed-topic`  | —   | Kafka topic for `ReactionsObservedEvent` messages. Required when the observer is enabled                                     |
| `jeap.reaction.observer.events.observed-event-rate-seconds` | `300` | Interval (seconds) at which the counts are published as `ReactionsObservedEvent` messages. At least `1`: a smaller value fails the start of the service, the publication of the counts cannot be switched off. Intervals of seconds only make sense in tests and demos: in operation, choose minutes |
| `jeap.reaction.observer.model-version` | `1` | The version of the reaction model the service observes by: `1` or `2`. Version `1` observes the events and commands a service sends. Version `2` adds its outgoing HTTP requests as actions and its incoming HTTP requests as triggers, and the reaction events report model version 2; it requires a Reaction Observer Service of version 12.26.0 or later, which stores the long ids of reactions with such triggers. A version that does not exist fails the start of the service (when the Reaction Observer is enabled). See [HTTP requests as actions](http-requests.md) and [HTTP requests as triggers](http-triggers.md) |
| `jeap.reaction.observer.reidentification-period` | `24h` | How long an instance remembers which reactions it has identified. After that it identifies each reaction again the next time it occurs, and its limit of 4096 identified reactions starts anew. The first period of an instance lasts a random 50 to 100 percent of this time. A number without a unit is in seconds. A period of less than 2 seconds or of more than 7 days fails the start of the service (when the Reaction Observer is enabled); the reidentification cannot be switched off. Periods of seconds or minutes only make sense in tests and demos: in operation, choose a period of one or several days. See [How it works](how-it-works.md#reidentification) |
| `jeap.reaction.observer.http.target-host.include-port` | `false` | Whether the port belongs to the host in the name of a coarse HTTP request action. Switch it on if the service calls several targets on the same host that differ in the port only. See [HTTP requests as actions](http-requests.md) |
| `jeap.reaction.observer.http.max-target-count` | `100` | The number of different targets an instance names coarse HTTP request actions for. `0` names no coarse actions at all, without a log entry (the loss metric counts these requests as `target_limit`); a negative value fails the start of the service. See [HTTP requests as actions](http-requests.md#limits-of-the-observation) |
| `jeap.reaction.observer.http.max-new-operation-count-per-target` | `100` | The number of new operation names one target (host and, where recognised, context) may contribute to the operation names the instance accepts. A name that is already accepted may be named by every target and does not count. A request whose response names a further new operation is observed like one whose operation is not named (the diagnosis metric counts it as `operation_limit`). `0` accepts no operation names at all, without a warning about the limit; a negative value fails the start of the service. See [HTTP requests as actions](http-requests.md#limits-of-the-observation) |
| `jeap.reaction.observer.http.max-operation-count` | `1000` | The number of different operation names an instance accepts in total, whichever targets name them. A request whose response names a further operation is observed like one whose operation is not named (the diagnosis metric counts it as `operation_limit`). `0` accepts no operation names at all, without a warning about the limit; a negative value fails the start of the service. See [HTTP requests as actions](http-requests.md#limits-of-the-observation) |
| `jeap.reaction.observer.http.log-interval` | `1m` | How often the observation of HTTP requests writes each of its recurring log entries (failed call, target over the limit, operation name over the limit, failure of the observation of an outgoing or an incoming request): at most once per interval. A number without a unit is in seconds, `0` logs every occurrence |
| `jeap.reaction.observer.header.enabled` | `true` | Kill switch for the `JEAP-REACTION-OBSERVATION` response header only (`jeap-reaction-observer-header-starter`). Independent of `jeap.reaction.observer.enabled`. See [Response header](response-header.md) |

When `jeap.reaction.observer.enabled` is `true`, both topic properties must be set or the application
fails to start with an `IllegalArgumentException` naming the missing property.

## Example

```yaml
jeap:
  reaction:
    observer:
      enabled: true
      events:
        reaction-identified-topic: applicationplatform-reaction-identified
        reactions-observed-topic: applicationplatform-reactions-observed
        observed-event-rate-seconds: 300
```

## Disabling in tests

Set `jeap.reaction.observer.enabled: false` to avoid configuring topics in integration tests that do
not exercise the observer. The condition is evaluated on all of the library's auto-configuration
classes, so disabling it removes the observer entirely; its other settings are then not read.

## Related

- [Getting started](getting-started.md)
- [How it works](how-it-works.md)
- [HTTP requests as actions](http-requests.md)
- [Response header](response-header.md)
