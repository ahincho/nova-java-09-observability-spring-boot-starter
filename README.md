# Nova Observability Spring Boot Starter

The Four Golden Signals wired into a Spring Boot application by adding a
dependency. Latency, traffic, errors and saturation are recorded for every
request, traces are exported over OTLP, and log lines carry the trace id.

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
| `OtlpExporterAutoConfiguration` | OTLP export to a collector |
| `LogCorrelationAutoConfiguration` | Trace and span id in the MDC |
| `CollectorHealthIndicator` | Actuator health for the collector endpoint |
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
    implementation("pe.edu.nova.java.starters:nova-observability-starter:0.1.0-SNAPSHOT")
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

Point the OTLP exporter at your collector and the traces follow.

## Use

Nothing to call for HTTP traffic — the filter covers it. For work that
happens outside a request, annotate it:

```java
@Traced
@Metered
public Invoice settle(Order order) { ... }
```

## Requirements

Java 25, Spring Boot 4, an OpenTelemetry collector for traces.

## License

Eclipse Public License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
