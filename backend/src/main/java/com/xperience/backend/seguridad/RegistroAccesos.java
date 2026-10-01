package com.xperience.backend.seguridad;

public interface RegistroAccesos {

	/** Se informa toda denegación, sea del PEP o de un handler (lo implementa R18). */
	void denegado(Sujeto sujeto, String accion, String recurso);

}
