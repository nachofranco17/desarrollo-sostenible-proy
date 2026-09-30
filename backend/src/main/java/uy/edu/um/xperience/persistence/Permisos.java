package uy.edu.um.xperience.persistence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface Permisos extends JpaRepository<Permiso, Permiso.Key> {
    List<Permiso> findByRolAndAccion(String rol, String accion);
}
