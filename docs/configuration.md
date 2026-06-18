# Configuration reference

All Reaction Observer properties use the prefix `jeap.reaction.observer`. The library also requires a
working [jEAP Messaging](https://github.com/jeap-admin-ch/jeap-messaging) configuration
(`jeap.messaging.kafka.*`) to publish its events, including `systemName` and `serviceName`.

## Properties

| Name                                                  | Default | Description                                                                                                                  |
|-------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------|
| `jeap.reaction.observer.enabled`                      | `true`  | Master switch. When `false`, no auto-configuration is activated and nothing is observed or published (e.g. in tests)         |
| `jeap.reaction.observer.events.reaction-identified-topic` | —   | Kafka topic for `ReactionIdentifiedEvent` messages. Required when the observer is enabled                                    |
| `jeap.reaction.observer.events.reactions-observed-topic`  | —   | Kafka topic for `ReactionsObservedEvent` messages. Required when the observer is enabled                                     |
| `jeap.reaction.observer.events.observed-event-rate-seconds` | `300` | Interval (seconds) at which aggregated `ReactionsObservedEvent` messages are published. A value `<= 0` disables the scheduler |

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
classes, so disabling it removes the observer entirely.

## Related

- [Getting started](getting-started.md)
- [How it works](how-it-works.md)
