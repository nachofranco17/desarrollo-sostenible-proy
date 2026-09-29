package uy.edu.um.xperience.contenido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByContenidoIdOrderBySubidoEnAsc(UUID contenidoId);

    Optional<Material> findByIdAndContenidoId(UUID id, UUID contenidoId);

    long countByContenidoId(UUID contenidoId);
}
