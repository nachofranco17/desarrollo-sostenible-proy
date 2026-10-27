package uy.edu.um.xperience.offer;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.security.Denegaciones;

/** Ninguna ruta de ofertas admite filtros ni atributos de autorización en la query. */
final class OfferQuery {
    private OfferQuery() {}

    static void requireEmpty(Map<String, String[]> parameters) {
        if (!parameters.isEmpty()) {
            Denegaciones.marcarCampoNoAutorizado(parameters.keySet().stream().sorted().findFirst().orElse(null));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud contiene parámetros no permitidos.");
        }
    }
}
