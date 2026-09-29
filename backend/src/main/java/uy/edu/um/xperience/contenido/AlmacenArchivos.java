package uy.edu.um.xperience.contenido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Almacenamiento de archivos en disco. Los nombres son UUID generados acá, así que ningún dato del
 * usuario participa de la ruta; el nombre original queda sólo en la base.
 */
@Component
public class AlmacenArchivos {

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenArchivos.class);

    private final Path base;

    public AlmacenArchivos(@Value("${app.almacenamiento.directorio}") String directorio) throws IOException {
        this.base = Path.of(directorio).toAbsolutePath().normalize();
        Files.createDirectories(base);
    }

    /** Guarda el contenido y devuelve la clave con la que se recupera. */
    public String guardar(InputStream contenido) {
        String clave = UUID.randomUUID().toString();
        try {
            Files.copy(contenido, ruta(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
        return clave;
    }

    public Resource abrir(String clave) {
        return new FileSystemResource(ruta(clave));
    }

    public void eliminar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            // El registro ya no existe; un archivo huérfano no es accesible porque nada apunta a él.
            LOG.warn("No se pudo eliminar el archivo {}", clave, e);
        }
    }

    private Path ruta(String clave) {
        UUID.fromString(clave); // rechaza cualquier cosa que no sea una clave generada por este componente
        Path ruta = base.resolve(clave).normalize();
        if (!ruta.getParent().equals(base)) {
            throw new IllegalArgumentException("Clave de almacenamiento inválida");
        }
        return ruta;
    }
}
