package uy.edu.um.xperience.persistence;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface Membresias extends JpaRepository<Membresia, UUID>, JpaSpecificationExecutor<Membresia> {
    @Override
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Membresia> findOne(org.springframework.data.jpa.domain.Specification<Membresia> filter);
}
