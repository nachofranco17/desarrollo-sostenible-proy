package com.xperience.backend.seguridad;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
class EvaluadorPoliticaImpl implements EvaluadorPolitica {

	private final PermisoRepository permisoRepository;

	EvaluadorPoliticaImpl(PermisoRepository permisoRepository) {
		this.permisoRepository = permisoRepository;
	}

	@Override
	public boolean puedeInvocar(Sujeto sujeto, String accion) {
		if (sujeto == null || sujeto.rol() == null || accion == null) {
			return false;
		}
		return permisoRepository.existsByRolAndAccion(sujeto.rol(), accion);
	}

	@Override
	public boolean autorizar(Sujeto sujeto, String accion, Object recurso) {
		return false;
	}

	@Override
	public <T> Specification<T> filtrar(Sujeto sujeto, String accion, Class<T> tipo) {
		return (root, query, cb) -> cb.disjunction();
	}

	@Override
	public Set<String> camposLegibles(Sujeto sujeto, String accion, Object recurso) {
		return Set.of();
	}

	@Override
	public Set<String> camposEscribibles(Sujeto sujeto, String accion, Object recurso) {
		return Set.of();
	}

}
