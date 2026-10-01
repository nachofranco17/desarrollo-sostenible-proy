package com.xperience.backend.seguridad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ManejadorAccesoDenegado {

	@ExceptionHandler(AccesoDenegadoException.class)
	ProblemDetail manejar() {
		return ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
	}

}
