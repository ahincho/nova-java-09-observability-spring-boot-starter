package pe.edu.nova.java.starters.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import pe.edu.nova.java.starters.observability.health.CollectorHealthIndicator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * El starter en un servicio Spring Boot de verdad, arrancado con {@code SpringApplication} y toda su
 * auto-configuración, como lo usa un consumidor. Es la única prueba que recorre el camino completo:
 * el registro en {@code spring.factories}, el orden del post-procesador respecto del SDK de
 * OpenTelemetry y la exportación hasta un Collector de mentira.
 */
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityStartupTest {

    /** El servicio de las pruebas: nada propio, solo la auto-configuración del classpath. */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ServiceWithTheStarter {
    }

    @BeforeEach
    void theMachineHasNoOpenTelemetryConfiguration() {
        // Una variable OTEL_* o una propiedad otel.* de la máquina cambiaría lo que se prueba.
        assumeTrue(
                System.getenv().keySet().stream().noneMatch(name -> name.startsWith("OTEL_")),
                "la máquina define variables OTEL_*");
        assumeTrue(
                System.getProperties().stringPropertyNames().stream().noneMatch(name -> name.startsWith("otel.")),
                "la máquina define propiedades otel.*");
    }

    @Test
    void withoutAnEndpointTheServiceStartsQuietHealthyAndStillTraces(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            ConfigurableEnvironment environment = context.getEnvironment();

            // La exportación está apagada en el SDK mismo, no solo en el entorno.
            ConfigProperties sdkConfig = context.getBean(ConfigProperties.class);
            assertThat(sdkConfig.getList("otel.traces.exporter")).containsExactly("none");
            assertThat(sdkConfig.getList("otel.metrics.exporter")).containsExactly("none");
            assertThat(sdkConfig.getList("otel.logs.exporter")).containsExactly("none");
            assertThat(environment.getProperty("otel.exporter.otlp.endpoint")).isNull();
            assertThat(otelAndNovaValues(environment)).noneMatch(value -> value.contains("localhost") || value.contains("4318"));

            // Sin Collector no hay indicador, y el servicio no queda caído por eso.
            assertThat(context.getBeansOfType(CollectorHealthIndicator.class)).isEmpty();
            HttpResponse<String> health = get(context, "/actuator/health");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("collector");
            assertThat(get(context, "/actuator/health/collector").statusCode()).isEqualTo(404);

            // Las trazas se siguen generando: el traceId de los logs no depende de que haya a dónde enviarlas.
            Span span = context.getBean(OpenTelemetry.class).getTracer("nova-test").spanBuilder("probe").startSpan();
            assertThat(span.getSpanContext().isValid()).isTrue();
            span.end();
        }

        assertThat(output.getOut())
                .containsOnlyOnce("Exportación OTLP apagada para trazas, métricas y logs: no hay un endpoint configurado");
    }

    @Test
    void withTheStarterEndpointTheSpansReachTheCollectorAndItsHealthIsReported(CapturedOutput output) throws Exception {
        try (StubCollector collector = new StubCollector();
                ConfigurableApplicationContext context =
                        start("--nova.observability.otlp.endpoint=" + collector.endpoint())) {
            assertThat(context.getEnvironment().getProperty("otel.exporter.otlp.endpoint")).isEqualTo(collector.endpoint());

            exportASpan(context);

            assertThat(collector.requests()).anySatisfy(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1/traces");
                assertThat(request.contentType()).isEqualTo("application/x-protobuf");
                assertThat(request.bodyLength()).isPositive();
            });
            assertThat(get(context, "/actuator/health").statusCode()).isEqualTo(200);
            HttpResponse<String> health = get(context, "/actuator/health/collector");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"").contains("\"endpoint\":\"" + collector.endpoint() + "\"");
        }

        assertThat(output.getOut()).doesNotContain("Exportación OTLP apagada");
    }

    @Test
    void anEndpointFromTheConfigurationFileIsSeenBecauseThePostProcessorRunsAfterTheFilesAreRead(@TempDir Path directory)
            throws Exception {
        // Los argumentos de la línea de comandos ya están en el entorno antes de cualquier post-procesador; un
        // archivo de configuración no. Esta es la prueba de que el orden del procesador es el correcto.
        try (StubCollector collector = new StubCollector()) {
            Files.writeString(
                    directory.resolve("application.yaml"),
                    "nova:\n  observability:\n    otlp:\n      endpoint: " + collector.endpoint() + "\n");
            try (ConfigurableApplicationContext context =
                    start("--spring.config.additional-location=" + directory.toUri())) {
                assertThat(context.getEnvironment().getProperty("otel.traces.exporter")).isNull();
                exportASpan(context);

                assertThat(collector.requests()).anySatisfy(request -> {
                    assertThat(request.method()).isEqualTo("POST");
                    assertThat(request.path()).isEqualTo("/v1/traces");
                });
            }
        }
    }

    @Test
    void withOnlyTheStandardEndpointTheSpansReachTheCollectorToo() throws Exception {
        try (StubCollector collector = new StubCollector();
                ConfigurableApplicationContext context =
                        start("--otel.exporter.otlp.endpoint=" + collector.endpoint())) {
            exportASpan(context);

            assertThat(collector.requests()).anySatisfy(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v1/traces");
            });
            HttpResponse<String> health = get(context, "/actuator/health/collector");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"").contains("\"endpoint\":\"" + collector.endpoint() + "\"");
        }
    }

    @Test
    void withTheExporterChosenOnPurposeThatSignalIsNotTurnedOff() throws Exception {
        try (ConfigurableApplicationContext context = start("--otel.traces.exporter=logging")) {
            ConfigProperties sdkConfig = context.getBean(ConfigProperties.class);
            assertThat(sdkConfig.getList("otel.traces.exporter")).containsExactly("logging");
            assertThat(sdkConfig.getList("otel.metrics.exporter")).containsExactly("none");
        }
    }

    private static ConfigurableApplicationContext start(String... arguments) {
        List<String> all = new ArrayList<>(List.of(
                "--server.port=0", "--spring.main.banner-mode=off", "--management.endpoint.health.show-details=always"));
        all.addAll(List.of(arguments));
        return new SpringApplicationBuilder(ServiceWithTheStarter.class)
                .web(WebApplicationType.SERVLET)
                .run(all.toArray(String[]::new));
    }

    /** Crea un span y fuerza el envío, para no esperar al procesador por lotes. */
    private static void exportASpan(ConfigurableApplicationContext context) {
        Span span = context.getBean(OpenTelemetry.class).getTracer("nova-test").spanBuilder("probe").startSpan();
        span.end();
        context.getBean(AutoConfiguredOpenTelemetrySdk.class)
                .getOpenTelemetrySdk()
                .getSdkTracerProvider()
                .forceFlush()
                .join(10, TimeUnit.SECONDS);
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path)
            throws IOException, InterruptedException {
        String port = context.getEnvironment().getProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /** Los valores efectivos de todas las propiedades {@code otel.*} y {@code nova.observability.*}. */
    private static List<String> otelAndNovaValues(ConfigurableEnvironment environment) {
        List<String> values = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("otel.") || name.startsWith("nova.observability")) {
                        values.add(String.valueOf(environment.getProperty(name)));
                    }
                }
            }
        }
        return values;
    }
}
