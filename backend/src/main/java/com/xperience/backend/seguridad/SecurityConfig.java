package com.xperience.backend.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(reglas -> reglas
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/api/**").permitAll()
				.anyRequest().denyAll());
		http.requestCache(cache -> cache.disable());
		return http.build();
	}

}
