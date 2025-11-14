# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.0] - 2025-11-14
### Changed
- update jeap-messaging from 10.0.1 to 10.1.0
- update jeap-crypto from 4.3.0 to 4.4.0
- update jeap-spring-boot-vault-starter from 18.3.0 to 18.4.0
- Update aws-advanced-jdbc-wrapper from 2.5.4 to 2.6.6


## [3.0.1] - 2025-11-14
### Changed
- update jeap-messaging from 10.0.0 to 10.0.1
- Fix parsing of boolean decryption properties in JeapGlueAvroDeserializer


## [3.0.0] - 2025-11-13
### Changed
- update jeap-messaging from 9.4.0 to 10.0.0
- Support Nifi-compatible decryption of messages in the Glue deserializer
- Remove legacy encryption in serializers
- Remove CustomKafkaAvroSerializerConfig as it no longer contains any custom properties. Use KafkaAvroSerializerConfig
  for confluent schema registry configuration directly if necessary.


## [2.4.0] - 2025-11-12

### Changed
- Update parent from 5.15.0 to 5.15.1
  
- update jeap-crypto from 4.2.0 to 4.3.0
- update jeap-spring-boot-vault-starter from 18.2.0 to 18.3.0
- update jeap-messaging from 9.3.1 to 9.4.0
- update jeap-spring-boot-roles-anywhere-starter from 1.5.0 to 1.6.0

## [2.3.1] - 2025-10-30
### Changed
- update jeap-messaging from 9.3.0 to 9.3.1
- Fixed NullpointerException when signatureMetricsService not set 


## [2.3.0] - 2025-10-02

### Changed
- Update parent from 5.14.0 to 5.15.0
- update jeap-messaging from 9.2.0 to 9.3.0
- update commons-io from 2.19.0 to 2.20.0

## [2.2.0] - 2025-09-26
### Changed
- update jeap-messaging from 9.1.1 to 9.2.0
- allow non jEAP messages, when property jeap.messaging.authentication.subscriber.allowNonJeapMessages=true is set


## [2.1.1] - 2025-09-26
### Changed
- update jeap-messaging from 9.1.0 to 9.1.1
- Instantiating the signature verifier when require-signature set to false
- Check signature if certificate is available, and headers are set
- No exception is thrown when signatureRequired is set to false and certificate is not available on the consumer side


## [2.1.0] - 2025-09-19

### Changed
- Update parent from 5.13.0 to 5.14.0
- update jeap-spring-boot-vault-starter from 18.0.0 to 18.1.0
- update jeap-spring-boot-roles-anywhere-starter from 1.3.0 to 1.4.0
  
- update jeap-messaging from 9.0.2 to 9.1.0
- update jeap-crypto from 4.0.0 to 4.1.0

## [2.0.2] - 2025-09-11
### Changed
- update jeap-messaging from 9.0.1 to 9.0.2
- Send headers to error service sender also in the case of failed deserialization


## [2.0.1] - 2025-09-03
### Changed
- update jeap-messaging from 9.0.0 to 9.0.1
- Ignoring the bootstrap.properties and bootstrap.yaml files when looking for an application name in the
  message contract annotation processor, as such configurations are no longer supported by jEAP.


## [2.0.0] - 2025-09-02
### Changed
- update jeap-messaging from 8.57.1 to 9.0.0
- update jeap-crypto from 3.28.0 to 4.0.0
- update jeap-spring-boot-vault-starter from 17.43.0 to 18.0.0
- Support for the Spring Cloud bootstrap context mechanism has been removed. Use the spring.config.import mechanism
  instead for your (external) microservice configuration. 


## [1.19.0] - 2025-09-02
### Changed
- update jeap-messaging from 8.56.1 to 8.57.1
- The GenericRecordDataDeserializer is now only available without signature check, which is now also removed from props


## [1.18.1] - 2025-08-29
### Changed
- update jeap-messaging from 8.56.0 to 8.56.1
- The GenericRecordDataDeserializer is now only available without signature check
- Better logging when signature verification fails


## [1.18.0] - 2025-08-27

### Changed
- Read variant from incoming messages and include it as part of the FQN of event/command observations
 
## [1.17.0] - 2025-08-26

### Changed
- Update parent from 5.12.1 to 5.13.0
  
- update jeap-crypto from 3.26.0 to 3.27.0
- update jeap-spring-boot-vault-starter from 17.41.0 to 17.42.0
- update jeap-messaging from 8.55.0 to 8.56.0
- update jeap-spring-boot-roles-anywhere-starter from 1.2.0 to 1.3.0

## [1.16.0] - 2025-08-22
### Changed
- update jeap-messaging from 8.54.0 to 8.55.0
- Added a new optional field 'variant' to interface MessageType
- Updated Domain Event and Command version to 1.3.0 to include the new 'variant' field


## [1.15.0] - 2025-08-14

### Changed
- Update parent from 5.12.0 to 5.12.1
- update jeap-spring-boot-vault-starter from 17.40.1 to 17.41.0
- update jeap-spring-boot-roles-anywhere-starter from 1.1.1 to 1.2.0
  
- update jeap-messaging from 8.53.1 to 8.54.0
- update jeap-crypto from 3.25.1 to 3.26.0

## [1.14.1] - 2025-08-08
### Changed
- update jeap-messaging from 8.53.0 to 8.53.1
- update jeap-crypto from 3.25.0 to 3.25.1
- update jeap-spring-boot-vault-starter from 17.40.0 to 17.40.1
- Make feature-policy header configurable in jeap-spring-boot-web-config-starter


## [1.14.0] - 2025-08-05

### Changed
- Update parent from 5.11.0 to 5.12.0
- updated springdoc-openapi from 2.8.6 to 2.8.9
- update jeap-spring-boot-roles-anywhere-starter from 1.0.0 to 1.1.1
- update commons-compress from 1.27.1 to 1.28.0
- update jeap-crypto from 3.24.3 to 3.25.0
- update jeap-spring-boot-vault-starter from 17.39.3 to 17.40.0
- update jeap-messaging from 8.52.0 to 8.53.0
- updated logstash from 8.0 to 8.1
  
- updated wiremock from 3.12.1 to 3.13.1

## [1.13.0] - 2025-07-24
### Changed
- update jeap-messaging from 8.51.3 to 8.52.0
- Added jeap-spring-boot-roles-anywhere-starter support for aws msk


## [1.12.3] - 2025-07-09
### Changed
- update jeap-messaging from 8.51.2 to 8.51.3
- update jeap-crypto from 3.24.2 to 3.24.3
- update jeap-spring-boot-vault-starter from 17.39.2 to 17.39.3
- switch from deprecated org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration to org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration


## [1.12.2] - 2025-07-09
### Changed
- update jeap-messaging from 8.51.1 to 8.51.2
- update jeap-crypto from 3.24.1 to 3.24.2
- update jeap-spring-boot-vault-starter from 17.39.1 to 17.39.2
- ServletRequestSecurityTracer now properly handles non-REST requests (e.g., SOAP) by falling back to the request URI when the REST HandlerMapping pattern is not available.


## [1.12.1] - 2025-07-07
### Changed
- update jeap-messaging from 8.51.0 to 8.51.1
- update jeap-crypto from 3.24.0 to 3.24.1
- update jeap-spring-boot-vault-starter from 17.39.0 to 17.39.1
- Make sure JeapPostgreSQLAWSDataSourceAutoConfig is evaluated before Spring's DataSourceAutoConfiguration to avoid
  DataSource bean conflicts.


## [1.12.0] - 2025-07-04

### Changed
- Update parent from 5.10.2 to 5.11.0
- update jeap-spring-boot-vault-starter from 17.38.0 to 17.39.0
- update protobuf-java from 4.30.2 to 4.31.1
- update maven.api from 3.9.9 to 3.9.10
- update testcontainers from 1.21.0 to 1.21.3
- update jeap-crypto from 3.23.0 to 3.24.0
- update org.eclipse.jgit from 7.2.0.202503040940-r to 7.3.0.202506031305-r
- update jeap-messaging from 8.49.1 to 8.51.0
- update guava-testlib from 31.1-jre to 33.4.8-jre
- update schema-registry-serde from 1.1.23 to 1.1.24
- update avro-serializer from 7.9.0 to 7.9.2

## [1.11.1] - 2025-06-30
### Changed
- update jeap-messaging from 8.49.0 to 8.49.1
- The logging in the MessageTypeRegistryVerifierMojo now respects the Maven logging configuration.


## [1.11.0] - 2025-06-30
### Changed
- update jeap-messaging from 8.47.1 to 8.49.0
- Support for privileged producer in message signature validation (for mirrormaker)


## [1.10.1] - 2025-06-19
### Changed
- update jeap-messaging.version from 8.47.0 to 8.47.1
- Fix bug in message signing verifier, where certificate common and service name were twisted 


## [1.10.0] - 2025-06-18

### Changed
- Update parent from 5.10.1 to 5.10.2
- update jeap-spring-boot-vault-starter.version from 17.37.0 to 17.38.0
- update jeap-messaging.version from 8.46.0 to 8.47.0
- update jeap-crypto.version from 3.22.1 to 3.23.0

## [1.9.0] - 2025-06-18
### Changed
- update jeap-messaging.version from 8.45.0 to 8.46.0
- Overwrite commons-io version (2.11.0) from spring-kafka-test 3.3.6 with 2.19.0 (CVE-2024-47554)
- Overwrite commons-beanutils version (1.9.4) from spring-kafka-test 3.3.6 with 1.11.0 (CVE-2025-48734)


## [1.8.0] - 2025-06-17

### Changed
- Update parent from 5.10.0 to 5.10.1
- Update because to upload (central-publish) didn't work properly
- update jeap-messaging.version from 8.44.0 to 8.45.0
- update jeap-crypto.version from 3.21.0 to 3.22.1

## [1.7.1] - 2025-06-13

### Changed

- Avoid recording duplicate actions
- Limit the amount of recorded reactions to a certain amount for resilience reasons

## [1.7.0] - 2025-06-13

### Changed
- Update parent from 5.9.0 to 5.10.0
- update jeap-spring-boot-vault-starter.version from 17.35.0 to 17.36.0
- update jeap-messaging.version from 8.43.0 to 8.44.0
- update jeap-crypto.version from 3.20.0 to 3.21.0

## [1.6.0] - 2025-06-13
### Changed
- update jeap-messaging.version from 8.42.0 to 8.43.0
Moved jeap-messaging-outbox to its own repository.
Moved jeap-messaging-sequential-inbox to its own repository.


## [1.5.0] - 2025-06-12
### Changed
- update jeap-messaging.version from 8.41.0 to 8.42.0
- update jeap-crypto.version from 3.19.0 to 3.20.0
- update jeap-spring-boot-vault-starter.version from 17.34.0 to 17.35.0
- security-starter-test: removed spring-security-rsa dependency as its functionality is now included in spring-security


## [1.4.0] - 2025-06-12
### Changed
- Reactions now group multiple actions occuring during a single trigger together.

## [1.3.0] - 2025-06-10
### Changed
- update jeap-messaging.version from 8.40.0 to 8.41.0
- The main branch name used by the MessageTypeRegistryVerifier plugin is now configurable. Defaults to 'master'.


## [1.2.0] - 2025-06-05
### Changed
- update jeap-messaging.version from 8.39.0 to 8.40.0
- Update parent from 5.8.1 to 5.9.0
- update jeap-crypto.version from 3.17.0 to 3.18.0
- Update parent from 5.8.1 to 5.9.0
- Project Name now required for uploads to Maven Central
- update jeap-crypto.version from 3.18.0 to 3.19.0
- update jeap-spring-boot-vault-starter.version from 17.32.0 to 17.34.0
- Update parent from 5.8.1 to 5.9.0

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
