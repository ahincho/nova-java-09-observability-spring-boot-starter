package pe.edu.nova.java.starters.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import pe.edu.nova.java.starters.observability.config.LogCorrelationAutoConfiguration;
import pe.edu.nova.java.starters.observability.config.MetricsAutoConfiguration;
import pe.edu.nova.java.starters.observability.config.ObservabilityProperties;
import pe.edu.nova.java.starters.observability.config.OtlpEndpointResolver;
import pe.edu.nova.java.starters.observability.config.TracingAutoConfiguration;
import pe.edu.nova.java.starters.observability.health.CollectorHealthIndicator;

/**
 * Auto-configuración principal del starter de observabilidad.
 *
 * <p>Se activa cuando {@code nova.observability.enabled=true} (por defecto).
 * Importa todas las sub-configuraciones y, cuando el servicio tiene un endpoint OTLP
 * configurado, registra el health check del OpenTelemetry Collector.</p>
 *
 * <p>La exportación OTLP no se configura aquí: la prepara
 * {@link pe.edu.nova.java.starters.observability.config.NovaObservabilityEnvironmentPostProcessor}
 * antes de que exista el contexto, para que el SDK de OpenTelemetry ya la encuentre.</p>
 *
 * <p>Sub-configuraciones importadas:</p>
 * <ul>
 *   <li>{@link TracingAutoConfiguration} — trazas distribuidas y aspectos AOP</li>
 *   <li>{@link MetricsAutoConfiguration} — métricas Four Golden Signals</li>
 *   <li>{@link LogCorrelationAutoConfiguration} — correlación de logs con traceId/spanId</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "nova.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(ObservabilityProperties.class)
@Import({
        TracingAutoConfiguration.class,
        MetricsAutoConfiguration.class,
        LogCorrelationAutoConfiguration.class
})
public class ObservabilityAutoConfiguration {

    /**
     * Registra el indicador de salud del OpenTelemetry Collector, solo si hay un endpoint OTLP
     * configurado (ver {@link OtlpEndpointResolver}). Sin endpoint no hay Collector que verificar,
     * y un indicador condenado a reportar {@code DOWN} dejaría al servicio caído en
     * {@code /actuator/health} por algo que nunca configuró.
     *
     * @param environment el entorno, de donde sale el endpoint efectivo
     * @return instancia de {@link CollectorHealthIndicator} que verifica ese endpoint
     */
    @Bean
    @Conditional(OtlpEndpointConfigured.class)
    public CollectorHealthIndicator collectorHealthIndicator(Environment environment) {
        String endpoint = OtlpEndpointResolver.resolve(environment)
                .orElseThrow(() -> new IllegalStateException("No hay un endpoint OTLP configurado"));
        return new CollectorHealthIndicator(endpoint);
    }

    /** Se cumple cuando el servicio tiene un endpoint OTLP configurado. */
    static class OtlpEndpointConfigured extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // El mensaje no incluye el endpoint: una URL puede traer credenciales y el informe de
            // condiciones se imprime con --debug.
            if (OtlpEndpointResolver.resolve(context.getEnvironment()).isPresent()) {
                return ConditionOutcome.match("hay un endpoint OTLP configurado");
            }
            return ConditionOutcome.noMatch("no hay un endpoint OTLP configurado");
        }
    }
}
