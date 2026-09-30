package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "alta_empresa")
public class AltaEmpresa {
    @Id public UUID id;
    @Column(name = "empresa_id", nullable = false) public UUID empresaId;
    @Column(name = "administrador_id", nullable = false) public UUID administradorId;
    @Column(nullable = false, length = 160) public String ejecutor;
}
