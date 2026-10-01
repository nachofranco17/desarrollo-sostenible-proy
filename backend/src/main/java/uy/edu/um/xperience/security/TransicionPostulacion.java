package uy.edu.um.xperience.security;
import java.util.UUID;
/** Attributes to be loaded by the application module, not supplied as authorization claims by a client. */
public record TransicionPostulacion(UUID empresaId, String desde, String hacia) {
    public boolean permitida() {
        return ("enviada".equals(desde) && ("preseleccionada".equals(hacia) || "descartada".equals(hacia)))
            || ("preseleccionada".equals(desde) && ("seleccionada".equals(hacia) || "descartada".equals(hacia)));
    }
}
