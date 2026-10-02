package com.xperience.backend.seguridad;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.xperience.backend.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PuntoControlAccesoTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cargarPermisos() {
		jdbc.update("INSERT INTO permiso (rol, accion, alcance) VALUES ('VISITANTE', 'prueba.permitida', 'GLOBAL')");
	}

	@Test
	void accionPermitidaEjecutaElHandler() throws Exception {
		mockMvc.perform(get("/api/prueba/permitida"))
				.andExpect(status().isOk())
				.andExpect(content().string("ejecutado"));
	}

	@Test
	void accionSinPermisoDevuelve403GenericoSinEjecutarElHandler() throws Exception {
		mockMvc.perform(get("/api/prueba/denegada"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.title").value("Forbidden"))
				.andExpect(jsonPath("$.detail").doesNotExist())
				.andExpect(content().string(not(containsString("ejecutado"))))
				.andExpect(content().string(not(containsString("prueba.denegada"))))
				.andExpect(content().string(not(containsString("VISITANTE"))));
	}

	@Test
	void endpointSinAnotacionDevuelve403() throws Exception {
		mockMvc.perform(get("/api/prueba/sin-anotacion"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(not(containsString("ejecutado"))));
	}

	@Test
	void denegacionDelHandlerDevuelveElMismo403Generico() throws Exception {
		mockMvc.perform(get("/api/prueba/denegada-por-handler"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.title").value("Forbidden"))
				.andExpect(jsonPath("$.detail").doesNotExist());
	}

	@Test
	void rutaFueraDeApiDevuelve403AunqueTengaPermiso() throws Exception {
		mockMvc.perform(get("/fuera-de-api/prueba"))
				.andExpect(status().isForbidden());
	}

	@Test
	void errorPedidoDirectoDevuelve403() throws Exception {
		mockMvc.perform(get("/error"))
				.andExpect(status().isForbidden());
	}

	@Test
	void postSinTokenCsrfDevuelve403() throws Exception {
		mockMvc.perform(post("/api/prueba/permitida"))
				.andExpect(status().isForbidden());
	}

	@Test
	void postConTokenCsrfEjecutaElHandler() throws Exception {
		mockMvc.perform(post("/api/prueba/permitida").with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string("ejecutado"));
	}

}
