# How it works

The Reaction Observer records, per consumed message, how the service responds to it. The vocabulary is:

- **Observation** — a single message seen on Kafka, described by its `type` (`EVENT` or `COMMAND`),
  its fully-qualified name (message type, plus the variant if any) and a small set of properties
  (currently the `topic`). Each observation has a deterministic id derived from these values.
  With reaction model version 2 an outgoing HTTP request is an observation, too (`HTTP_REQUEST`), see
  [HTTP requests as actions](http-requests.md).
- **Trigger** — the observation of a *consumed* message that begins a reaction, or with reaction model
  version 2 of an incoming HTTP request, see [HTTP requests as triggers](http-triggers.md).
- **Action** — the observation of a message *produced* while handling a trigger, or with reaction model
  version 2 of an outgoing HTTP request.
- **Reaction** — a trigger together with the (de-duplicated, ordered) set of actions produced in
  response. A reaction has a stable `id` built from the trigger and action ids, so the same
  behavioural pattern always yields the same id.

## Recording flow

The observer plugs into jEAP Messaging through `ObserverKafkaMessageCallback`, a
`JeapKafkaMessageCallback`. Recording is per-thread (`ThreadLocal`) so concurrent consumers do not
interfere:

```mermaid
sequenceDiagram
    participant K as jEAP Messaging
    participant CB as ObserverKafkaMessageCallback
    participant R as ReactionRecorder
    participant S as ReactionObserverService
    K->>CB: beforeConsume(trigger message)
    CB->>R: onTriggerStart(trigger)
    K->>CB: onSend(produced message)
    CB->>R: onAction(action)
    K->>CB: afterConsume(message)
    CB->>R: onTriggerHandled()
    R->>S: reactionObserved(reaction)
    K->>CB: afterRecord(message)
    CB->>R: afterTrigger()
```

A message produced *outside* any trigger (e.g. by a scheduler) is recorded as an **action-only**
reaction. A trigger with no resulting actions is a **trigger-only** reaction.

The observer's own events (`ReactionIdentifiedEvent`, `ReactionsObservedEvent`) and
`MessageProcessingFailedEvent` are filtered out, so they are never observed as reactions.

## What gets published

`ReactionObserverService` does two things with each observed reaction:

1. **Identify** — the first time a reaction id is seen (after service-instance startup), a
   `ReactionIdentifiedEvent` is published to the `reaction-identified-topic`, carrying the full
   trigger/action structure of the new pattern. A single thread of the observer publishes these events
   one after the other, so a slow or unreachable Kafka broker does not delay the observed code. If the
   publication fails, the reaction is identified again the next time it occurs.
2. **Count** — every observed reaction increments an in-memory counter keyed by reaction id.

On a schedule, `ReactionsObservedEventScheduler` drains the counters and publishes a single
`ReactionsObservedEvent` to the `reactions-observed-topic` with the per-reaction counts for the
elapsed timeframe (the interval is `observed-event-rate-seconds`, default 300s). If no reactions were
observed in a timeframe, no event is sent. A final event is also published on graceful shutdown.

## Best-effort and bounded by design

- Exceptions during recording are caught and logged, never propagated to business logic.
- The observed code never waits for the publication of a `ReactionIdentifiedEvent` (see above). On
  graceful shutdown, pending identifications get a few seconds to complete; an abandoned one is
  identified by a later run of the service instance when the reaction occurs there.
- As a consequence, a `ReactionsObservedEvent` can count a reaction whose identification is still
  pending or has failed, most likely the final event on shutdown or while the broker is slow. The
  Reaction Observer Service skips such counts.
- At most `4096` distinct reactions and `100` message actions per trigger are tracked; once a limit is
  reached a one-time warning is logged and further items are ignored, capping memory use. HTTP request
  actions have a budget of their own of `100` per trigger.

## Related

- [Getting started](getting-started.md)
- [HTTP requests as actions](http-requests.md)
- [HTTP requests as triggers](http-triggers.md)
- [Configuration reference](configuration.md)
- [System behaviour documentation](system-behaviour-documentation.md)
