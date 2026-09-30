package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name = "usuario")
public class Usuario {
    @Id public UUID id;
    @Column(nullable = false, unique = true, length = 254) public String correo;
    @Column(nullable = false, length = 80) public String nombre;
    @Column(nullable = false, length = 80) public String apellido;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(name = "tipo_cuenta", nullable = false, length = 10) public String tipoCuenta;
    @Column(nullable = false) public boolean activo = true;
}
