package uy.edu.um.xperience.course;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Archivos en un directorio privado que el navegador no alcanza: sólo se entregan a través de un
 * endpoint del backend (R14). Los nombres en disco son UUID generados acá; ningún dato del usuario
 * participa de la ruta.
 */
@Component
public class FileStorage {
    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);
    private final Path base;

    public FileStorage(@Value("${app.storage.dir:./.data/archivos}") String dir) throws IOException {
        this.base = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(base);
    }

    public String save(InputStream content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.copy(content, path(key));
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo guardar el archivo", error);
        }
        return key;
    }

    public Resource open(String key) { return new FileSystemResource(path(key)); }

    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException error) {
            // Sin registro en la base nada apunta al archivo, así que queda inaccesible.
            log.warn("No se pudo eliminar un archivo del almacenamiento");
        }
    }

    private Path path(String key) {
        UUID.fromString(key); // sólo claves generadas por este componente
        return base.resolve(key);
    }
}
