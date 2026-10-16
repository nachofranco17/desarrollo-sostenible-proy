package uy.edu.um.xperience;

import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.Parameter;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.InvitationTokens;
import uy.edu.um.xperience.security.RequiereAccion;
import static org.assertj.core.api.Assertions.*;

/**
 * RS10 — Decisión de autorización exclusiva en el backend (ASVS v5.0.0-8.3.1).
 * La interfaz solo oculta o deshabilita opciones; quien decide es el servidor, con el sujeto
 * armado desde la sesión. Llamar a la API directamente o falsificar señales del cliente
 * (headers, parámetros, cookies, campos del body) no cambia la decisión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Rs10BackendAuthorizationTest {
    private static final String PASSWORD = "una frase de prueba segura";
    private static final String COURSES = "/company/courses";
    private static final String OFFERS = "/company/offers";
    /** Nombres con los que un cliente podría intentar declarar quién es o qué puede hacer. */
    private static final Set<String> SUBJECT_ATTRIBUTES = Set.of("rol", "role", "roles", "tipocuenta", "empresaid",
        "companyid", "usuarioid", "userid", "talentoid", "ownerid", "permisos", "permissions", "alcance", "admin");

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CompanyProvisioner provisioner;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping;

    // ---- Señales falsificadas por el cliente ----

    @Test
    void forgedRoleAndIdentitySignalsDoNotElevateATalent() throws Exception {
        UUID company = company();
        var admin = admin(company);
        var talent = registerTalent();
        int members = members(company);

        for (var request : companyOperations(admin)) {
            var baseline = talent.send(request.method, request.path, request.body, Map.of());
            var forged = talent.send(request.method, request.path + forgedQuery(admin, company), request.body,
                forgedHeaders(admin, company));
            assertThat(baseline.statusCode()).as(request.toString()).isEqualTo(403);
            assertThat(forged.statusCode()).as(request.toString()).isEqualTo(baseline.statusCode());
            assertThat(forged.body()).as(request.toString()).isEqualTo(baseline.body());
        }
        talent.forgeCookies(admin, company);
        for (var request : companyOperations(admin)) {
            assertThat(talent.send(request.method, request.path, request.body, Map.of()).statusCode())
                .as(request + " con cookies falsificadas").isEqualTo(403);
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oferta WHERE empresa_id = ?", Integer.class, company))
            .isZero();
        assertThat(members(company)).isEqualTo(members);
        assertThat(accounts.byId(admin.id).orElseThrow().rol()).isEqualTo("ADMIN");
        assertThat(json.readTree(talent.get("/account").body()).path("rol").asText()).isEqualTo("TALENTO");
    }

    @Test
    void forgedSignalsDoNotWidenAStaffRoleInsideItsOwnCompany() throws Exception {
        UUID company = company();
        var admin = admin(company);
        var recruiter = member(company, "RECLUTADOR");
        var course = courseBody("Curso del reclutador");

        var baseline = recruiter.send("POST", COURSES, course, Map.of());
        var forged = recruiter.send("POST", COURSES + "?rol=EDITOR&role=ADMIN", course,
            forgedHeaders(admin, company));
        var staff = recruiter.send("GET", "/staff", null, forgedHeaders(admin, company));

        assertThat(baseline.statusCode()).isEqualTo(403);
        assertThat(forged.statusCode()).isEqualTo(403);
        assertThat(forged.body()).isEqualTo(baseline.body());
        assertThat(staff.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM curso WHERE empresa_id = ?", Integer.class, company))
            .isZero();
    }

    @Test
    void visitorCannotDeclareAnIdentityWithHeadersOrCookies() throws Exception {
        UUID company = company();
        var admin = admin(company);
        var visitor = new Browser(null);
        visitor.forgeCookies(admin, company);
        visitor.cookies.getCookieStore().add(uri("/"), cookie("SESSION",
            Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII))));

        for (String path : List.of("/account", "/profile/me", "/enrollments/me", "/staff", COURSES, OFFERS)) {
            var response = visitor.send("GET", path, null, forgedHeaders(admin, company));
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).as(path).doesNotContain(admin.email, company.toString());
        }
    }

    // ---- Controles que la interfaz solo oculta ----

    @Test
    void adminRestrictionsShownOnlyAsHiddenButtonsAreEnforcedByTheServer() throws Exception {
        UUID company = company();
        var admin = admin(company);
        var editor = member(company, "EDITOR");
        String change = "{\"rol\":\"RECLUTADOR\"}";

        // La interfaz pide la contraseña antes de mostrar la confirmación; la API no depende de ese diálogo.
        assertThat(admin.send("PATCH", "/staff/" + editor.id + "/rol", change, Map.of()).statusCode()).isEqualTo(403);
        assertThat(admin.send("POST", "/staff/" + editor.id + "/baja", "", Map.of()).statusCode()).isEqualTo(403);
        assertThat(role(editor.id)).isEqualTo("EDITOR");

        // La interfaz no ofrece cambiar el propio rol ni darse de baja, ni siquiera reautenticado.
        assertThat(admin.reauthenticate().statusCode()).isEqualTo(204);
        assertThat(admin.send("PATCH", "/staff/" + admin.id + "/rol", change, Map.of()).statusCode()).isEqualTo(403);
        assertThat(admin.send("POST", "/staff/" + admin.id + "/baja", "", Map.of()).statusCode()).isEqualTo(403);
        assertThat(role(admin.id)).isEqualTo("ADMIN");

        // Control positivo: el mismo pedido es válido cuando el servidor sí lo autoriza.
        assertThat(admin.send("PATCH", "/staff/" + editor.id + "/rol", change, Map.of()).statusCode()).isEqualTo(204);
        assertThat(role(editor.id)).isEqualTo("RECLUTADOR");
    }

    @Test
    void stateRulesThatTheInterfaceOnlyDisablesAreEnforcedByTheServer() throws Exception {
        UUID company = company();
        var admin = admin(company);
        var talent = registerTalent();

        // Curso publicado: la interfaz oculta "Eliminar" en sus lecciones.
        UUID published = createCourse(admin, "Publicado RS10");
        String lesson = json.readTree(admin.send("POST", COURSES + "/" + published + "/lessons",
            json.writeValueAsString(Map.of("titulo", "L1", "cuerpo", "Cuerpo")), Map.of()).body())
            .path("lecciones").get(0).path("id").asText();
        assertThat(admin.send("POST", COURSES + "/" + published + "/publish", "", Map.of()).statusCode()).isEqualTo(200);
        assertThat(admin.send("DELETE", COURSES + "/" + published + "/lessons/" + lesson, null, Map.of()).statusCode())
            .isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leccion WHERE id = ?", Integer.class,
            UUID.fromString(lesson))).isEqualTo(1);

        // Curso dado de baja: la interfaz deshabilita el formulario.
        assertThat(admin.send("POST", COURSES + "/" + published + "/unpublish", "", Map.of()).statusCode()).isEqualTo(200);
        assertThat(admin.send("PUT", COURSES + "/" + published, json.writeValueAsString(courseData("Editado")), Map.of())
            .statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT titulo FROM curso WHERE id = ?", String.class, published))
            .isEqualTo("Publicado RS10");

        // Borrador: el catálogo no lo muestra, pero conocer el id no permite inscribirse.
        UUID draft = createCourse(admin, "Borrador RS10");
        assertThat(talent.send("POST", "/enrollments", json.writeValueAsString(Map.of("cursoId", draft.toString())),
            Map.of()).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inscripcion WHERE curso_id = ?", Integer.class, draft))
            .isZero();

        // Oferta publicada: la interfaz la muestra solo para lectura.
        var created = admin.send("POST", OFFERS, offerBody("Oferta RS10"), Map.of());
        UUID offer = UUID.fromString(json.readTree(created.body()).path("id").asText());
        assertThat(admin.send("POST", OFFERS + "/" + offer + "/publish", "", Map.of()).statusCode()).isEqualTo(200);
        assertThat(admin.send("PUT", OFFERS + "/" + offer, offerBody("Cambio posterior"), Map.of()).statusCode())
            .isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT titulo FROM oferta WHERE id = ?", String.class, offer))
            .isEqualTo("Oferta RS10");

        // Postulación: la interfaz deshabilita el botón si no hay currículum.
        assertThat(talent.send("POST", "/offers/" + offer + "/applications", "{}", Map.of()).statusCode())
            .isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postulacion WHERE oferta_id = ?", Integer.class, offer))
            .isZero();
    }

    // ---- Convenciones que mantienen la decisión en el servidor ----

    @Test
    void handlersNeverReadTheSubjectOrItsPermissionsFromTheRequest() {
        var violations = new ArrayList<String>();
        for (HandlerMethod handler : mapping.getHandlerMethods().values()) {
            if (BasicErrorController.class.isAssignableFrom(handler.getBeanType())) continue;
            var action = handler.getMethodAnnotation(RequiereAccion.class);
            String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
            for (Parameter parameter : handler.getMethod().getParameters()) {
                var header = parameter.getAnnotation(RequestHeader.class);
                // Única excepción documentada: el token de invitación lo valida el PEP, no lo decide el cliente.
                if (header != null && !(action != null && "invitacion.aceptar".equals(action.value())
                        && InvitationTokens.HEADER.equals(header.value()))) {
                    violations.add(name + " lee el header " + header.value());
                }
                if (parameter.isAnnotationPresent(CookieValue.class)) violations.add(name + " lee una cookie");
                var param = parameter.getAnnotation(RequestParam.class);
                if (param != null && subjectAttribute(param.value().isEmpty() ? param.name() : param.value())) {
                    violations.add(name + " recibe el parámetro " + param.value());
                }
                var path = parameter.getAnnotation(PathVariable.class);
                if (path != null && subjectAttribute(path.value().isEmpty() ? parameter.getName() : path.value())) {
                    violations.add(name + " recibe la variable de ruta " + path.value());
                }
                if (parameter.isAnnotationPresent(RequestBody.class) && parameter.getType().isRecord()) {
                    for (var component : parameter.getType().getRecordComponents()) {
                        // staff.cambiar_rol recibe el rol a asignar al otro miembro, no el del sujeto.
                        boolean targetRole = action != null && "staff.cambiar_rol".equals(action.value())
                            && "rol".equals(component.getName());
                        if (subjectAttribute(component.getName()) && !targetRole) {
                            violations.add(name + " acepta el campo " + component.getName() + " en el body");
                        }
                    }
                }
            }
        }
        assertThat(violations).as("datos de autorización tomados de la solicitud").isEmpty();
    }

    @Test
    void frontendNeverSendsOrStoresAuthorizationDecisions() throws Exception {
        Pattern forbidden = Pattern.compile(
            "localStorage|sessionStorage|indexedDB|document\\.cookie|['\"]Authorization['\"]"
                + "|['\"]X-(Role|Rol|User|Usuario|Empresa|Company|Tenant|Permission|Permiso)[A-Za-z-]*['\"]",
            Pattern.CASE_INSENSITIVE);
        var violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("../frontend/src"))) {
            for (Path file : files.filter(f -> f.toString().matches(".*\\.(ts|tsx)$")).toList()) {
                var lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (forbidden.matcher(lines.get(i)).find()) violations.add(file.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertThat(violations).as("el frontend no debe enviar ni guardar identidad, rol o permisos").isEmpty();
    }

    // ---- Apoyo ----

    private record Call(String method, String path, String body) {}

    private List<Call> companyOperations(Browser admin) throws Exception {
        return List.of(
            new Call("GET", "/staff", null),
            new Call("POST", "/staff/invitations", json.writeValueAsString(Map.of("correo", email()))),
            new Call("PATCH", "/staff/" + admin.id + "/rol", "{\"rol\":\"EDITOR\"}"),
            new Call("GET", COURSES, null),
            new Call("POST", COURSES, courseBody("Curso forzado")),
            new Call("GET", OFFERS, null),
            new Call("POST", OFFERS, offerBody("Oferta forzada")));
    }

    private static Map<String, String> forgedHeaders(Browser admin, UUID company) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("X-Role", "ADMIN");
        headers.put("X-Rol", "ADMIN");
        headers.put("X-User-Role", "ADMIN");
        headers.put("X-User-Id", admin.id.toString());
        headers.put("X-Usuario-Id", admin.id.toString());
        headers.put("X-Empresa-Id", company.toString());
        headers.put("X-Forwarded-User", admin.email);
        headers.put("X-Remote-User", admin.email);
        headers.put("X-Authenticated", "true");
        headers.put("X-Permissions", "staff.ver,oferta.gestionar,curso.gestionar");
        // Un token autocontenido "firmado" por el cliente: el servidor solo reconoce su sesión.
        headers.put("Authorization", "Bearer " + base64("{\"alg\":\"none\"}") + "."
            + base64("{\"sub\":\"" + admin.id + "\",\"rol\":\"ADMIN\",\"empresaId\":\"" + company + "\"}") + ".");
        return headers;
    }

    private static String forgedQuery(Browser admin, UUID company) {
        return "?rol=ADMIN&role=ADMIN&empresaId=" + company + "&usuarioId=" + admin.id + "&admin=true";
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean subjectAttribute(String name) {
        return name != null && SUBJECT_ATTRIBUTES.contains(name.toLowerCase(Locale.ROOT).replace("_", ""));
    }

    private static HttpCookie cookie(String name, String value) {
        var cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setVersion(0);
        return cookie;
    }

    private int members(UUID company) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM membresia WHERE empresa_id = ?", Integer.class, company);
    }

    private String role(UUID user) {
        return jdbc.queryForObject("SELECT rol FROM membresia WHERE usuario_id = ?", String.class, user);
    }

    private Browser registerTalent() throws Exception {
        String email = email();
        var client = new Browser(email);
        assertThat(client.send("POST", "/auth/register", json.writeValueAsString(
            new RegisterRequest(email, "Ana", "Prueba", PASSWORD)), Map.of()).statusCode()).isEqualTo(202);
        assertThat(client.login(PASSWORD).statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private UUID company() {
        return provisioner.create("Empresa " + UUID.randomUUID(),
            new RegisterRequest(email(), "Admin", "Prueba", PASSWORD), "test-suite");
    }

    private Browser admin(UUID company) throws Exception {
        String email = jdbc.queryForObject("""
            SELECT u.correo FROM usuario u JOIN membresia m ON m.usuario_id = u.id
            WHERE m.empresa_id = ? AND m.rol = 'ADMIN'
            """, String.class, company);
        var client = new Browser(email);
        assertThat(client.login(PASSWORD).statusCode()).isEqualTo(204);
        client.id = accounts.byEmail(email).orElseThrow().id();
        return client;
    }

    private Browser member(UUID company, String role) throws Exception {
        String email = email();
        UUID id = accounts.create(email, "Staff", "Prueba", passwords.encode(PASSWORD), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')",
            id, company, role);
        var client = new Browser(email);
        assertThat(client.login(PASSWORD).statusCode()).isEqualTo(204);
        client.id = id;
        return client;
    }

    private UUID createCourse(Browser client, String title) throws Exception {
        var response = client.send("POST", COURSES, courseBody(title), Map.of());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("id").asText());
    }

    private String courseBody(String title) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("tipo", "CURSO");
        body.putAll(courseData(title));
        return json.writeValueAsString(body);
    }

    private static Map<String, Object> courseData(String title) {
        var body = new LinkedHashMap<String, Object>();
        body.put("titulo", title);
        body.put("descripcion", "Descripción");
        body.put("tecnologia", "Java");
        body.put("nivel", "INICIAL");
        body.put("duracionHoras", 10);
        body.put("costo", 0);
        return body;
    }

    private String offerBody(String title) throws Exception {
        return json.writeValueAsString(Map.of("titulo", title, "descripcion", "Desc", "cursosRequeridos", List.of()));
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String email;
        UUID id;

        Browser(String email) { this.email = email; }

        HttpResponse<String> login(String password) throws Exception {
            return post("/auth/login", "correo=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
        }

        HttpResponse<String> reauthenticate() throws Exception {
            return send("POST", "/auth/reauthenticate", json.writeValueAsString(Map.of("password", PASSWORD)), Map.of());
        }

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, Map.of()); }

        /** Cookies con las que el cliente intenta declarar rol, empresa o identidad. */
        void forgeCookies(Browser admin, UUID company) {
            var store = cookies.getCookieStore();
            store.add(uri("/"), cookie("rol", "ADMIN"));
            store.add(uri("/"), cookie("role", "ADMIN"));
            store.add(uri("/"), cookie("empresaId", company.toString()));
            store.add(uri("/"), cookie("usuarioId", admin.id.toString()));
        }

        HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
            boolean write = !"GET".equals(method);
            var builder = HttpRequest.newBuilder(uri(path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) builder.header("Content-Type", "application/json");
            if (write) csrf(builder);
            headers.forEach(builder::header);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
            csrf(builder);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private void csrf(HttpRequest.Builder builder) throws Exception {
            var token = json.readTree(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
            builder.header(token.path("headerName").asText(), token.path("token").asText());
        }
    }
}
