package uy.edu.um.xperience.persistence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface Empresas extends JpaRepository<Empresa, UUID>, JpaSpecificationExecutor<Empresa> {}
