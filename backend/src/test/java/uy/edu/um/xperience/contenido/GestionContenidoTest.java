package uy.edu.um.xperience.contenido;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import uy.edu.um.xperience.seguridad.Consumidor;
import uy.edu.um.xperience.seguridad.Rol;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF5 y R9. Como el inicio de sesión (RF1) todavía no existe, cada solicitud se autentica
 * directamente con el {@link Consumidor} que RF1 deberá dejar en el contexto de seguridad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestionContenidoTest {

    private static final String BASE = "/api/gestion/contenidos";
    private static final byte[] PDF = "%PDF-1.4\n% prueba\n".getBytes(StandardCharsets.US_ASCII);

    private static final UUID ACME = UUID.randomUUID();
    private static final UUID GLOBEX = UUID.randomUUID();
    private static final Consumidor ADMIN = new Consumidor(UUID.randomUUID(), Rol.EMPRESA_ADMIN, ACME);
    private static final Consumidor EDITOR = new Consumidor(UUID.randomUUID(), Rol.EMPRESA_EDITOR, ACME);
    private static final Consumidor RECLUTADOR = new Consumidor(UUID.randomUUID(), Rol.EMPRESA_RECLUTADOR, ACME);
    private static final Consumidor SIN_ROL = new Consumidor(UUID.randomUUID(), null, ACME);
    private static final Consumidor TALENTO = new Consumidor(UUID.randomUUID(), Rol.TALENTO, null);
    private static final Consumidor EDITOR_GLOBEX = new Consumidor(UUID.randomUUID(), Rol.EMPRESA_EDITOR, GLOBEX);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        for (String tabla : List.of("respuesta_aceptada", "entregable", "leccion", "material", "contenido")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    // ---- RF5: funcionalidad ----

    @Nested
    class Gestion {

        @Test
        void elEditorCreaUnCursoEnBorradorParaSuEmpresa() throws Exception {
            UUID id = crear(EDITOR, curso("Java desde cero"));

            assertThat(jdbc.queryForMap("SELECT empresa_id, estado, creado_por FROM contenido WHERE id = ?", id))
                    .containsEntry("empresa_id", ACME)
                    .containsEntry("estado", "BORRADOR")
                    .containsEntry("creado_por", EDITOR.usuarioId());
            ejecutar(get(BASE), EDITOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void cadaEmpresaVeSoloSusCursosYProyectos() throws Exception {
            UUID deAcme = crear(EDITOR, curso("De Acme"));
            crear(EDITOR_GLOBEX, curso("De Globex"));

            ejecutar(get(BASE), EDITOR_GLOBEX)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].titulo").value("De Globex"));
            ejecutar(get(BASE + "/" + deAcme), EDITOR_GLOBEX).andExpect(status().isNotFound());
        }

        @Test
        void cicloDeVidaBorradorPublicadoBajado() throws Exception {
            UUID id = crear(EDITOR, curso("Docker"));

            ejecutar(post(BASE + "/" + id + "/publicar"), EDITOR)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONTENIDO_INCOMPLETO"));

            agregarLeccion(EDITOR, id);
            ejecutar(post(BASE + "/" + id + "/publicar"), EDITOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PUBLICADO"));

            // Publicado: ya no se borra, porque puede haber inscriptos.
            ejecutar(delete(BASE + "/" + id), EDITOR).andExpect(status().isConflict());

            ejecutar(post(BASE + "/" + id + "/bajar"), EDITOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("BAJADO"));

            ejecutar(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content(aJson(actualizacion("Otro título"))), EDITOR)
                    .andExpect(status().isConflict());
        }

        @Test
        void unBorradorSePuedeEliminar() throws Exception {
            UUID id = crear(EDITOR, curso("Borrable"));

            ejecutar(delete(BASE + "/" + id), EDITOR).andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contenido", Integer.class)).isZero();
        }

        @Test
        void lasLeccionesSeRenumeranAlEliminar() throws Exception {
            UUID id = crear(EDITOR, curso("Numeración"));
            agregarLeccion(EDITOR, id);
            agregarLeccion(EDITOR, id);
            String primera = agregarLeccion(EDITOR, id).get("lecciones").get(0).get("id").asText();

            ejecutar(delete(BASE + "/" + id + "/lecciones/" + primera), EDITOR)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lecciones.length()").value(2))
                    .andExpect(jsonPath("$.lecciones[0].numero").value(1))
                    .andExpect(jsonPath("$.lecciones[1].numero").value(2));
        }

        @Test
        void unCursoNoAdmiteEntregablesNiUnProyectoLecciones() throws Exception {
            UUID curso = crear(EDITOR, curso("Curso"));
            UUID proyecto = crear(EDITOR, proyecto("Proyecto"));

            ejecutar(post(BASE + "/" + curso + "/entregables").contentType(MediaType.APPLICATION_JSON)
                    .content(aJson(entregable("x"))), EDITOR).andExpect(status().isConflict());
            ejecutar(post(BASE + "/" + proyecto + "/lecciones").contentType(MediaType.APPLICATION_JSON)
                    .content(aJson(Map.of("titulo", "x", "cuerpo", "x"))), EDITOR).andExpect(status().isConflict());
        }

        @Test
        void lasRespuestasAceptadasSeGuardanHasheadasYNoSeDevuelven() throws Exception {
            UUID id = crear(EDITOR, proyecto("API REST"));
            String secreto = "respuesta-secreta-42";

            String alCrear = ejecutar(post(BASE + "/" + id + "/entregables").contentType(MediaType.APPLICATION_JSON)
                    .content(aJson(Map.of("titulo", "Endpoint", "consigna", "¿Qué devuelve GET /ping?",
                            "pista", "Mirá el controlador",
                            "respuestasAceptadas", List.of(secreto, "  RESPUESTA-secreta-42 ")))), EDITOR)
                    .andExpect(status().isOk())
                    // Normalizadas, las dos respuestas son la misma.
                    .andExpect(jsonPath("$.entregables[0].cantidadRespuestasAceptadas").value(1))
                    .andReturn().getResponse().getContentAsString();

            String hash = jdbc.queryForObject("SELECT hash FROM respuesta_aceptada", String.class);
            assertThat(hash).hasSize(64).isNotEqualTo(secreto);
            assertThat(alCrear).doesNotContain(secreto).doesNotContain(hash);
        }

        @Test
        void elMaterialSeSubeYSeDescarga() throws Exception {
            UUID id = crear(EDITOR, curso("Con material"));

            MvcResult subida = mvc.perform(multipart(BASE + "/" + id + "/materiales")
                            .file(new MockMultipartFile("archivo", "../../guia.pdf", "text/plain", PDF))
                            .with(como(EDITOR)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.materiales[0].nombre").value("guia.pdf"))
                    .andExpect(jsonPath("$.materiales[0].tipoMime").value("application/pdf"))
                    .andReturn();
            String materialId = cuerpo(subida).get("materiales").get(0).get("id").asText();

            ejecutar(get(BASE + "/" + id + "/materiales/" + materialId), EDITOR)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                    .andExpect(content().bytes(PDF));
        }

        @Test
        void seRechazaUnArchivoDeTipoNoAdmitido() throws Exception {
            UUID id = crear(EDITOR, curso("Tipos"));

            mvc.perform(multipart(BASE + "/" + id + "/materiales")
                            .file(new MockMultipartFile("archivo", "script.pdf", "application/pdf",
                                    "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                            .with(como(EDITOR)).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void sinSesionSeResponde401() throws Exception {
            mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        }
    }

    // ---- R9: mínimo privilegio por rol ----

    @Nested
    class MinimoPrivilegio {

        @Test
        void elAdministradorTambienGestionaContenido() throws Exception {
            UUID id = crear(ADMIN, curso("Spring"));
            agregarLeccion(ADMIN, id);

            ejecutar(post(BASE + "/" + id + "/publicar"), ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PUBLICADO"));
        }

        static Stream<Arguments> sinPermisoDeGestion() {
            return Stream.of(
                    Arguments.of("Reclutador", RECLUTADOR),
                    Arguments.of("Talento", TALENTO),
                    Arguments.of("Staff sin rol", SIN_ROL));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sinPermisoDeGestion")
        void noPuedeEjecutarNingunaOperacionDeGestion(String nombre, Consumidor consumidor) throws Exception {
            UUID id = crear(EDITOR, curso("Existente"));
            String leccion = agregarLeccion(EDITOR, id).get("lecciones").get(0).get("id").asText();
            MvcResult subida = mvc.perform(multipart(BASE + "/" + id + "/materiales")
                            .file(new MockMultipartFile("archivo", "a.pdf", "application/pdf", PDF))
                            .with(como(EDITOR)).with(csrf()))
                    .andReturn();
            String material = cuerpo(subida).get("materiales").get(0).get("id").asText();

            List<MockHttpServletRequestBuilder> operaciones = List.of(
                    get(BASE),
                    post(BASE).contentType(MediaType.APPLICATION_JSON).content(aJson(curso("Nuevo"))),
                    get(BASE + "/" + id),
                    put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(aJson(actualizacion("Pisado"))),
                    post(BASE + "/" + id + "/publicar"),
                    post(BASE + "/" + id + "/bajar"),
                    delete(BASE + "/" + id),
                    post(BASE + "/" + id + "/lecciones").contentType(MediaType.APPLICATION_JSON)
                            .content(aJson(Map.of("titulo", "x", "cuerpo", "x"))),
                    delete(BASE + "/" + id + "/lecciones/" + leccion),
                    get(BASE + "/" + id + "/materiales/" + material),
                    delete(BASE + "/" + id + "/materiales/" + material));

            for (MockHttpServletRequestBuilder operacion : operaciones) {
                ejecutar(operacion, consumidor)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
            }
            mvc.perform(multipart(BASE + "/" + id + "/materiales")
                            .file(new MockMultipartFile("archivo", "b.pdf", "application/pdf", PDF))
                            .with(como(consumidor)).with(csrf()))
                    .andExpect(status().isForbidden());

            assertThat(jdbc.queryForMap("SELECT titulo, estado FROM contenido WHERE id = ?", id))
                    .containsEntry("titulo", "Existente")
                    .containsEntry("estado", "BORRADOR");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contenido", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leccion", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material", Integer.class)).isEqualTo(1);
        }
    }

    // ---- Apoyo ----

    private static RequestPostProcessor como(Consumidor consumidor) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(consumidor, null, List.of()));
    }

    private ResultActions ejecutar(MockHttpServletRequestBuilder solicitud, Consumidor consumidor) throws Exception {
        return mvc.perform(solicitud.with(como(consumidor)).with(csrf()));
    }

    private UUID crear(Consumidor consumidor, Map<String, Object> datos) throws Exception {
        MvcResult resultado = ejecutar(post(BASE).contentType(MediaType.APPLICATION_JSON).content(aJson(datos)), consumidor)
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(cuerpo(resultado).get("id").asText());
    }

    private JsonNode agregarLeccion(Consumidor consumidor, UUID contenido) throws Exception {
        MvcResult resultado = ejecutar(post(BASE + "/" + contenido + "/lecciones").contentType(MediaType.APPLICATION_JSON)
                .content(aJson(Map.of("titulo", "Introducción", "cuerpo", "Contenido de la lección"))), consumidor)
                .andExpect(status().isOk()).andReturn();
        return cuerpo(resultado);
    }

    private JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    private String aJson(Object objeto) throws Exception {
        return json.writeValueAsString(objeto);
    }

    private static Map<String, Object> curso(String titulo) {
        return datos("CURSO", titulo);
    }

    private static Map<String, Object> proyecto(String titulo) {
        return datos("PROYECTO", titulo);
    }

    private static Map<String, Object> datos(String tipo, String titulo) {
        Map<String, Object> datos = new HashMap<>(actualizacion(titulo));
        datos.put("tipo", tipo);
        return datos;
    }

    private static Map<String, Object> actualizacion(String titulo) {
        return Map.of("titulo", titulo, "descripcion", "Descripción", "tecnologia", "Java",
                "nivel", "INICIAL", "duracionHoras", 10, "costo", 0);
    }

    private static Map<String, Object> entregable(String titulo) {
        return Map.of("titulo", titulo, "consigna", "Consigna", "pista", "Pista", "respuestasAceptadas", List.of("r"));
    }
}
