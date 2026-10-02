package pe.edu.nova.java.starters.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un OpenTelemetry Collector de mentira: un servidor HTTP en el loopback, en un puerto libre, que
 * anota lo que recibe. Contesta como uno real: {@code 200} a lo que le exporta OTLP y {@code 404}
 * a un {@code GET} cualquiera, que es lo que el indicador de salud considera «arriba».
 */
public final class StubCollector implements AutoCloseable {

    /**
     * Una solicitud que llegó al Collector.
     *
     * @param method      el método HTTP
     * @param path        la ruta, por ejemplo {@code /v1/traces}
     * @param contentType el {@code Content-Type}, o {@code null} si no trajo
     * @param bodyLength  los bytes del cuerpo
     */
    public record Request(String method, String path, String contentType, int bodyLength) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int healthStatus = 404;

    /** Levanta el servidor en un puerto libre del loopback. */
    public StubCollector() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * La dirección del Collector, tal como se escribe en {@code nova.observability.otlp.endpoint}.
     *
     * @return {@code http://127.0.0.1:<puerto>}
     */
    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Cambia el código con el que contesta a un {@code GET}, para probar un Collector que falla.
     *
     * @param status el código HTTP
     */
    public void answerGetWith(int status) {
        this.healthStatus = status;
    }

    /**
     * Lo que le llegó hasta ahora.
     *
     * @return una copia de las solicitudes, en orden de llegada
     */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String method = exchange.getRequestMethod();
            requests.add(new Request(
                    method,
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body.length));
            exchange.sendResponseHeaders("GET".equals(method) ? healthStatus : 200, -1);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
