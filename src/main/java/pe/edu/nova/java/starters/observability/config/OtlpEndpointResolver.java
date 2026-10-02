package pe.edu.nova.java.starters.observability.config;

import org.springframework.core.env.PropertyResolver;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Decide si el servicio tiene un endpoint OTLP configurado. Es la única regla de «hay endpoint»
 * del starter: la usan el post-procesador que enciende o apaga la exportación y la condición del
 * indicador de salud del Collector, para que las dos decisiones nunca se contradigan.
 *
 * <p>El endpoint puede venir de dos propiedades. El exportador de OpenTelemetry solo lee la
 * estándar y la del starter se la completa, así que si la estándar está definida decide ella, y si
 * no, vale la del starter:</p>
 * <ol>
 *   <li>{@value #STANDARD_PROPERTY}, que Spring también expone desde la variable de entorno
 *       {@code OTEL_EXPORTER_OTLP_ENDPOINT} por su binding relajado.</li>
 *   <li>{@value #STARTER_PROPERTY}, la propiedad del starter.</li>
 * </ol>
 *
 * <p>Un valor en blanco cuenta como no configurado. Así un servicio puede escribir
 * {@code ${OTEL_EXPORTER_OTLP_ENDPOINT:}} en su {@code application.yaml} sin que la variable
 * vacía encienda la exportación.</p>
 */
public final class OtlpEndpointResolver {

    /**
     * Propiedad estándar de OpenTelemetry. La variable de entorno equivalente es
     * {@code OTEL_EXPORTER_OTLP_ENDPOINT}.
     */
    public static final String STANDARD_PROPERTY = "otel.exporter.otlp.endpoint";

    /** Propiedad del starter. */
    public static final String STARTER_PROPERTY = "nova.observability.otlp.endpoint";

    private OtlpEndpointResolver() {
    }

    /**
     * Devuelve el endpoint OTLP efectivo del servicio.
     *
     * @param environment el entorno donde buscar las dos propiedades
     * @return el endpoint, o vacío si la propiedad que decide no tiene texto
     */
    public static Optional<String> resolve(PropertyResolver environment) {
        String endpoint = environment.getProperty(STANDARD_PROPERTY);
        if (endpoint == null) {
            endpoint = environment.getProperty(STARTER_PROPERTY);
        }
        return Optional.ofNullable(endpoint).filter(StringUtils::hasText);
    }
}
