package uy.edu.um.xperience.persistence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AltasEmpresa extends JpaRepository<AltaEmpresa, UUID> {}
