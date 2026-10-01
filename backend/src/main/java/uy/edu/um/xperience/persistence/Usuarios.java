package uy.edu.um.xperience.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface Usuarios extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByCorreo(String correo);
}
