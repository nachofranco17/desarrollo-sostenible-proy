package uy.edu.um.xperience.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class Denegaciones {
    public static final String ACTION = Denegaciones.class.getName() + ".action";
    public static final String REASON = Denegaciones.class.getName() + ".reason";
    public static final String RESOURCE_TYPE = Denegaciones.class.getName() + ".resourceType";
    public static final String RESOURCE_ID = Denegaciones.class.getName() + ".resourceId";
    public static final String FIELD = Denegaciones.class.getName() + ".field";
    public static final String OPERATION = Denegaciones.class.getName() + ".operation";

    public static final String ROLE_PERMISSION_DENIED = "ROLE_PERMISSION_DENIED";
    public static final String INSTANCE_ACCESS_DENIED = "INSTANCE_ACCESS_DENIED";
    public static final String ORGANIZATION_ACCESS_DENIED = "ORGANIZATION_ACCESS_DENIED";
    public static final String FIELD_ACCESS_DENIED = "FIELD_ACCESS_DENIED";
    public static final String FILE_ACCESS_DENIED = "FILE_ACCESS_DENIED";

    private static final Pattern SAFE_FIELD = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,64}$");
    private static final Logger log = LoggerFactory.getLogger(Denegaciones.class);

    private final Sujetos subjects;
    private final RegistroAccesos registry;
    private final AuthorizationService policy;

    public Denegaciones(Sujetos subjects, RegistroAccesos registry, AuthorizationService policy) {
        this.subjects = subjects;
        this.registry = registry;
        this.policy = policy;
    }

    public void registrar(HttpServletRequest request) {
        String action = "NO_DECLARADA";
        try {
            Sujeto subject;
            try {
                subject = subjects.current();
            } catch (RuntimeException error) {
                subject = new Sujeto(null, null, null, null);
            }
            action = request.getAttribute(ACTION) instanceof String a ? a : "NO_DECLARADA";
            completarMetadatos(request, action);
            // Never send passwords, request bodies, invitation tokens or query strings to the audit interface.
            registry.denegado(subject, action, request.getRequestURI());
        } catch (RuntimeException error) {
            // A failing access log must never turn a denial into something else; the denial still happens.
            log.error("No se pudo registrar la denegacion de {} sobre {}", action, request.getRequestURI(), error);
        }
    }

    public void responder(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        try {
            registrar(request);
        } finally {
            try {
                Sujeto subject = subjects.current();
                if (subject.usuarioId() != null && !policy.vigente(subject)) {
                    if (request.getSession(false) != null) {
                        request.getSession(false).invalidate();
                    }
                    SecurityContextHolder.clearContext();
                }
            } catch (RuntimeException ignored) {
                /* A failed policy lookup never grants access. */
            }
            json(response, status, status == 401 ? "Credenciales o sesión inválidas." : "Acceso denegado.");
        }
    }

    public void responder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        responder(request, response, auth == null || auth instanceof AnonymousAuthenticationToken ? 401 : 403);
    }

    public static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    /** Marca una denegación vertical (PEP / rol) sin tocar el resultado de autorización. */
    public static void marcarRolDenegado(HttpServletRequest request) {
        if (request != null && request.getAttribute(REASON) == null) {
            request.setAttribute(REASON, ROLE_PERMISSION_DENIED);
        }
    }

    /**
     * Marca un campo no autorizado (RS12). Solo el nombre técnico; nunca el valor.
     * Seguro llamar desde parsers estáticos vía {@link RequestContextHolder}.
     */
    public static void marcarCampoNoAutorizado(String field) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        request.setAttribute(REASON, FIELD_ACCESS_DENIED);
        if (field != null && SAFE_FIELD.matcher(field).matches()) {
            request.setAttribute(FIELD, field);
        }
    }

    static void completarMetadatos(HttpServletRequest request, String action) {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        // Acceso por identificador a un perfil ajeno (no existe /profile/{id}) → instancia (RS11),
        // aunque el PEP lo vea primero como ruta sin acción.
        if (esPerfilPorIdentificador(path)) {
            request.setAttribute(REASON, INSTANCE_ACCESS_DENIED);
        } else if (request.getAttribute(REASON) == null) {
            request.setAttribute(REASON, clasificar(action, path));
        }
        if (request.getAttribute(RESOURCE_TYPE) == null) {
            request.setAttribute(RESOURCE_TYPE, RegistroAccesosEstructurado.resourceType(action, path));
        }
        if (request.getAttribute(OPERATION) == null) {
            request.setAttribute(OPERATION, RegistroAccesosEstructurado.operation(request, action, path));
        }
        if (request.getAttribute(RESOURCE_ID) == null) {
            String id = RegistroAccesosEstructurado.resourceId(path);
            if (id != null) {
                request.setAttribute(RESOURCE_ID, id);
            }
        }
    }

    private static boolean esPerfilPorIdentificador(String path) {
        return path.matches(".*/api/profile/[^/]+/?") && !path.endsWith("/me") && !path.endsWith("/me/");
    }

    private static String clasificar(String action, String path) {
        if ((action != null && (action.startsWith("material.") || action.startsWith("curriculum.")))
                || path.contains("/materials/") || path.endsWith("/curriculum")) {
            return FILE_ACCESS_DENIED;
        }
        if (path.contains("/company/courses") || path.contains("/company/offers")
                || (action != null && action.startsWith("curso."))) {
            return ORGANIZATION_ACCESS_DENIED;
        }
        if (action != null && action.startsWith("staff.")) {
            return ORGANIZATION_ACCESS_DENIED;
        }
        if (action != null && action.startsWith("perfil.")) {
            return INSTANCE_ACCESS_DENIED;
        }
        // Denegación tras pasar el PEP: instancia por defecto. Vertical ya marca ROLE en el PEP.
        if (action != null && !"NO_DECLARADA".equals(action)) {
            return INSTANCE_ACCESS_DENIED;
        }
        return ROLE_PERMISSION_DENIED;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
