package uy.edu.um.xperience.persistence;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface Membresias extends JpaRepository<Membresia, UUID>, JpaSpecificationExecutor<Membresia> {}
