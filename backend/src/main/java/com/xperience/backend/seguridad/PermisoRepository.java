package com.xperience.backend.seguridad;

import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface PermisoRepository extends Repository<Permiso, UUID> {

	boolean existsByRolAndAccion(Rol rol, String accion);

}
