# jEAP Reaction Observer Library

jEAP Reaction Observer is a library that detects, tracks, and documents how microservices respond to incoming events
such as messages, API requests, or scheduled triggers.

It enables automated observation of runtime behaviour, capturing both the type and frequency of reactions across
distributed systems.

The library emits Reaction Identified Events when a new behavioural pattern is first observed, and Reactions Observed
Events to provide aggregated statistics over time.

This data forms the foundation for system behaviour documentation, helping architects and developers understand service
dynamics without relying on manual documentation.

By integrating with the jEAP ArchRepo, the Reaction Observer enriches the generated documentation with insightful
runtime behaviour patterns.

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP.
See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
