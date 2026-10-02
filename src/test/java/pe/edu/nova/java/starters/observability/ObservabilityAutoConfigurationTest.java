package pe.edu.nova.java.starters.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.edu.nova.java.starters.observability.config.ObservabilityProperties;
import pe.edu.nova.java.starters.observability.health.CollectorHealthIndicator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qué registra la auto-configuración según haya o no un endpoint OTLP. No necesita un servidor, y
 * tampoco corre el post-procesador del entorno: la decisión del indicador no depende de él.
 */
class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void withoutAnEndpointTheServiceStartsWithoutACollectorHealthIndicator() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CollectorHealthIndicator.class);
            assertThat(context).hasSingleBean(ObservabilityProperties.class);
        });
    }

    @Test
    void withoutAnEndpointThePropertiesDoNotInventOne() {
        runner.run(context ->
                assertThat(context.getBean(ObservabilityProperties.class).getOtlp().getEndpoint()).isNull());
    }

    @Test
    void withTheStarterEndpointTheIndicatorChecksIt() {
        try (StubCollector collector = new StubCollector()) {
            runner.withPropertyValues("nova.observability.otlp.endpoint=" + collector.endpoint())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(CollectorHealthIndicator.class);
                        Health health = context.getBean(CollectorHealthIndicator.class).health();
                        assertThat(health.getStatus()).isEqualTo(Status.UP);
                        assertThat(health.getDetails()).containsEntry("endpoint", collector.endpoint());
                    });
        }
    }

    @Test
    void withOnlyTheStandardEndpointTheIndicatorChecksIt() {
        try (StubCollector collector = new StubCollector()) {
            runner.withPropertyValues("otel.exporter.otlp.endpoint=" + collector.endpoint())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(CollectorHealthIndicator.class);
                        Health health = context.getBean(CollectorHealthIndicator.class).health();
                        assertThat(health.getStatus()).isEqualTo(Status.UP);
                        assertThat(health.getDetails()).containsEntry("endpoint", collector.endpoint());
                    });
        }
    }

    @Test
    void theIndicatorChecksTheEndpointTheExporterWillUseWhenBothAreSet() {
        try (StubCollector standard = new StubCollector(); StubCollector starter = new StubCollector()) {
            runner.withPropertyValues(
                            "nova.observability.otlp.endpoint=" + starter.endpoint(),
                            "otel.exporter.otlp.endpoint=" + standard.endpoint())
                    .run(context -> {
                        Health health = context.getBean(CollectorHealthIndicator.class).health();
                        assertThat(health.getDetails()).containsEntry("endpoint", standard.endpoint());
                    });
            assertThat(standard.requests()).hasSize(1);
            assertThat(starter.requests()).isEmpty();
        }
    }

    @Test
    void aBlankEndpointRegistersNoIndicator() {
        runner.withPropertyValues("nova.observability.otlp.endpoint=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CollectorHealthIndicator.class);
        });
    }

    @Test
    void aDisabledStarterRegistersNothingEvenWithAnEndpoint() {
        runner.withPropertyValues(
                        "nova.observability.enabled=false", "nova.observability.otlp.endpoint=http://collector:4318")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CollectorHealthIndicator.class);
                    assertThat(context).doesNotHaveBean(ObservabilityProperties.class);
                });
    }
}
