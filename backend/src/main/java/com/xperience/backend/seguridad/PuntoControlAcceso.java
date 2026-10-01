package com.xperience.backend.seguridad;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class PuntoControlAcceso implements HandlerInterceptor {

	private final EvaluadorPolitica evaluador;
	private final ResolutorSujeto resolutorSujeto;

	PuntoControlAcceso(EvaluadorPolitica evaluador, ResolutorSujeto resolutorSujeto) {
		this.evaluador = evaluador;
		this.resolutorSujeto = resolutorSujeto;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (request.getDispatcherType() == DispatcherType.ERROR) {
			return true;
		}
		if (!(handler instanceof HandlerMethod metodo)) {
			return denegar(response);
		}
		RequiereAccion requiere = metodo.getMethodAnnotation(RequiereAccion.class);
		
		if (requiere == null) {
			return denegar(response);
		}
		Sujeto sujeto = resolutorSujeto.resolver(request);
		if (!evaluador.puedeInvocar(sujeto, requiere.value())) {
			return denegar(response);
		}
		return true;
	}

	private boolean denegar(HttpServletResponse response) throws Exception {
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
		return false;
	}

}
