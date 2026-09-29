package uy.edu.um.xperience.contenido;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de material admitidos. Se detectan por la firma de los primeros bytes y no por la extensión
 * ni por el Content-Type que declara el cliente.
 */
enum TipoArchivo {
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}, 0),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0),
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0),
    ZIP("application/zip", new byte[]{'P', 'K', 0x03, 0x04}, 0),
    MP4("video/mp4", new byte[]{'f', 't', 'y', 'p'}, 4);

    static final int BYTES_CABECERA = 12;

    private final String mime;
    private final byte[] firma;
    private final int desplazamiento;

    TipoArchivo(String mime, byte[] firma, int desplazamiento) {
        this.mime = mime;
        this.firma = firma;
        this.desplazamiento = desplazamiento;
    }

    String mime() {
        return mime;
    }

    static Optional<TipoArchivo> detectar(byte[] cabecera) {
        return Arrays.stream(values()).filter(t -> t.coincide(cabecera)).findFirst();
    }

    private boolean coincide(byte[] cabecera) {
        if (cabecera.length < desplazamiento + firma.length) {
            return false;
        }
        return Arrays.equals(cabecera, desplazamiento, desplazamiento + firma.length, firma, 0, firma.length);
    }
}
