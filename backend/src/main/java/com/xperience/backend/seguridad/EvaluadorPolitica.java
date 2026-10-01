package com.xperience.backend.seguridad;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

public interface EvaluadorPolitica {

	/** Nivel de función (8.2.1): ¿el rol del sujeto tiene algún permiso para la acción? Lo usa el PEP. */
	boolean puedeInvocar(Sujeto sujeto, String accion);

	/** Nivel de dato (8.2.2): ¿puede ejecutar la acción sobre esta instancia? */
	boolean autorizar(Sujeto sujeto, String accion, Object recurso);

	/** Condición que se agrega a toda consulta sobre el tipo de recurso (R11, R15). */
	<T> Specification<T> filtrar(Sujeto sujeto, String accion, Class<T> tipo);

	/** Campos que el sujeto puede leer o escribir (R2, R12). */
	Set<String> camposLegibles(Sujeto sujeto, String accion, Object recurso);

	Set<String> camposEscribibles(Sujeto sujeto, String accion, Object recurso);

}
