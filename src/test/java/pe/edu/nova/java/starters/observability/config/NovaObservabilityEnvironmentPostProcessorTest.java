package pe.edu.nova.java.starters.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import pe.edu.nova.java.starters.observability.RecordingLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué le agrega el starter al entorno antes de que exista el SDK de OpenTelemetry. El procesador se
 * invoca directamente, sobre un entorno que solo tiene lo que cada prueba le pone, para que no
 * dependa de las variables {@code OTEL_*} de la máquina.
 */
class NovaObservabilityEnvironmentPostProcessorTest {

    private static final String STARTER_ENDPOINT = "nova.observability.otlp.endpoint";
    private static final String STANDARD_ENDPOINT = "otel.exporter.otlp.endpoint";

    private final List<String> log = new ArrayList<>();
    private final NovaObservabilityEnvironmentPostProcessor processor =
            new NovaObservabilityEnvironmentPostProcessor(destination -> RecordingLog.into(log));

    @Test
    void withoutAnEndpointTheThreeExportersAreOff() {
        MockEnvironment environment = environment();

        process(environment);

        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("none");
        assertThat(environment.getProperty("otel.metrics.exporter")).isEqualTo("none");
        assertThat(environment.getProperty("otel.logs.exporter")).isEqualTo("none");
        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isNull();
    }

    @Test
    void withoutAnEndpointNothingTheStarterAddsPointsAtLocalhost() {
        MockEnvironment environment = environment();

        process(environment);

        assertThat(starterSource(environment).getPropertyNames()).isNotEmpty();
        assertThat(valuesOf(starterSource(environment)))
                .noneMatch(value -> value.contains("localhost") || value.contains("127.0.0.1") || value.contains("4318"));
    }

    @Test
    void withoutAnEndpointTheTracesAreStillSampledSoTheTraceIdKeepsWorking() {
        MockEnvironment environment = environment();

        process(environment);

        assertThat(environment.getProperty("otel.traces.sampler")).isEqualTo("parentbased_traceidratio");
        assertThat(environment.getProperty("otel.traces.sampler.arg")).isEqualTo("1.0");
        assertThat(environment.getProperty("otel.sdk.disabled")).isNull();
    }

    @Test
    void withoutAnEndpointItSaysOnceAndInSpanishThatTheExportIsOff() {
        process(environment());

        assertThat(log).hasSize(1);
        assertThat(log.get(0))
                .startsWith("info: Exportación OTLP apagada para trazas, métricas y logs")
                .contains("no hay un endpoint configurado")
                .contains("nova.observability.otlp.endpoint")
                .contains("OTEL_EXPORTER_OTLP_ENDPOINT");
    }

    @Test
    void theStarterEndpointIsMappedToTheStandardPropertyAndTheExportersStayOn() {
        MockEnvironment environment = environment().withProperty(STARTER_ENDPOINT, "http://collector:4318");

        process(environment);

        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isEqualTo("http://collector:4318");
        assertThat(environment.containsProperty("otel.traces.exporter")).isFalse();
        assertThat(environment.containsProperty("otel.metrics.exporter")).isFalse();
        assertThat(environment.containsProperty("otel.logs.exporter")).isFalse();
        assertThat(log).noneMatch(line -> line.startsWith("info:"));
    }

    @Test
    void onlyTheStandardEndpointIsRespectedAndTheExportersStayOn() {
        MockEnvironment environment = environment().withProperty(STANDARD_ENDPOINT, "http://standard:4318");

        process(environment);

        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isEqualTo("http://standard:4318");
        assertThat(environment.containsProperty("otel.traces.exporter")).isFalse();
        assertThat(starterSource(environment).containsProperty(STANDARD_ENDPOINT)).isFalse();
        assertThat(log).noneMatch(line -> line.startsWith("info:"));
    }

    @Test
    void theStandardEnvironmentVariableCountsAsAnEndpoint() {
        MockEnvironment environment = environment();
        environment
                .getPropertySources()
                .addLast(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://from-env:4318")));

        process(environment);

        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isEqualTo("http://from-env:4318");
        assertThat(environment.containsProperty("otel.traces.exporter")).isFalse();
        assertThat(log).noneMatch(line -> line.startsWith("info:"));
    }

    @Test
    void theStarterEndpointFromAnEnvironmentVariableIsMappedToo() {
        MockEnvironment environment = environment();
        environment
                .getPropertySources()
                .addLast(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("NOVA_OBSERVABILITY_OTLP_ENDPOINT", "http://from-env:4318")));

        process(environment);

        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isEqualTo("http://from-env:4318");
        assertThat(environment.containsProperty("otel.traces.exporter")).isFalse();
    }

    @Test
    void theStandardEndpointWinsOverTheStarterOne() {
        MockEnvironment environment = environment()
                .withProperty(STARTER_ENDPOINT, "http://starter:4318")
                .withProperty(STANDARD_ENDPOINT, "http://standard:4318");

        process(environment);

        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isEqualTo("http://standard:4318");
    }

    @Test
    void aBlankStandardEndpointMeansNoEndpointBecauseItIsWhatTheExporterWouldRead() {
        MockEnvironment environment = environment()
                .withProperty(STANDARD_ENDPOINT, "")
                .withProperty(STARTER_ENDPOINT, "http://starter:4318");

        process(environment);

        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("none");
        assertThat(log).singleElement().asString().startsWith("info: Exportación OTLP apagada");
    }

    @Test
    void anExplicitExporterIsNeverOverwritten() {
        MockEnvironment environment = environment().withProperty("otel.traces.exporter", "otlp");

        process(environment);

        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("otlp");
        assertThat(environment.getProperty("otel.metrics.exporter")).isEqualTo("none");
        assertThat(environment.getProperty("otel.logs.exporter")).isEqualTo("none");
    }

    @Test
    void anExplicitExporterFromTheEnvironmentIsNeverOverwrittenAndTheLogOnlyNamesWhatIsOff() {
        MockEnvironment environment = environment();
        environment
                .getPropertySources()
                .addLast(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("OTEL_METRICS_EXPORTER", "logging")));

        process(environment);

        assertThat(environment.getProperty("otel.metrics.exporter")).isEqualTo("logging");
        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("none");
        assertThat(log).singleElement().asString().startsWith("info: Exportación OTLP apagada para trazas y logs:");
    }

    @Test
    void ifTheOperatorChoseEveryExporterNothingIsSaidToBeOff() {
        MockEnvironment environment = environment()
                .withProperty("otel.traces.exporter", "otlp")
                .withProperty("otel.metrics.exporter", "otlp")
                .withProperty("otel.logs.exporter", "otlp");

        process(environment);

        assertThat(log).noneMatch(line -> line.startsWith("info:"));
    }

    @Test
    void anExplicitSamplerIsNeverOverwritten() {
        MockEnvironment environment = environment()
                .withProperty("otel.traces.sampler", "always_on")
                .withProperty("otel.traces.sampler.arg", "0.5");

        process(environment);

        assertThat(environment.getProperty("otel.traces.sampler")).isEqualTo("always_on");
        assertThat(environment.getProperty("otel.traces.sampler.arg")).isEqualTo("0.5");
    }

    @Test
    void theSamplingRatioOfTheStarterIsMapped() {
        MockEnvironment environment = environment().withProperty("nova.observability.traces.sampling-ratio", "0.25");

        process(environment);

        assertThat(environment.getProperty("otel.traces.sampler")).isEqualTo("parentbased_traceidratio");
        assertThat(environment.getProperty("otel.traces.sampler.arg")).isEqualTo("0.25");
    }

    @Test
    void protocolAndTimeoutHaveTheirDefaultsWhenNothingIsConfigured() {
        MockEnvironment environment = environment();

        process(environment);

        assertThat(environment.getProperty("otel.exporter.otlp.protocol")).isEqualTo("http/protobuf");
        assertThat(environment.getProperty("otel.exporter.otlp.timeout")).isEqualTo("10000");
    }

    @Test
    void protocolAndTimeoutOfTheStarterAreMapped() {
        MockEnvironment environment = environment()
                .withProperty("nova.observability.otlp.protocol", "grpc")
                .withProperty("nova.observability.otlp.timeout", "3s");

        process(environment);

        assertThat(environment.getProperty("otel.exporter.otlp.protocol")).isEqualTo("grpc");
        assertThat(environment.getProperty("otel.exporter.otlp.timeout")).isEqualTo("3000");
    }

    @Test
    void aBlankEndpointCountsAsNoEndpoint() {
        MockEnvironment environment = environment().withProperty(STARTER_ENDPOINT, "  ");

        process(environment);

        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("none");
        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isNull();
    }

    @Test
    void anEmptyPlaceholderForTheEnvironmentVariableCountsAsNoEndpoint() {
        MockEnvironment environment = environment().withProperty(STARTER_ENDPOINT, "${OTEL_EXPORTER_OTLP_ENDPOINT:}");

        process(environment);

        assertThat(environment.getProperty("otel.traces.exporter")).isEqualTo("none");
        assertThat(environment.getProperty(STANDARD_ENDPOINT)).isNull();
    }

    @Test
    void aDisabledStarterAppliesNothing() {
        MockEnvironment environment = environment().withProperty("nova.observability.enabled", "false");

        process(environment);

        assertThat(environment.getPropertySources().contains(NovaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
        assertThat(environment.containsProperty("otel.traces.exporter")).isFalse();
        assertThat(environment.containsProperty("otel.exporter.otlp.protocol")).isFalse();
        assertThat(log).isEmpty();
    }

    @Test
    void itSitsAtTheLowestPriorityOfTheEnvironment() {
        MockEnvironment environment = environment();

        process(environment);

        List<String> names = environment.getPropertySources().stream().map(PropertySource::getName).toList();
        assertThat(names).last().isEqualTo(NovaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }

    @Test
    void itOnlyActsOncePerEnvironment() {
        MockEnvironment environment = environment();

        process(environment);
        process(environment);

        assertThat(log).hasSize(1);
        assertThat(environment.getPropertySources().stream().filter(source -> source.getName()
                        .equals(NovaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)))
                .hasSize(1);
    }

    @Test
    void itRunsAfterTheConfigurationFilesAndTheNovaSecretsHaveBeenRead() {
        // Los secretos de Nova usan ConfigDataEnvironmentPostProcessor.ORDER + 1.
        assertThat(processor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER + 1);
    }

    private void process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication());
    }

    /** Un entorno que solo tiene lo que la prueba le pone, con las propiedades de configuración como en Spring Boot. */
    private static MockEnvironment environment() {
        MockEnvironment environment = new MockEnvironment();
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    private static EnumerablePropertySource<?> starterSource(MockEnvironment environment) {
        PropertySource<?> source =
                environment.getPropertySources().get(NovaObservabilityEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
        assertThat(source).isInstanceOf(EnumerablePropertySource.class);
        return (EnumerablePropertySource<?>) source;
    }

    private static List<String> valuesOf(EnumerablePropertySource<?> source) {
        List<String> values = new ArrayList<>();
        for (String name : source.getPropertyNames()) {
            values.add(String.valueOf(source.getProperty(name)));
        }
        return values;
    }
}
