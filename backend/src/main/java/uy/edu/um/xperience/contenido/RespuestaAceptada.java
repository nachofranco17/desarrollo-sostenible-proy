package uy.edu.um.xperience.contenido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Hash de una respuesta aceptada normalizada. Ver {@link HasherRespuestas}. */
@Entity
@Table(name = "respuesta_aceptada")
class RespuestaAceptada {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entregable_id", nullable = false, updatable = false)
    private Entregable entregable;

    @Column(nullable = false, length = 64)
    private String hash;

    protected RespuestaAceptada() {
    }

    RespuestaAceptada(Entregable entregable, String hash) {
        this.id = UUID.randomUUID();
        this.entregable = entregable;
        this.hash = hash;
    }
}
