package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "empresa")
public class Empresa {
    @Id public UUID id;
    @Column(nullable = false, length = 160) public String nombre;
}
