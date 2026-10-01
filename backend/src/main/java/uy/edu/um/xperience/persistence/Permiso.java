package uy.edu.um.xperience.persistence;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name = "permiso") @IdClass(Permiso.Key.class)
public class Permiso {
    @Id @Column(length = 20) public String rol;
    @Id @Column(length = 80) public String accion;
    @Column(nullable = false, length = 20) public String alcance;
    public static class Key implements Serializable {
        public String rol;
        public String accion;
        public Key() {}
        @Override public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(rol, k.rol) && Objects.equals(accion, k.accion);
        }
        @Override public int hashCode() { return Objects.hash(rol, accion); }
    }
}
