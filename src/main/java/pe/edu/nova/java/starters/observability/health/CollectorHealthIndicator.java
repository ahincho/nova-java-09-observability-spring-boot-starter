package pe.edu.nova.java.starters.observability.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.util.Assert;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Indicador de salud que verifica la conectividad con el OpenTelemetry Collector.
 *
 * <p>Solo existe cuando el servicio tiene un endpoint OTLP configurado: sin endpoint no hay
 * Collector que verificar y el starter no lo registra, así que un servicio sin Collector no
 * reporta {@code DOWN} por eso. Verifica el endpoint efectivo, el mismo al que exporta
 * OpenTelemetry (ver {@link pe.edu.nova.java.starters.observability.config.OtlpEndpointResolver}).</p>
 *
 * <p>Reporta {@code UP} si el Collector responde con status &lt; 500,
 * {@code DOWN} si no está disponible o responde con error del servidor.</p>
 *
 * <p>Usa {@link HttpClient} de Java con timeout de conexión de 3 segundos
 * y timeout de respuesta de 5 segundos.</p>
 */
public class CollectorHealthIndicator extends AbstractHealthIndicator {

    private final String endpoint;
    private final HttpClient httpClient;

    /**
     * Crea una nueva instancia del indicador de salud del Collector.
     *
     * @param endpoint endpoint OTLP del Collector; no puede estar vacío
     */
    public CollectorHealthIndicator(String endpoint) {
        super("No se puede conectar con el OpenTelemetry Collector");
        Assert.hasText(endpoint, "El endpoint del OpenTelemetry Collector no puede estar vacío");
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Ejecuta la verificación de salud contra el endpoint del Collector.
     *
     * @param builder el builder de Health para construir el resultado
     */
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 500) {
                builder.up()
                        .withDetail("endpoint", endpoint)
                        .withDetail("statusCode", response.statusCode());
            } else {
                builder.down()
                        .withDetail("endpoint", endpoint)
                        .withDetail("statusCode", response.statusCode());
            }
        } catch (Exception e) {
            builder.down()
                    .withDetail("endpoint", endpoint)
                    .withDetail("error", e.getMessage());
        }
    }
}
