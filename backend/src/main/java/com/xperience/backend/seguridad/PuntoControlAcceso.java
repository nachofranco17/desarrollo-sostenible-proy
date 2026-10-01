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
			throw denegar(request, sujeto, null);
		}
		RequiereAccion requiere = metodo.getMethodAnnotation(RequiereAccion.class);
		if (requiere == null) {
			throw denegar(request, sujeto, null);
		}
		if (!evaluador.puedeInvocar(sujeto, requiere.value())) {
			throw denegar(request, sujeto, requiere.value());
		}
		return true;
	}

	private AccesoDenegadoException denegar(HttpServletRequest request, Sujeto sujeto, String accion) {
		registroAccesos.denegado(sujeto, accion, request.getMethod() + " " + request.getRequestURI());
		return new AccesoDenegadoException();
	}

}
