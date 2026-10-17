package uy.edu.um.xperience.course;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tipos de material admitidos. Se detectan por la firma de los primeros bytes, no por la extensión
 * ni por el Content-Type que declara el cliente.
 */
enum FileType {
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}, 0, "pdf"),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, "png"),
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0, "jpg", "jpeg"),
    ZIP("application/zip", new byte[]{'P', 'K', 0x03, 0x04}, 0, "zip"),
    MP4("video/mp4", new byte[]{'f', 't', 'y', 'p'}, 4, "mp4");

    static final int HEADER_BYTES = 12;

    private final String mime;
    private final byte[] signature;
    private final int offset;
    private final List<String> extensions;

    FileType(String mime, byte[] signature, int offset, String... extensions) {
        this.mime = mime;
        this.signature = signature;
        this.offset = offset;
        this.extensions = List.of(extensions);
    }

    String mime() { return mime; }

    /**
     * Extensión a agregar para que el nombre servido coincida con el tipo detectado (ASVS 5.4.1):
     * vacía si ya termina en una propia, nunca la que eligió el cliente (p. ej. un PDF subido como .html).
     */
    String missingExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(e -> lower.endsWith("." + e)) ? "" : "." + extensions.get(0);
    }

    static Optional<FileType> detect(byte[] header) {
        return Arrays.stream(values()).filter(t -> t.matches(header)).findFirst();
    }

    private boolean matches(byte[] header) {
        return header.length >= offset + signature.length
            && Arrays.equals(header, offset, offset + signature.length, signature, 0, signature.length);
    }
}
