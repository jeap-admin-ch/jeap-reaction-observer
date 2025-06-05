# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-06-04

### Changed
- Update parent from 5.8.1 to 5.9.0
- update jeap-messaging.version from 8.37.0 to 8.39.0
- SequentialInbox: Prevent parallel execution of housekeeping methods using SchedulerLock. Ensure the shedlock table exists if ShedLock is not already configured.

## [1.0.0] - 2025-05-28

### Changed

- Release 1.0.0 (no changes)

## [0.3.0] - 2025-05-26

### Changed

- Update parent from 5.8.0 to 5.8.1

## [0.2.5] - 2025-05-23

### Changed

- Verify that system name is set so that reaction events can be published

## [0.2.4] - 2025-05-23

### Changed

- Fixed service name in ReactionsObservedEvent

## [0.2.3] - 2025-05-23

### Changed

- Optimized ReactionsObservedEvent idempotence ID generation

## [0.2.2] - 2025-05-23

### Changed

- Filter MessageProcessingFailedEvents when recording reactions

## [0.2.1] - 2025-05-14

### Added

- Submit topic name as a property of identified messaging reactions

## [0.2.0] - 2025-04-30

### Added

- Observe reactions for events and commands consumed from / produced to kafka

## [0.1.0] - 2025-04-30

### Changed

- Update parent from 5.7.1 to 5.8.0

## [0.0.2] - 2025-04-23

### Added

- Reaction identified event producer

## [0.0.1] - 2025-04-03

### Added

- Initial library skeleton
