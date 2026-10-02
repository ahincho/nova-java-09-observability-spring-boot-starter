package pe.edu.nova.java.starters.observability.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Propiedades de configuración del starter de observabilidad.
 * Mapeadas bajo el prefijo {@code nova.observability}.
 *
 * <p>Permite configurar todos los aspectos de la observabilidad:
 * exportación OTLP, métricas, trazas, logs y filtro HTTP.</p>
 */
@Validated
@ConfigurationProperties(prefix = "nova.observability")
public class ObservabilityProperties {

    /** Activa/desactiva toda la observabilidad. Por defecto: {@code true}. */
    private boolean enabled = true;

    /** Configuración de exportación OTLP. */
    @Valid
    private OtlpProperties otlp = new OtlpProperties();

    /** Configuración de métricas. */
    @Valid
    private MetricsProperties metrics = new MetricsProperties();

    /** Configuración de trazas distribuidas. */
    @Valid
    private TracesProperties traces = new TracesProperties();

    /** Configuración de correlación de logs. */
    @Valid
    private LogsProperties logs = new LogsProperties();

    /** Configuración del filtro HTTP. */
    @Valid
    private FilterProperties filter = new FilterProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OtlpProperties getOtlp() {
        return otlp;
    }

    public void setOtlp(OtlpProperties otlp) {
        this.otlp = otlp;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public TracesProperties getTraces() {
        return traces;
    }

    public void setTraces(TracesProperties traces) {
        this.traces = traces;
    }

    public LogsProperties getLogs() {
        return logs;
    }

    public void setLogs(LogsProperties logs) {
        this.logs = logs;
    }

    public FilterProperties getFilter() {
        return filter;
    }

    public void setFilter(FilterProperties filter) {
        this.filter = filter;
    }

    /**
     * Propiedades de configuración del exportador OTLP.
     */
    public static class OtlpProperties {

        /**
         * Endpoint del OpenTelemetry Collector. No tiene valor por defecto: si no se define ni esta
         * propiedad ni otel.exporter.otlp.endpoint, la exportación OTLP queda apagada.
         */
        private String endpoint;

        /** Protocolo de exportación OTLP. Por defecto: {@code http/protobuf}. */
        private String protocol = "http/protobuf";

        /** Timeout de exportación. Por defecto: 10 segundos. */
        private Duration timeout = Duration.ofSeconds(10);

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Propiedades de configuración de métricas.
     */
    public static class MetricsProperties {

        /** Activa/desactiva las métricas. Por defecto: {@code true}. */
        private boolean enabled = true;

        /** Configuración de Four Golden Signals. */
        @Valid
        private GoldenSignalsProperties goldenSignals = new GoldenSignalsProperties();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public GoldenSignalsProperties getGoldenSignals() {
            return goldenSignals;
        }

        public void setGoldenSignals(GoldenSignalsProperties goldenSignals) {
            this.goldenSignals = goldenSignals;
        }
    }

    /**
     * Propiedades de configuración de Four Golden Signals.
     */
    public static class GoldenSignalsProperties {

        /** Activa/desactiva el registro de Four Golden Signals. Por defecto: {@code true}. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Propiedades de configuración de trazas distribuidas.
     */
    public static class TracesProperties {

        /** Activa/desactiva las trazas. Por defecto: {@code true}. */
        private boolean enabled = true;

        /** Ratio de sampling (0.0 a 1.0). Por defecto: {@code 1.0} (todas las trazas). */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double samplingRatio = 1.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getSamplingRatio() {
            return samplingRatio;
        }

        public void setSamplingRatio(double samplingRatio) {
            this.samplingRatio = samplingRatio;
        }
    }

    /**
     * Propiedades de configuración de correlación de logs.
     */
    public static class LogsProperties {

        /** Activa/desactiva la correlación de logs. Por defecto: {@code true}. */
        private boolean enabled = true;

        /** Exportar logs al Collector vía OTLP. Por defecto: {@code true}. */
        private boolean exportToOtlp = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isExportToOtlp() {
            return exportToOtlp;
        }

        public void setExportToOtlp(boolean exportToOtlp) {
            this.exportToOtlp = exportToOtlp;
        }
    }

    /**
     * Propiedades de configuración del filtro HTTP de métricas.
     */
    public static class FilterProperties {

        /** Rutas excluidas del registro de métricas. */
        private List<String> excludePaths = new ArrayList<>(List.of(
                "/actuator", "/swagger-ui", "/v3/api-docs"));

        /** Normalizar IDs en URIs usando path patterns de Spring MVC. Por defecto: {@code true}. */
        private boolean normalizeIds = true;

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }

        public boolean isNormalizeIds() {
            return normalizeIds;
        }

        public void setNormalizeIds(boolean normalizeIds) {
            this.normalizeIds = normalizeIds;
        }
    }
}
