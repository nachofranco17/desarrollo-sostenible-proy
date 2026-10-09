package uy.edu.um.xperience.persistence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface Permisos extends JpaRepository<Permiso, Permiso.Key> {
    // Always query current grants; no TTL cache or session snapshot (RS16).
    List<Permiso> findByRolAndAccion(String rol, String accion);
}
