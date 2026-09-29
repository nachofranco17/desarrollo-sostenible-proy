package uy.edu.um.xperience.contenido;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** La gestión de una empresa trabaja sólo sobre sus propios cursos y proyectos. */
public interface ContenidoRepository extends JpaRepository<Contenido, UUID> {

    List<Contenido> findByEmpresaIdOrderByActualizadoEnDesc(UUID empresaId);

    Optional<Contenido> findByIdAndEmpresaId(UUID id, UUID empresaId);
}
