package com.xperience.backend.seguridad;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
class ResolutorSujeto {

	private static final Sujeto VISITANTE = new Sujeto(null, TipoCuenta.VISITANTE, null, Rol.VISITANTE);

	Sujeto resolver(HttpServletRequest request) {
		return VISITANTE;
	}

}
