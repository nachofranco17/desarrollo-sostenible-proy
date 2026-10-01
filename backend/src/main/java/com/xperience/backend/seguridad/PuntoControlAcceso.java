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
	private final RegistroAccesos registroAccesos;

	PuntoControlAcceso(EvaluadorPolitica evaluador, ResolutorSujeto resolutorSujeto, RegistroAccesos registroAccesos) {
		this.evaluador = evaluador;
		this.resolutorSujeto = resolutorSujeto;
		this.registroAccesos = registroAccesos;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (request.getDispatcherType() == DispatcherType.ERROR) {
			return true;
		}
		Sujeto sujeto = resolutorSujeto.resolver(request);
		if (!(handler instanceof HandlerMethod metodo)) {
			return denegar(request, response, sujeto, null);
		}
		RequiereAccion requiere = metodo.getMethodAnnotation(RequiereAccion.class);
		if (requiere == null) {
			return denegar(request, response, sujeto, null);
		}
		if (!evaluador.puedeInvocar(sujeto, requiere.value())) {
			return denegar(request, response, sujeto, requiere.value());
		}
		return true;
	}

	private boolean denegar(HttpServletRequest request, HttpServletResponse response, Sujeto sujeto, String accion)
		throws Exception {
		registroAccesos.denegado(sujeto, accion, request.getMethod() + " " + request.getRequestURI());
		response.sendError(HttpServletResponse.SC_FORBIDDEN);
		return false;
	}

}
