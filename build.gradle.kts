plugins {
    `java-library`
    `maven-publish`
    checkstyle
    id("net.nemerosa.versioning") version "4.0.1"
    id("signing")
    id("org.owasp.dependencycheck") version "12.2.2"
    id("org.cyclonedx.bom") version "3.2.4"
}

versioning {
    releaseMode = "snapshot"
    displayMode = "snapshot"
    releaseBuild = false
}

group = "pe.edu.nova.java.starters"
version = findProperty("version") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/milestone")
    }
    // Internal Nova Platform dependency (nova-observability-utils lives in its own repo/package).
    // GITHUB_TOKEN cannot read packages from another repo, so this needs a PAT
    // (falls back to GITHUB_TOKEN for local/manual builds where only that is set).
    maven {
        name = "NovaObservabilityUtils"
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-05-observability-utils")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("NOVA_PACKAGES_READ_TOKEN") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

// Force patched versions of any transitive deps that carry known CVEs (CVSS >= 7).
// Versions verified against Maven Central 2026-07-13. Applied globally so they
// cover any classpath (compile, runtime, even buildscript transitives) so the
// OWASP gate reflects the real, patched state.
//
//  - Apache Tomcat 11.0.26+ for CVE-2026-68525, CVE-2026-65905, CVE-2026-65182
//    (Spring Boot 4.1.1's BOM pins tomcat-embed-core to 11.0.24, which still
//    carries them; they are fixed in 11.0.25)
//  - Apache HttpComponents Core 4.4.16+ for CVE-2026-54428, CVE-2026-54399
//  - Apache HttpComponents Core5 5.4.3+ for CVE-2026-54399 (5.4.2 is not enough)
//  - Apache Commons BeanUtils 1.11.0+ for CVE-2025-48734
//  - plexus-utils 3.6.1+ for CVE-2025-67030 (3.5.1 is not enough)
// Checked against the GitHub Advisory Database on 2026-09-27.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.httpcomponents" && requested.name.startsWith("httpcore")) {
            useVersion("4.4.16")
            because("CVE-2026-54428, CVE-2026-54399 require httpcore 4.4.16+")
        }
        if (requested.group == "org.apache.httpcomponents.core5" && requested.name.startsWith("httpcore5")) {
            useVersion("5.4.3")
            because("CVE-2026-54399 requires httpcore5 5.4.3+")
        }
        if (requested.group == "commons-beanutils" && requested.name == "commons-beanutils") {
            useVersion("1.11.0")
            because("CVE-2025-48734 requires commons-beanutils 1.11.0+")
        }
        if (requested.group == "org.codehaus.plexus" && requested.name == "plexus-utils") {
            useVersion("3.6.1")
            because("CVE-2025-67030 requires plexus-utils 3.6.1+")
        }
    }
}

val springBootVersion = "4.1.1"
val otelInstrumentationVersion = "2.31.1"

// Force Tomcat 11.0.26 strictly - Spring Boot 4.1.1's BOM pins 11.0.24, which
// still carries CVE-2026-68525, CVE-2026-65905 and CVE-2026-65182 (fixed in
// 11.0.25). Constraints + strictly() used instead of resolutionStrategy.force()
// to avoid a Gradle 9 config-cache serialization bug.
dependencies {
    constraints {
        "implementation"("org.apache.tomcat.embed:tomcat-embed-core") {
            version { strictly("11.0.26") }
            because("CVE-2026-68525, CVE-2026-65905, CVE-2026-65182 require 11.0.25+")
        }
        "implementation"("org.apache.tomcat.embed:tomcat-embed-websocket") {
            version { strictly("11.0.26") }
            because("Same CVEs in 11.0.24")
        }
        "implementation"("org.apache.tomcat.embed:tomcat-embed-el") {
            version { strictly("11.0.26") }
            because("Same CVEs in 11.0.24")
        }
        // Kotlin 2.4.0 para CVE-2026-53914 (CRITICAL 9.8), verificado contra Maven Central el
        // 2026-07-13.
        // semconv no se fuerza: CVE-2026-29181 y CVE-2026-39883 son de OpenTelemetry-Go, y
        // semconv-incubating se publica con sufijo (1.43.0-alpha), así que un strictly("1.43.0")
        // viaja en la metadata y ningún consumidor resuelve el starter. La instrumentación
        // 2.31.1 ya trae semconv 1.43.0 y semconv-incubating 1.43.0-alpha.
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib") {
            version { strictly("2.4.0") }
            because("CVE-2026-53914 CRITICAL 9.8 requires 2.4.0+ (2.3.21 is still flagged in NVD)")
        }
    }
    // BOMs — applied to all configurations via api (propagates to implementation, compileOnly, etc.)
    api(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    api(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:$otelInstrumentationVersion"))

    // Annotation processor needs explicit version since platform doesn't apply to annotationProcessor config
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    // Librería pura (transitiva al consumidor)
    api("pe.edu.nova.java.libs:nova-observability-utils:1.0.2")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")

    // Spring Web MVC (for RequestMappingHandlerMapping and OncePerRequestFilter)
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")

    // OpenTelemetry SDK (modo manual)
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter") {
        exclude(group = "io.opentelemetry.contrib", module = "opentelemetry-resource-providers")
        exclude(group = "io.opentelemetry.contrib", module = "opentelemetry-azure-resources")
    }

    // Micrometer → OTel bridge
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    // Jakarta Servlet (para el filtro)
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

checkstyle {
    // Only lint production code. Test suites commonly rely on static-import
    // wildcards (org.junit.jupiter.api.Assertions.*, net.jqwik.api.*), which
    // is an accepted convention that would otherwise trip AvoidStarImport.
    sourceSets = listOf(project.sourceSets.main.get())
}

dependencyCheck {
    // NVD_API_KEY / NOVA_OWASP_FAIL_ON_CVSS are injected by reusable-owasp-check.yml.
    // Locally (no env vars set) this defaults to "never fail" (11.0, matches plugin default)
    // and an empty NVD key (slower updates, acceptable for local dev).
    failBuildOnCVSS = (System.getenv("NOVA_OWASP_FAIL_ON_CVSS") ?: "11").toFloat()
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""

    // Restrict OWASP analysis to configurations that actually propagate to
    // consumers of this artifact. Without this, the plugin also scans test
    // configurations and (via the gradle daemon's own classpath) the buildscript
    // plugin transitives that NEVER reach a downstream project consuming this
    // artifact - for libraries this surfaces CVEs in httpcore, plexus-utils,
    // and commons-beanutils that are build-time only and not security-relevant
    // for library consumers.
    //
    // Verified pattern in nova-java-mask-utils PR#6 (commit f133fa2) and
    // nova-java-api-standard PR#3 (commit 8c7e89f).
    scanConfigurations = listOf("compileClasspath", "runtimeClasspath")

    // Must match the path reusable-owasp-check.yml caches AND restores the
    // shared nova-devops NVD mirror into. Do NOT rely on the plugin's
    // built-in default here - it was never verified/documented and previous
    // cache sizes (15-57MB) strongly suggest it did not match what was
    // being cached. Locally (no env var set) this falls back to a plain,
    // dedicated directory outside ~/.gradle so it is never confused with
    // unrelated Gradle caches.
    data.directory = System.getenv("NOVA_OWASP_DATA_DIR")
        ?: "${System.getProperty("user.home")}/.dependency-check-data"

    // Dynamic CVE suppression file (documented false positives, opt-in via
    // NOVA_OWASP_CVE_SUPPRESSIONS repo variable + nova-devops/docs/owasp-suppressions.json
    // registry). The reusable-owasp-check.yml workflow generates the XML from
    // the central registry and exports the path as NOVA_OWASP_SUPPRESSIONS_FILE.
    // Empty when the repo variable is unset (safe default: no suppressions).
    // Note: suppressionFiles in org.owasp.dependencycheck 12.2.2 is a
    // ListProperty<String> (paths), not a ListProperty<File>. add(element: String)
    // is the only applicable overload.
    System.getenv("NOVA_OWASP_SUPPRESSIONS_FILE")?.let { path ->
        if (File(path).exists()) {
            suppressionFiles.add(path)
        }
    }

    // Investigation (2026-07-13, docs/java/06-semantic-versioning-en-java.md):
    // a cold NVD sync took 50+ min mostly due to cache scoping, NOT these
    // analyzers - but disabling ecosystems that plainly do not exist
    // anywhere in this repo removes real (if smaller) analyze-phase
    // overhead and network surface at zero detection-feature cost.
    //
    // Deliberately NOT disabled: nodeEnabled / nodeAudit.enabled
    // (package.json IS present - commitlint/lefthook devDependencies -
    // keep scanning it for real) and opensslEnabled (harmless/fast).
    // RetireJS IS disabled: it fingerprints vendored/bundled JS *library*
    // files - this repo has no such files, only commitlint.config.js.
    analyzers {
        retirejs.enabled = false
        assemblyEnabled = false
        nuspecEnabled = false
        nugetconfEnabled = false
        msbuildEnabled = false
        golangDepEnabled = false
        golangModEnabled = false
        swiftEnabled = false
        swiftPackageResolvedEnabled = false
        cocoapodsEnabled = false
        composerEnabled = false
        cpanEnabled = false
        cmakeEnabled = false
        autoconfEnabled = false
        bundleAuditEnabled = false
        pyDistributionEnabled = false
        pyPackageEnabled = false
        rubygemsEnabled = false
        dartEnabled = false

        // Per-call analyzer override (opt-in): the reusable-owasp-check.yml workflow
        // reads inputs.analyzer-override (or vars.DEPENDENCY_CHECK_ANALYZERS) and
        // exports it as NOVA_OWASP_ANALYZER_OVERRIDE. Re-enable here any analyzer
        // that this repo actually depends on (npm frontend, native C/C++ source,
        // etc.). The default above already disables every analyzer the current
        // build does not need. The Maven plugin has a richer analyzer set than the
        // Gradle one (verified by javap on dependency-check-gradle-12.2.2.jar): some
        // Maven-only tokens (gogradle, yarn, pnpm, pipenv, poetry, pyinstall,
        // setuptools, clang, haskell, mix, rebar, cargo) are accepted by the
        // reusable workflow but no-op for Gradle consumers. Format:
        // comma-separated, e.g. "npm,clang" or "npm, node, yarn".
        val enableAnalyzers = System.getenv("NOVA_OWASP_ANALYZER_OVERRIDE")
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()
        if (enableAnalyzers.isNotEmpty()) {
            enableAnalyzers.forEach { name ->
                when (name) {
                    "nuspec" -> nuspecEnabled = true
                    "nugetconf" -> nugetconfEnabled = true
                    "msbuild" -> msbuildEnabled = true
                    "golang", "go", "golangdep" -> golangDepEnabled = true
                    "golangmod", "gomod" -> golangModEnabled = true
                    "swift" -> { swiftEnabled = true; swiftPackageResolvedEnabled = true }
                    "cocoapods" -> cocoapodsEnabled = true
                    "composer" -> composerEnabled = true
                    "cpanfile", "perl" -> cpanEnabled = true
                    "cmake" -> cmakeEnabled = true
                    "autoconf" -> autoconfEnabled = true
                    "bundle", "ruby", "bundler" -> bundleAuditEnabled = true
                    "pydistribution" -> pyDistributionEnabled = true
                    "pypackage", "pip" -> pyPackageEnabled = true
                    "rubygems" -> rubygemsEnabled = true
                    "dart", "pub" -> dartEnabled = true
                    "assembly" -> assemblyEnabled = true
                    "retirejs" -> retirejs.enabled = true
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ahincho/nova-java-09-observability-spring-boot-starter")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val gpgKeyId: String? = System.getenv("GPG_SIGNING_KEY_ID")
    val gpgKey: String? = System.getenv("GPG_SIGNING_KEY")
    val gpgPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")

    if (gpgKeyId != null && gpgKey != null) {
        useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword ?: "")
        sign(publishing.publications)
    }
}