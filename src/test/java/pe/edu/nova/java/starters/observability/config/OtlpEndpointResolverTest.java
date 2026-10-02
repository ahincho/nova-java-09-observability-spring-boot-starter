package pe.edu.nova.java.starters.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** La regla de «hay endpoint»: la usan el post-procesador y el indicador de salud. */
class OtlpEndpointResolverTest {

    @Test
    void withoutAnyPropertyThereIsNoEndpoint() {
        assertThat(OtlpEndpointResolver.resolve(new MockEnvironment())).isEmpty();
    }

    @Test
    void theStarterPropertyIsAnEndpoint() {
        MockEnvironment environment = new MockEnvironment().withProperty(OtlpEndpointResolver.STARTER_PROPERTY, "http://a:4318");

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://a:4318");
    }

    @Test
    void theStandardPropertyIsAnEndpoint() {
        MockEnvironment environment = new MockEnvironment().withProperty(OtlpEndpointResolver.STANDARD_PROPERTY, "http://b:4318");

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://b:4318");
    }

    @Test
    void theStandardEnvironmentVariableIsAnEndpointThroughRelaxedBinding() {
        MockEnvironment environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addLast(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://c:4318")));

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://c:4318");
    }

    @Test
    void theStarterVariableIsAnEndpointThroughRelaxedBindingToo() {
        MockEnvironment environment = new MockEnvironment();
        environment
                .getPropertySources()
                .addLast(new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("NOVA_OBSERVABILITY_OTLP_ENDPOINT", "http://d:4318")));

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://d:4318");
    }

    @Test
    void theStandardPropertyWinsWhenBothAreSet() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(OtlpEndpointResolver.STARTER_PROPERTY, "http://starter:4318")
                .withProperty(OtlpEndpointResolver.STANDARD_PROPERTY, "http://standard:4318");

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://standard:4318");
    }

    @Test
    void aBlankStarterValueDoesNotHideTheStandardOne() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(OtlpEndpointResolver.STARTER_PROPERTY, "")
                .withProperty(OtlpEndpointResolver.STANDARD_PROPERTY, "http://standard:4318");

        assertThat(OtlpEndpointResolver.resolve(environment)).contains("http://standard:4318");
    }

    @Test
    void aBlankStandardValueDecidesThereIsNoEndpointBecauseThatIsWhatTheExporterWouldRead() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(OtlpEndpointResolver.STANDARD_PROPERTY, "")
                .withProperty(OtlpEndpointResolver.STARTER_PROPERTY, "http://starter:4318");

        assertThat(OtlpEndpointResolver.resolve(environment)).isEmpty();
    }

    @Test
    void twoBlankValuesAreNoEndpoint() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(OtlpEndpointResolver.STANDARD_PROPERTY, " ")
                .withProperty(OtlpEndpointResolver.STARTER_PROPERTY, "");

        assertThat(OtlpEndpointResolver.resolve(environment)).isEmpty();
    }
}
