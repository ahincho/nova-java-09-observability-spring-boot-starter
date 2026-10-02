package pe.edu.nova.java.starters.observability;

import org.apache.commons.logging.Log;

import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Un {@link Log} de Commons Logging que anota cada línea que recibe, para que una prueba pueda
 * comprobar qué dijo un componente que loguea antes de que exista el sistema de logging.
 */
public final class RecordingLog {

    private RecordingLog() {
    }

    /**
     * Crea un log que anota cada línea como {@code nivel: mensaje}, por ejemplo
     * {@code info: Exportación OTLP apagada}. Todos los niveles se dan por habilitados.
     *
     * @param lines la lista donde se anotan las líneas
     * @return el log
     */
    public static Log into(List<String> lines) {
        return (Log) Proxy.newProxyInstance(
                Log.class.getClassLoader(),
                new Class<?>[] {Log.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("is") && name.endsWith("Enabled")) {
                        return true;
                    }
                    if (args != null && args.length > 0) {
                        lines.add(name + ": " + args[0]);
                    }
                    return null;
                });
    }
}
