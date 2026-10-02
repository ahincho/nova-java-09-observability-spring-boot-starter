package pe.edu.nova.java.starters.observability.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import pe.edu.nova.java.starters.observability.config.ObservabilityProperties.OtlpProperties;
import pe.edu.nova.java.starters.observability.config.ObservabilityProperties.TracesProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configura OpenTelemetry a partir de las propiedades del starter, antes de que exista su SDK.
 *
 * <p>Es un {@code EnvironmentPostProcessor} y no una {@code @Configuration} porque el SDK de
 * {@code opentelemetry-spring-boot-starter} lee su configuración del {@code Environment} cuando
 * crea su bean, y una {@code @Configuration} de este starter podía ejecutarse después. Corre justo
 * después de que Spring lee los archivos de configuración y los secretos de Nova (que usan
 * {@code ORDER + 1}), así que ve el endpoint venga de donde venga.</p>
 *
 * <p>Todo lo que agrega va en una fuente de propiedades de la <em>menor</em> prioridad, así que
 * cualquier valor que defina el operador gana. Hace tres cosas:</p>
 * <ol>
 *   <li>Pasa el protocolo, el timeout y el muestreo del starter a las propiedades {@code otel.*}.</li>
 *   <li>Si hay un endpoint (ver {@link OtlpEndpointResolver}), pasa el de
 *       {@value OtlpEndpointResolver#STARTER_PROPERTY} a {@value OtlpEndpointResolver#STANDARD_PROPERTY}.</li>
 *   <li>Si no lo hay, apaga los exportadores de trazas, métricas y logs ({@code none}) y lo dice una
 *       sola vez en el log. Las trazas se siguen generando, con sus ids válidos, y las métricas de
 *       Micrometer se siguen registrando; solo no se envían a ninguna parte.</li>
 * </ol>
 *
 * <p>No hace nada si {@code nova.observability.enabled} es {@code false}.</p>
 */
public class NovaObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Después de la carga de los archivos de configuración y de los secretos de Nova. */
    public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 10;

    /** El nombre de la fuente de propiedades que agrega el starter. */
    public static final String PROPERTY_SOURCE_NAME = "nova-observability-otlp";

    private static final String ENABLED_PROPERTY = "nova.observability.enabled";

    /** Las señales que exporta OpenTelemetry, cada una con la propiedad que elige su exportador. */
    private static final List<Signal> SIGNALS = List.of(
            new Signal("trazas", "otel.traces.exporter"),
            new Signal("métricas", "otel.metrics.exporter"),
            new Signal("logs", "otel.logs.exporter"));

    private final Log log;

    /**
     * Crea el procesador; lo instancia Spring Boot.
     *
     * @param logFactory el log diferido, porque el sistema de logging todavía no está listo
     */
    public NovaObservabilityEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(NovaObservabilityEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Mismo criterio que el @ConditionalOnProperty de ObservabilityAutoConfiguration. Si ya pasó
        // por este entorno (un contexto padre, por ejemplo), no se repite ni se vuelve a loguear.
        boolean enabled = "true".equalsIgnoreCase(environment.getProperty(ENABLED_PROPERTY, "true"));
        if (!enabled || environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Binder binder = Binder.get(environment);
        OtlpProperties otlp = binder.bind("nova.observability.otlp", Bindable.of(OtlpProperties.class))
                .orElseGet(OtlpProperties::new);
        TracesProperties traces = binder.bind("nova.observability.traces", Bindable.of(TracesProperties.class))
                .orElseGet(TracesProperties::new);

        Map<String, Object> otel = new LinkedHashMap<>();
        long timeoutMs = otlp.getTimeout().toMillis();
        otel.put("otel.exporter.otlp.protocol", otlp.getProtocol());
        otel.put("otel.exporter.otlp.timeout", String.valueOf(timeoutMs));
        otel.put("otel.traces.sampler", "parentbased_traceidratio");
        otel.put("otel.traces.sampler.arg", String.valueOf(traces.getSamplingRatio()));

        Optional<String> endpoint = OtlpEndpointResolver.resolve(environment);
        if (endpoint.isPresent()) {
            if (StringUtils.hasText(otlp.getEndpoint())) {
                otel.put(OtlpEndpointResolver.STANDARD_PROPERTY, otlp.getEndpoint());
            }
            if (log.isDebugEnabled()) {
                log.debug("Exportación OTLP configurada — endpoint: " + endpoint.get()
                        + ", protocolo: " + otlp.getProtocol() + ", timeout: " + timeoutMs + "ms");
            }
        } else {
            turnExportersOff(environment, otel);
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, otel));
    }

    /**
     * Apaga el exportador de cada señal que el operador no eligió. Lo que él definió gana por la
     * prioridad de la fuente; aquí solo se mira para no decir que está apagado lo que él encendió.
     */
    private void turnExportersOff(ConfigurableEnvironment environment, Map<String, Object> otel) {
        List<String> turnedOff = new ArrayList<>();
        for (Signal signal : SIGNALS) {
            if (!environment.containsProperty(signal.exporterProperty())) {
                otel.put(signal.exporterProperty(), "none");
                turnedOff.add(signal.label());
            }
        }
        if (!turnedOff.isEmpty()) {
            log.info("Exportación OTLP apagada para " + enumerate(turnedOff) + ": no hay un endpoint configurado. "
                    + "Para exportar, hay que definir " + OtlpEndpointResolver.STARTER_PROPERTY
                    + " u OTEL_EXPORTER_OTLP_ENDPOINT.");
        }
    }

    /** Escribe una lista como en una frase: {@code a}, {@code a y b}, {@code a, b y c}. */
    private static String enumerate(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    /** Una señal de OpenTelemetry: cómo se llama en los mensajes y qué propiedad elige su exportador. */
    private record Signal(String label, String exporterProperty) {
    }
}
