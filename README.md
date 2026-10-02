# Nova Observability Spring Boot Starter

The Four Golden Signals wired into a Spring Boot application by adding a
dependency. Latency, traffic, errors and saturation are recorded for every
request, traces are exported over OTLP once a collector is configured, and
log lines carry the trace id.

The contract it implements lives in
[nova-observability-utils](https://github.com/ahincho/nova-java-05-observability-utils),
which has no framework dependency — this module is the Spring half.

## What it configures

| Class | Does |
|---|---|
| `GoldenSignalsFilter` | Records latency, traffic and errors per request |
| `UriNormalizer` | Collapses `/orders/1234` into `/orders/{id}` so cardinality stays bounded |
| `GoldenSignalsMetrics` | The `GoldenSignalsRecorder` implementation, on Micrometer |
| `MetricsAutoConfiguration` | Meter registry and common tags |
| `TracingAutoConfiguration` | Micrometer tracing bridged to OpenTelemetry |
| `NovaObservabilityEnvironmentPostProcessor` | Maps the OTLP settings to OpenTelemetry before its SDK starts, and turns the exporters off when there is no endpoint |
| `LogCorrelationAutoConfiguration` | Trace and span id in the MDC |
| `CollectorHealthIndicator` | Actuator health for the collector endpoint, only when an endpoint is configured |
| `MeteredAspect`, `TracedAspect` | Support for `@Metered` and `@Traced` on any bean |

## Install

Published to GitHub Packages, so the repository needs to be declared and
authenticated with a token that has `read:packages`.

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-09-observability-spring-boot-starter")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("pe.edu.nova.java.starters:nova-observability-spring-boot-starter:3.0.0")
}
```

## Configure

Properties are bound by `ObservabilityProperties` under the `nova`
prefix. The full set is described in
`additional-spring-configuration-metadata.json`, so your IDE will
autocomplete them.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### The collector endpoint

There is no default endpoint, so a service that adds the dependency and
configures nothing does not try to reach a collector. Export is on only
when an endpoint is configured, in either of two ways:

| Setting | What it is |
|---|---|
| `nova.observability.otlp.endpoint` | The starter's own property |
| `otel.exporter.otlp.endpoint` | The standard OpenTelemetry setting, which Spring also reads from the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable |

```yaml
nova:
  observability:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:}
```

If both are set, the OpenTelemetry one wins. A blank value counts as not
configured, which is why the placeholder above can have an empty default.

**With an endpoint**, traces, metrics and logs are exported to it over OTLP,
and `CollectorHealthIndicator` reports the collector in `/actuator/health`.

**Without one**, the service stays quiet:

- The exporters for traces, metrics and logs are turned off
  (`otel.traces.exporter`, `otel.metrics.exporter` and `otel.logs.exporter`
  are `none`), and a single INFO line at startup says so.
- Traces are still generated and metrics are still recorded, so trace ids and
  the Four Golden Signals behave as they do with an endpoint. They are just
  not sent anywhere.
- `CollectorHealthIndicator` is not registered, so `/actuator/health` does
  not report `DOWN` for a collector nobody configured.

The starter never overrides what you set. An exporter you choose yourself,
such as `otel.traces.exporter=otlp`, is left as you wrote it. Endpoints for a
single signal, such as `otel.exporter.otlp.traces.endpoint`, do not count as
the endpoint on their own: set the generic one, or choose that signal's
exporter yourself.

`nova.observability.enabled=false` switches this starter off, the
post-processor included. It does not switch off the OpenTelemetry Spring Boot
starter this one depends on, which then starts with its own defaults (an OTLP
exporter aimed at `http://localhost:4318`). To turn the SDK off as well, set
`otel.sdk.disabled=true`.

## Migrating to 3.0.0

Up to 2.x, a service that configured nothing exported to
`http://localhost:4318`. From 3.0.0 it does not: without an endpoint nothing
is exported, as described above. What to do depends on the service:

- **It already sets `nova.observability.otlp.endpoint`** (or
  `OTEL_EXPORTER_OTLP_ENDPOINT`): nothing changes.
- **It relied on the implicit `http://localhost:4318`**: set
  `nova.observability.otlp.endpoint`, or the `OTEL_EXPORTER_OTLP_ENDPOINT`
  environment variable, to the collector's address. Until then nothing is
  exported and `/actuator/health` has no `collector` component.
- **It writes `${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}`**: it
  keeps working as before, because the fallback is an endpoint the service
  configured. To stay quiet when the variable is missing, drop the fallback:
  `${OTEL_EXPORTER_OTLP_ENDPOINT:}`.

Two API changes come with it:

- `CollectorHealthIndicator` takes the endpoint in its constructor instead of
  `ObservabilityProperties`, and it is registered only when an endpoint is
  configured.
- `OtlpExporterAutoConfiguration` is gone. Its work moved to
  `NovaObservabilityEnvironmentPostProcessor`, registered in
  `META-INF/spring.factories`, so the `otel.*` properties are in the
  environment before the OpenTelemetry SDK is created.

## Use

Nothing to call for HTTP traffic — the filter covers it. For work that
happens outside a request, annotate it:

```java
@Traced
@Metered
public Invoice settle(Order order) { ... }
```

## Requirements

Java 25, Spring Boot 4. An OpenTelemetry collector is only needed to export:
without an endpoint the starter runs fine and exports nothing.

## License

Eclipse Public License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
