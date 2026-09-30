package uy.edu.um.xperience.course;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de material admitidos. Se detectan por la firma de los primeros bytes, no por la extensión
 * ni por el Content-Type que declara el cliente.
 */
enum FileType {
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}, 0),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0),
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0),
    ZIP("application/zip", new byte[]{'P', 'K', 0x03, 0x04}, 0),
    MP4("video/mp4", new byte[]{'f', 't', 'y', 'p'}, 4);

    static final int HEADER_BYTES = 12;

    private final String mime;
    private final byte[] signature;
    private final int offset;

    FileType(String mime, byte[] signature, int offset) {
        this.mime = mime;
        this.signature = signature;
        this.offset = offset;
    }

    String mime() { return mime; }

    static Optional<FileType> detect(byte[] header) {
        return Arrays.stream(values()).filter(t -> t.matches(header)).findFirst();
    }

    private boolean matches(byte[] header) {
        return header.length >= offset + signature.length
            && Arrays.equals(header, offset, offset + signature.length, signature, 0, signature.length);
    }
}
