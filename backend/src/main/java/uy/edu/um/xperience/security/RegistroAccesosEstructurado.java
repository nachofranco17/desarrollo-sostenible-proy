package uy.edu.um.xperience.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RS18 — emite un evento estructurado (JSON) por cada acceso denegado.
 * Solo serializa una allowlist de metadatos seguros; nunca cuerpos, secretos ni contenido.
 */
@Component
public class RegistroAccesosEstructurado implements RegistroAccesos {
    public static final String LOGGER_NAME = "uy.edu.um.xperience.security.access";
    private static final Pattern SAFE_FIELD = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,64}$");
    private static final Pattern UUID_SEGMENT = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
    private final ObjectMapper json;

    public RegistroAccesosEstructurado(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void denegado(Sujeto sujeto, String accion, String recurso) {
        try {
            HttpServletRequest request = currentRequest();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "authorization_denied");
            event.put("timestamp", Instant.now().toString());
            event.put("userId", sujeto == null || sujeto.usuarioId() == null ? null : sujeto.usuarioId().toString());
            event.put("origin", origin(request));
            event.put("resourceType", textAttr(request, Denegaciones.RESOURCE_TYPE, resourceType(accion, recurso)));
            String resourceId = textAttr(request, Denegaciones.RESOURCE_ID, resourceId(recurso));
            if (resourceId != null) {
                event.put("resourceId", resourceId);
            }
            event.put("operation", textAttr(request, Denegaciones.OPERATION, operation(request, accion, recurso)));
            event.put("action", accion == null || accion.isBlank() ? "NO_DECLARADA" : accion);
            event.put("result", "denied");
            event.put("reason", textAttr(request, Denegaciones.REASON, "ACCESS_DENIED"));
            String field = textAttr(request, Denegaciones.FIELD, null);
            if (field != null && SAFE_FIELD.matcher(field).matches()) {
                event.put("field", field);
            }
            // Allowlist only: never request body, headers, cookies, tokens or resource content.
            log.warn("{}", json.writeValueAsString(event));
        } catch (Exception ignored) {
            // Logging must never convert a denial into an allow. Swallow and continue.
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String origin(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private static String textAttr(HttpServletRequest request, String name, String fallback) {
        if (request != null && request.getAttribute(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    static String resourceType(String action, String resource) {
        String a = action == null ? "" : action;
        String path = resource == null ? "" : resource;
        if (a.startsWith("material.") || a.startsWith("curriculum.")
                || path.contains("/materials/") || path.endsWith("/curriculum")) {
            return "file";
        }
        if (a.startsWith("perfil.") || path.contains("/profile")) {
            return "profile";
        }
        if (a.startsWith("curso.") || path.contains("/company/courses")) {
            return "course";
        }
        if (a.startsWith("cuenta.") || path.contains("/account") || path.contains("/auth/register")) {
            return "account";
        }
        if (a.startsWith("staff.") || path.contains("/staff")) {
            return "membership";
        }
        if (a.startsWith("sesion.") || path.contains("/auth/login") || path.contains("/auth/logout")
                || path.contains("/auth/reauthenticate")) {
            return "session";
        }
        if (a.startsWith("postulacion.") || path.contains("/applications")) {
            return "application";
        }
        if (a.startsWith("oferta.") || path.contains("/offers")) {
            return "offer";
        }
        if (a.startsWith("inscripcion.") || a.startsWith("leccion.") || a.startsWith("entregable.")) {
            return "enrollment";
        }
        return "unknown";
    }

    static String operation(HttpServletRequest request, String action, String resource) {
        String a = action == null ? "" : action;
        String path = resource == null ? "" : resource;
        if (a.contains("descargar") || (path.contains("/materials/") && method(request, "GET"))) {
            return "download";
        }
        if (a.contains("subir")) {
            return "create";
        }
        if (a.contains("registrar") || a.contains("invitar")) {
            return "create";
        }
        if ("oferta.gestionar".equals(a) && path.endsWith("/publish") && method(request, "POST")) {
            return "publish";
        }
        String method = request == null ? "" : String.valueOf(request.getMethod());
        return switch (method) {
            case "GET" -> path.endsWith("/company/courses") || path.endsWith("/offers")
                    || path.endsWith("/applications") || path.endsWith("/applications/me") ? "list" : "read";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> "unknown";
        };
    }

    private static boolean method(HttpServletRequest request, String expected) {
        return request != null && expected.equalsIgnoreCase(request.getMethod());
    }

    static String resourceId(String resource) {
        if (resource == null || resource.isBlank()) {
            return null;
        }
        String[] parts = resource.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (UUID_SEGMENT.matcher(parts[i]).matches()) {
                try {
                    return UUID.fromString(parts[i]).toString();
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
