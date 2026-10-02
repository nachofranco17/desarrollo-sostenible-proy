package uy.edu.um.xperience.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class Denegaciones {
    public static final String ACTION = Denegaciones.class.getName() + ".action";
    private static final Logger log = LoggerFactory.getLogger(Denegaciones.class);
    private final Sujetos subjects;
    private final RegistroAccesos registry;
    private final AuthorizationService policy;
    public Denegaciones(Sujetos subjects, RegistroAccesos registry, AuthorizationService policy) {
        this.subjects = subjects; this.registry = registry; this.policy = policy;
    }
    public void registrar(HttpServletRequest request) {
        Sujeto subject;
        try { subject = subjects.current(); } catch (RuntimeException error) { subject = new Sujeto(null, null, null, null); }
        String action = request.getAttribute(ACTION) instanceof String a ? a : "NO_DECLARADA";
        // Never send passwords, request bodies, invitation tokens or query strings to the audit interface.
        try { registry.denegado(subject, action, request.getRequestURI()); }
        catch (RuntimeException error) {
            // A failing access log must never turn a denial into something else; the denial still happens.
            log.error("No se pudo registrar la denegacion de {} sobre {}", action, request.getRequestURI(), error);
        }
    }
    public void responder(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        try { registrar(request); } finally {
            try {
                Sujeto subject = subjects.current();
                if (subject.usuarioId() != null && !policy.vigente(subject)) {
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                    SecurityContextHolder.clearContext();
                }
            } catch (RuntimeException ignored) { /* A failed policy lookup never grants access. */ }
            json(response, status, status == 401 ? "Credenciales o sesión inválidas." : "Acceso denegado.");
        }
    }
    public void responder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        responder(request, response, auth == null || auth instanceof AnonymousAuthenticationToken ? 401 : 403);
    }
    public static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
