package com.xperience.backend.seguridad;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

	private final PuntoControlAcceso puntoControlAcceso;

	WebConfig(PuntoControlAcceso puntoControlAcceso) {
		this.puntoControlAcceso = puntoControlAcceso;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(puntoControlAcceso);
	}

}
