# Response header: `JEAP-REACTION-OBSERVATION`

Every jEAP service answers a request carrying a `JEAP-REACTION-MODEL-VERSION` request header with a
`JEAP-REACTION-OBSERVATION` response header naming the operation it was called on. A consumer takes that
name unchanged as its action, and the jEAP Reaction Observer Service links it to the provider's trigger.
This lets consumer and provider agree on one name for a REST operation without either side guessing the
other's routing.

This is deliberately **not** gated on the rest of the Reaction Observer library: the header is set by a
separate, minimal module, `jeap-reaction-observer-header-starter`, so that every jEAP service delivers it,
even services that do not otherwise use this library (mode "header only").

## Contract

| | |
|---|---|
| Request signal | `JEAP-REACTION-MODEL-VERSION`: an integer, the consumer's reaction model version. The provider only checks that the header is present, not its value |
| Response header | `JEAP-REACTION-OBSERVATION`, present only when the request carried the signal |
| Value format | RFC 9651 Structured Field: a List with one Display String |

This contract (request signal, value format, operation-naming rule below) is **frozen** from the first
released version: providers running an old header module stay in service for a long time, so the wire
format cannot change compatibly afterwards.

### Value format

```
JEAP-REACTION-OBSERVATION: %"partner-service GET /api/partners/{}"
```

A [Display String](https://www.rfc-editor.org/rfc/rfc9651.html#name-displaying-a-string) - `%"..."` -
as the single member of a Structured Field List. Encoding rule: `%`, `"`, every non-printable ASCII
character and every non-ASCII byte are percent-encoded as their raw UTF-8 bytes, in lowercase hex; every
other visible ASCII character (0x20-0x7E) is kept as-is. `ReactionObservationEncoder` in this module is
the canonical serializer; the parser lives in the Reaction Observer HTTP module.

A Structured Field was chosen over plain text with ad-hoc percent-encoding because the contract is frozen
from the first release: a standard encoding and correct behaviour when several field lines get merged
(see [Known limits](#known-limits)) weigh more than `curl` readability.

**Shared test cases** (header module and the HTTP module's parser agree on all of these):

| Input | Encoded value |
|---|---|
| `partner-service GET /api/partners/{}` | `%"partner-service GET /api/partners/{}"` |
| `svc GET /api/100%` | `%"svc GET /api/100%25"` |
| `svc GET /api/"quoted"` | `%"svc GET /api/%22quoted%22"` |
| `svc GET /api/a,b` (comma) | `%"svc GET /api/a,b"` - kept literal, safe inside the quoted string |
| `svc GET /api/{*}` | `%"svc GET /api/{*}"` |
| `svc GET /api/x ;v=2` | `%"svc GET /api/x ;v=2"` |
| `svc GET /api/café` (non-ASCII) | `%"svc GET /api/caf%c3%a9"` |
| `test-app GET /` + `x` repeated 499 times (513 characters) | `%"test-app GET /` + `x` repeated 433 times + `~24a4cf8ed5134d5a7a78c86193c1390e24d4468f664c56b40b3c8c38c3e3e8d6"` (512 characters) |

## Operation name (FQN)

```
fqn = "<provider> <METHOD> <pattern>[ ;v=<apiVersion>]"
```

| Part | Rule |
|---|---|
| `<provider>` | `jeap.rest.tracing.application-name` (relaxed binding, so it matches `JEAP-APPLICATION-NAME`), falling back to `spring.application.name`. Blank or unresolvable values (e.g. `${spring.application.name}` without an application name) count as not set. If neither is set, a warning is logged at startup and no operation name is produced; startup never fails |
| `<METHOD>` | HTTP method, uppercase |
| `<pattern>` | `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, relative to the context path and the `DispatcherServlet` mapping |
| Normalisation | `{name}` and `{name:regex}` → `{}`; `{*name}` → `{*}`; a trailing `/` is dropped, except for `/` itself |
| `;v=<apiVersion>` | The declared `@RequestMapping(version = ...)` value (including composed annotations such as `@GetMapping` and inherited/interface mappings). A non-empty method version overrides the class version. The value is preserved verbatim, including `+` for baselines; omitted for unversioned mappings and all functional WebMvc.fn endpoints |
| Length | FQNs over 512 characters are cut to `512 - 65` characters and suffixed with `~` + the lowercase hex SHA-256 of the *untruncated* FQN (65 characters), for a stable result of at most 512 characters. The limit counts UTF-16 characters of the unencoded FQN; the cut never splits a surrogate pair, and the encoded header value can be longer for non-ASCII names |
| Not part of the FQN | host, context path, query string, status code, path-variable names, client-requested API version |

For example, requests for versions `1.3` and `1.5` matching a mapping declared with `version = "1.2+"`
both produce `;v=1.2+`. A mapping declared with `version = "1"` produces `;v=1`, regardless of the
API version parser's representation. `HandlerMapping.API_VERSION_ATTRIBUTE` is deliberately not used:
it contains the parsed client-requested version, not the mapping version. Sending an API version to an
unversioned mapping does not add a suffix. Functional endpoints remain observable, but always without
a version suffix, even when their route uses API version predicates.

This normalisation is close to, but **not identical with**, `RelationPaths.normalised` (jEAP ArchRepo /
Doc-Service): that rule collapses every path variable, including a `{*name}` catch-all, into `{}`. This
module keeps `{*name}` as `{*}` because that is what Spring itself reports for a wildcard capture, and
collapsing it would make two different operations look the same. 

`OperationNameResolver` is the single place this naming rule and the exclusions below live, so the
Reaction Observer core library's own trigger-capturing interceptor can reuse it unchanged
instead of re-implementing it. There is no SPI between the two: both are released from this repository in
lockstep, so this class is not part of the frozen contract.

### What is observed

Only controller methods (`HandlerMethod`) and functional endpoints (`HandlerFunction`, WebMvc.fn) are
observed. Everything else - most notably static resources - is not. Among controller methods, these are
fixed, non-configurable exclusions:

- the error page (`BasicErrorController`)
- springdoc/Swagger (any handler in a package starting with `org.springdoc.`)
- Actuator handlers in packages starting with `org.springframework.boot.webmvc.actuate.` (Boot 4);
  the legacy prefix `org.springframework.boot.actuate.` is also excluded

Standard Spring Boot 4 Actuator endpoints are outside the interceptor's handler mapping:
`WebMvcEndpointHandlerMapping` does not receive interceptors registered through `InterceptorRegistry`.
They therefore return no observation header without invoking `OperationNameResolver`. Their handlers
live under `org.springframework.boot.webmvc.actuate.endpoint.web` and are additionally excluded by the
resolver's Boot 4 package prefix. This protects callers that also reach the Actuator mapping, for example
a trigger-capturing interceptor registered as a `MappedInterceptor` bean: every `AbstractHandlerMapping`,
including `WebMvcEndpointHandlerMapping`, picks up such beans. Integration tests verify both the mapping
boundary and the resolver exclusion using the real Boot health and links handlers obtained from
`WebMvcEndpointHandlerMapping`.

## Configuration

| Property | Default | Description |
|---|---|---|
| `jeap.reaction.observer.header.enabled` | `true` | Emergency kill switch for the response header interceptor and protection filter only. The `OperationNameResolver` remains available for trigger capture. **Independent** of the library's global `jeap.reaction.observer.enabled`: a service that disables its own reaction recording should still stay visible to its callers. Meant for an emergency in the request path, not for normal operation |

The module is only active in a servlet web application with Spring MVC on the classpath
(`@ConditionalOnClass(DispatcherServlet.class)`, `@ConditionalOnWebApplication(type = SERVLET)`); in any
other service it contributes nothing. Its Spring Web MVC and Servlet API dependencies are `optional`, so
it never puts them on the classpath of a non-web service (which would make Spring Boot start it as a
servlet application).

## How it is wired in

The module has no dependency on jEAP Messaging, Kafka, Avro or the other `jeap-reaction-observer`
modules (enforced by a Maven Enforcer `bannedDependencies` rule) - only on Spring Boot autoconfigure, plus
optional Spring Web MVC / Servlet API dependencies supplied by the service itself. The `jeap-spring-boot-parent`'s default `spring` profile adds it to every service next to
`jeap-spring-boot-application-starter`, so a service needs no dependency of its own to get the header.
Services that do not inherit from `jeap-spring-boot-parent` can add the Maven dependency directly:

```xml
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-reaction-observer-header-starter</artifactId>
</dependency>
```

A project that pins `jeap-reaction-observer.version` to a version before this module was introduced will
not find the artifact and its build will break; see this library's [CHANGELOG](../CHANGELOG.md) for the
first version that includes it.

## Known limits

| Situation | Behaviour |
|---|---|
| `@ControllerAdvice`, `ResponseStatusException` | Header present - the header is set in `preHandle`, before the handler runs |
| Unhandled exception, container error dispatch to `/error` | Header and `Vary` present - re-applied from a request attribute if the container reset response headers before the error dispatch |
| 401 from a security filter, 404 with no matching handler, 503 from an ingress/proxy | **No header** - `preHandle` never runs because no handler was ever resolved. The consumer counts an unidentified action |
| A BFF/aggregator copying a downstream response's headers onto its own | A copied downstream `JEAP-REACTION-OBSERVATION` value is always dropped (see `ProtectedObservationHeaderResponse`), so it can neither overwrite the service's own value nor reach the caller when the service sets none itself; this also holds when later filters such as Spring Security wrap the response again |
| Application interceptors returning `false` from `preHandle` | Header and `Vary` present - this module's interceptor is registered with the highest precedence and runs first |
| Cacheable responses (without `no-store`) | `Vary: JEAP-REACTION-MODEL-VERSION` is added for every observed operation, merged with any existing `Vary` value, whether or not that particular request carried the signal - the response depends on the signal either way. Authenticated jEAP APIs already set `no-store` via Spring Security, so shared caching rarely applies |
| `Callable`, `DeferredResult`, SSE | Header present - set on the initial `REQUEST` dispatch, before async processing starts; the trigger itself is not captured by this module |
| Calls made in filters running before the `DispatcherServlet` | Outside this module's reach entirely |

## Related

- [Configuration reference](configuration.md)
- [How it works](how-it-works.md)
