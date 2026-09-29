package uy.edu.um.xperience.contenido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Archivo asociado a un curso o proyecto. El nombre en disco es un UUID que nunca se expone; el
 * nombre original sólo se usa para la descarga.
 */
@Entity
@Table(name = "material")
public class Material {

    @Id
    private UUID id;

    @Column(name = "contenido_id", nullable = false, updatable = false)
    private UUID contenidoId;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    /** Detectado a partir del contenido del archivo, no del que declara el cliente. */
    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamanio_bytes", nullable = false)
    private long tamanioBytes;

    @Column(name = "clave_almacen", nullable = false, unique = true, length = 64)
    private String claveAlmacen;

    @Column(name = "subido_por", nullable = false)
    private UUID subidoPor;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn;

    protected Material() {
    }

    public Material(UUID contenidoId, String nombreOriginal, String tipoMime, long tamanioBytes,
                    String claveAlmacen, UUID subidoPor) {
        this.id = UUID.randomUUID();
        this.contenidoId = contenidoId;
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.tamanioBytes = tamanioBytes;
        this.claveAlmacen = claveAlmacen;
        this.subidoPor = subidoPor;
        this.subidoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getContenidoId() {
        return contenidoId;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamanioBytes() {
        return tamanioBytes;
    }

    public String getClaveAlmacen() {
        return claveAlmacen;
    }

    public Instant getSubidoEn() {
        return subidoEn;
    }
}
