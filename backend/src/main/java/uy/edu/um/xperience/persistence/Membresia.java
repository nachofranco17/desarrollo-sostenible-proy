package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "membresia")
public class Membresia {
    @Id @Column(name = "usuario_id") public UUID usuarioId;
    @Column(name = "empresa_id", nullable = false) public UUID empresaId;
    @Column(length = 20) public String rol;
    @Column(nullable = false, length = 10) public String estado;
}
