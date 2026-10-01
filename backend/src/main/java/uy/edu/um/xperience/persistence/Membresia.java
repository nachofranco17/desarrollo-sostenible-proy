package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name = "membresia")
public class Membresia {
    @Id @Column(name = "usuario_id") public UUID usuarioId;
    @Column(name = "empresa_id", nullable = false) public UUID empresaId;
    @Column(length = 20) public String rol;
    @Column(nullable = false, length = 10) public String estado;
    @Column(name = "invitacion_hash", length = 64) public String invitacionHash;
    @Column(name = "invitacion_expira") public Instant invitacionExpira;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false) public Usuario usuario;
}
