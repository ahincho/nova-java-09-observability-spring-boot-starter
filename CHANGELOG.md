# Changelog

## [3.0.1](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v3.0.0...v3.0.1) (2026-10-02)


### Bug Fixes

* report the collector as down when its failure has no message ([c70719f](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/c70719f0e67a8257629ecd996de65d8d513c1df1))

## [3.0.0](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v2.0.2...v3.0.0) (2026-10-02)


### ⚠ BREAKING CHANGES

* nova.observability.otlp.endpoint no longer defaults to http://localhost:4318. A service that relied on it must set nova.observability.otlp.endpoint or OTEL_EXPORTER_OTLP_ENDPOINT, or nothing is exported. CollectorHealthIndicator is registered only with an endpoint and takes it in its constructor instead of ObservabilityProperties, and OtlpExporterAutoConfiguration is removed.

### Features

* export OTLP only when an endpoint is configured ([b4adf87](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/b4adf87814cd649f26d85a6724629669cc6e36b6))


### Documentation

* document the OTLP endpoint rule and the 3.0.0 migration ([e61f1f0](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/e61f1f03ca9a660490c59fc9d36a8a0d64ab203d))

## [2.0.2](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v2.0.1...v2.0.2) (2026-09-30)


### Bug Fixes

* **deps:** build on Spring Boot 4.0.8 like the rest of the platform ([c6a6832](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/c6a683239918ccacf06e5896f82d37af2d33cea1))

## [2.0.1](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v2.0.0...v2.0.1) (2026-09-27)


### Bug Fixes

* **deps:** let OpenTelemetry instrumentation 2.31.1 bring semconv ([d102d6a](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/d102d6a565b27f734344a52ffab93f129570e577))
* **deps:** let OpenTelemetry instrumentation 2.31.1 bring semconv ([6168ad1](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/6168ad1e4d24ad2af10e92e0ff9ab80c62c94154))

## [2.0.0](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v1.0.2...v2.0.0) (2026-09-27)


### ⚠ BREAKING CHANGES

* pe.edu.nova.java.starters:nova-observability-starter becomes nova-observability-spring-boot-starter; consumers migrate with ops/rename-artifacts.py --phase 2 from nova-shared-01-docs.

### Features

* publish the starter as nova-observability-spring-boot-starter ([5b2bdee](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/5b2bdeef5f955fb6d0075224964ba549b78974fb))


### Bug Fixes

* **deps:** move to Spring Boot 4.1.1 and Tomcat 11.0.26 ([8f045d6](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/8f045d678f4c5800c4516ef3c7fa31b78f3fd06b))
* **deps:** move to Spring Boot 4.1.1 and Tomcat 11.0.26 ([cf72827](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/cf72827f0fb29dd96f562343339175a99a4893cd))

## [1.0.2](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/compare/v1.0.1...v1.0.2) (2026-09-27)


### Documentation

* add a README and adopt EPL-2.0 ([97a4c9c](https://github.com/ahincho/nova-java-09-observability-spring-boot-starter/commit/97a4c9c64aba88385684b68b5d1d17b8e2485c3c))

## [1.0.1](https://github.com/ahincho/nova-java-observability-spring-boot-starter/compare/v1.0.0...v1.0.1) (2026-07-13)


### Bug Fixes

* **ci:** OWASP CVE patches + Spring Boot 4.1.0 + dynamic FP suppression ([bbf8499](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/bbf8499dedc501045c79e969aa70fe3de38a0bca))

## 1.0.0 (2026-07-10)


### Features

* **ci:** migrate to release-please + tag-based publish flow (NOVA-SEMVER-13) ([ab9b660](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/ab9b6600db5701cc3753369ee5a14e22d756c3b6))
* **gradle:** add GPG signing plugin for Maven Central publishing (NOVA-SEMVER-10) ([caf6a10](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/caf6a101ae978ac05d1522014c6db74a269e24be))
* **gradle:** enable Local Build Cache and Configuration Cache (NOVA-SEMVER-23-24) ([10b5325](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/10b5325f7e12d8230d807f07982cfcc33ce8c1a6))
* initial commit - Starter Spring Boot: Golden Signals, OTel, Micrometer ([3a7e62a](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/3a7e62a4f9362fe093460ddcf8ca55a525e63bb4))


### Bug Fixes

* **ci:** inline publish-on-tag and remove dirty closure for Gradle 9.6.1 ([c01cd3a](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/c01cd3a070552e60f7ec511b3f9f2dc64e87721d))
* **ci:** use PAT fallback for release-please to enable tag-triggered workflows ([0280efb](https://github.com/ahincho/nova-java-observability-spring-boot-starter/commit/0280efbec365ffc043aea7b20670c8b463cd3a1c))
