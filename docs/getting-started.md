# Getting started

This page shows how to add jEAP Reaction Observer to a Spring Boot service so that the service's
reactions to incoming messages are observed and published. For what the library actually records see
[How it works](how-it-works.md); for the broader context see
[System behaviour documentation](system-behaviour-documentation.md).

## 1. Add the starter

```xml
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-reaction-observer-starter</artifactId>
</dependency>
```

The version is managed by the jEAP Spring Boot parent. The starter pulls in the `messaging` and
`events` modules and registers the Spring Boot auto-configuration. The library is enabled by default.

## 2. Prerequisite: jEAP Messaging

The observer publishes its events through [jEAP Messaging](https://github.com/jeap-admin-ch/jeap-messaging),
so a working jEAP Messaging configuration is required. In particular `jeap.messaging.kafka.systemName`
and `serviceName` must be set, as they are used in the published events. The observer also reuses the
Kafka `JeapKafkaMessageCallback` hooks of jEAP Messaging to detect produced and consumed messages — no
business code changes are needed.

You do **not** need to declare message contracts for the reaction events; they are exempt from
contract validation in jEAP Messaging.

## 3. Grant Kafka topic access

The service needs write access to the two reaction topics of your platform's Reaction Observer Service
instance, for example:

- `applicationplatform-reaction-identified`
- `applicationplatform-reactions-observed`

The `applicationplatform` prefix is specific to your Reaction Observer Service instance — ask the team
operating it for the correct topic names. Configure your Kafka broker to allow this write access.

## 4. Configure the topics

The topic names are the only required observer configuration (see the full
[Configuration reference](configuration.md)):

```yaml
jeap:
  reaction:
    observer:
      enabled: true   # default; set to false e.g. in integration tests
      events:
        reaction-identified-topic: applicationplatform-reaction-identified
        reactions-observed-topic: applicationplatform-reactions-observed
```

If `jeap.reaction.observer.enabled` is `true` (the default) both topic names are mandatory; startup
fails with a clear error otherwise.

## 5. Run your application

Start the service. The library tracks and emits events automatically. When it is active you will see:

```
INFO Starting ReactionsObservedEventScheduler
```

## Related

- [How it works](how-it-works.md)
- [Configuration reference](configuration.md)
- [System behaviour documentation](system-behaviour-documentation.md)
