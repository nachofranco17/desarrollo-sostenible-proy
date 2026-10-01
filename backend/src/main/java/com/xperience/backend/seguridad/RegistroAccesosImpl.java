package com.xperience.backend.seguridad;

import org.springframework.stereotype.Component;

@Component
class RegistroAccesosImpl implements RegistroAccesos {

	@Override
	public void denegado(Sujeto sujeto, String accion, String recurso) {
	}

}
