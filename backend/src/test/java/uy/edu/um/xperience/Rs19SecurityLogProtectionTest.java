package uy.edu.um.xperience;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.security.*;
import static org.assertj.core.api.Assertions.*;

/**
 * RS19 — Los eventos de seguridad van a un almacén separado de solo agregado. La aplicación escribe
 * con un usuario que sólo tiene INSERT, así que ni ella ni sus usuarios con más privilegios pueden
 * leerlos, modificarlos ni borrarlos; el texto se codifica antes de escribirse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs19SecurityLogProtectionTest {
    private static final String PASSWORD = "una frase de prueba segura";

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate applicationDatabase;
    @Autowired RegistroAccesos registry;
    @Autowired ConfigurableListableBeanFactory beans;
    @Value("${app.security-log.url}") String url;
    @Value("${app.security-log.writer-user}") String writerUser;
    @Value("${app.security-log.writer-password}") String writerPassword;
    @Value("${app.security-log.admin-user}") String auditorUser;
    @Value("${app.security-log.admin-password}") String auditorPassword;
    @Value("${spring.datasource.username}") String databaseUser;
    @Value("${spring.datasource.password}") String databasePassword;

    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void everyDenialIsAppendedToTheSeparateStore() throws Exception {
        var talent = registerTalent();
        long before = lastId();

        assertThat(talent.get("/staff").statusCode()).isEqualTo(403);

        var events = eventsAfter(before);
        assertThat(events).hasSize(1);
        JsonNode event = json.readTree(events.get(0));
        assertThat(event.path("event").asText()).isEqualTo("authorization_denied");
        assertThat(event.path("userId").asText()).isEqualTo(talent.id.toString());
        assertThat(event.path("action").asText()).isEqualTo("staff.ver");
        assertThat(event.path("result").asText()).isEqualTo("denied");
        assertThat(events.get(0)).doesNotContain(talent.email, PASSWORD);
    }

    @Test
    void theApplicationCredentialsCanOnlyAppend() throws Exception {
        assertThat(talentDenial()).isPositive();
        long before = lastId();
        try (Connection writer = DriverManager.getConnection(url + ";IFEXISTS=TRUE", writerUser, writerPassword);
             Statement sql = writer.createStatement()) {
            for (String attempt : List.of(
                    "SELECT * FROM EVENTO_SEGURIDAD",
                    "SELECT COUNT(*) FROM EVENTO_SEGURIDAD",
                    "UPDATE EVENTO_SEGURIDAD SET EVENTO = '{}'",
                    "DELETE FROM EVENTO_SEGURIDAD",
                    "TRUNCATE TABLE EVENTO_SEGURIDAD",
                    "DROP TABLE EVENTO_SEGURIDAD",
                    "ALTER TABLE EVENTO_SEGURIDAD ADD COLUMN X INT",
                    "CREATE TABLE COPIA AS SELECT * FROM EVENTO_SEGURIDAD",
                    "GRANT SELECT ON EVENTO_SEGURIDAD TO " + writerUser,
                    "ALTER USER " + writerUser + " ADMIN TRUE",
                    "CREATE USER OTRO PASSWORD 'otro-usuario'",
                    "SCRIPT TO 'copia.sql'")) {
                // 90096 NOT_ENOUGH_RIGHTS / 90040 ADMIN_RIGHTS_REQUIRED: falla por permisos, no por sintaxis
                // ni por una tabla inexistente.
                assertThatThrownBy(() -> sql.execute(attempt)).as(attempt).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getErrorCode()).as(attempt + ": " + error.getMessage()).isIn(90096, 90040));
            }
        }
        // Nada cambió: lo verifica el auditor, el único que puede leer.
        assertThat(lastId()).isEqualTo(before);
        assertThat(eventsAfter(0)).allSatisfy(event -> assertThat(event).isNotEqualTo("{}"));
    }

    @Test
    void theApplicationDatabaseAndItsCredentialsCannotReachTheStore() {
        // La tabla no existe en la base de la aplicación: el almacén es otra base.
        assertThatThrownBy(() -> applicationDatabase.queryForObject("SELECT COUNT(*) FROM EVENTO_SEGURIDAD", Long.class))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // Ni las credenciales de la aplicación ni las del dueño de su base abren el almacén.
        assertThatThrownBy(() -> DriverManager.getConnection(url + ";IFEXISTS=TRUE", databaseUser, databasePassword).close())
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> DriverManager.getConnection(url + ";IFEXISTS=TRUE", "sa", "").close())
            .isInstanceOf(SQLException.class);
        // El escritor no puede crear un almacén nuevo con su usuario como administrador.
        assertThatThrownBy(() -> DriverManager.getConnection("jdbc:h2:mem:otro-almacen;IFEXISTS=TRUE",
            writerUser, writerPassword).close()).isInstanceOf(SQLException.class);
    }

    @Test
    void noComponentCanReadTheStoreSoNoApplicationUserCanEither() {
        String[] storeBeans = beans.getBeanNamesForType(AlmacenRegistroSeguridad.class);
        assertThat(storeBeans).hasSize(1);
        assertThat(beans.getDependentBeans(storeBeans[0])).containsExactly("registroAccesosEstructurado");
        assertThat(Arrays.stream(AlmacenRegistroSeguridad.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
            .map(Method::getName)).containsExactly("agregar");
    }

    @Test
    void userControlledTextIsEncodedBeforeItIsWritten() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/profile/me");
        String injected = "ROLE\r\n{\"event\":\"authorization_granted\"}<script>alert(1)</script>‮abc fin";
        request.setAttribute(Denegaciones.REASON, injected);
        request.setAttribute(Denegaciones.OPERATION, "x".repeat(5000));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        long before = lastId();

        registry.denegado(Sujeto.visitante(), "perfil.gestionar\nforjada", "/api/profile/me");

        var events = eventsAfter(before);
        assertThat(events).hasSize(1);
        String line = events.get(0);
        assertThat(line).doesNotContain("\n", "\r", "<", ">", "‮", " ");
        JsonNode event = json.readTree(line);
        assertThat(event.path("event").asText()).isEqualTo("authorization_denied");
        assertThat(event.path("result").asText()).isEqualTo("denied");
        assertThat(event.path("reason").asText())
            .startsWith("ROLE\\u000d\\u000a{\\u0022event\\u0022")
            .contains("\\u003cscript\\u003e", "\\u202e", "\\u2028")
            .doesNotContain("\"");
        assertThat(event.path("action").asText()).isEqualTo("perfil.gestionar\\u000aforjada");
        assertThat(event.path("operation").asText()).hasSizeLessThan(600).endsWith("[truncado]");
    }

    @Test
    void encoderLeavesOrdinaryTextReadable() {
        assertThat(CodificadorRegistro.codificar("material.descargar")).isEqualTo("material.descargar");
        assertThat(CodificadorRegistro.codificar("José Pérez 127.0.0.1")).isEqualTo("José Pérez 127.0.0.1");
        assertThat(CodificadorRegistro.codificar(null)).isNull();
        assertThat(CodificadorRegistro.codificar("a\tb\u0000c")).isEqualTo("a\\u0009b\\u0000c");
    }

    // ---- Apoyo ----

    private long talentDenial() throws Exception {
        var talent = registerTalent();
        assertThat(talent.get("/staff").statusCode()).isEqualTo(403);
        return lastId();
    }

    private Connection auditor() throws SQLException {
        return DriverManager.getConnection(url + ";IFEXISTS=TRUE", auditorUser, auditorPassword);
    }

    private long lastId() throws SQLException {
        try (Connection c = auditor(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COALESCE(MAX(ID), 0) FROM EVENTO_SEGURIDAD")) {
            r.next();
            return r.getLong(1);
        }
    }

    private List<String> eventsAfter(long id) throws SQLException {
        try (Connection c = auditor();
             PreparedStatement s = c.prepareStatement("SELECT EVENTO FROM EVENTO_SEGURIDAD WHERE ID > ? ORDER BY ID")) {
            s.setLong(1, id);
            var events = new ArrayList<String>();
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) events.add(r.getString(1));
            }
            return events;
        }
    }

    private Browser registerTalent() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var client = new Browser(email);
        assertThat(client.send("/auth/register", json.writeValueAsString(
            new RegisterRequest(email, "Ana", "Prueba", PASSWORD)), "application/json").statusCode()).isEqualTo(202);
        assertThat(client.send("/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8),
            "application/x-www-form-urlencoded").statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        final String email;
        UUID id;

        Browser(String email) { this.email = email; }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> send(String path, String body, String contentType) throws Exception {
            var token = json.readTree(get("/auth/csrf").body());
            return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                .header(token.path("headerName").asText(), token.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }
    }
}
