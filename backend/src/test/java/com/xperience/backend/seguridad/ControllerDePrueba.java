package com.xperience.backend.seguridad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ControllerDePrueba {

	@GetMapping("/api/prueba/permitida")
	@RequiereAccion("prueba.permitida")
	String permitida() {
		return "ejecutado";
	}

	@PostMapping("/api/prueba/permitida")
	@RequiereAccion("prueba.permitida")
	String permitidaPost() {
		return "ejecutado";
	}

	@GetMapping("/api/prueba/denegada")
	@RequiereAccion("prueba.denegada")
	String denegada() {
		return "ejecutado";
	}

	@GetMapping("/api/prueba/sin-anotacion")
	String sinAnotacion() {
		return "ejecutado";
	}

	@GetMapping("/api/prueba/denegada-por-handler")
	@RequiereAccion("prueba.permitida")
	String denegadaPorHandler() {
		throw new AccesoDenegadoException();
	}

	@GetMapping("/fuera-de-api/prueba")
	@RequiereAccion("prueba.permitida")
	String fueraDeApi() {
		return "ejecutado";
	}

}
