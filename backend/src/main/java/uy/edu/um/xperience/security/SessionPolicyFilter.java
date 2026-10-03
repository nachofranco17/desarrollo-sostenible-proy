package uy.edu.um.xperience.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Covers framework endpoints that never reach an MVC handler. Runs before LogoutFilter. */
public class SessionPolicyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionPolicyFilter.class);
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;
    private final Denegaciones denials;
    private final ObjectMapper json;
    public SessionPolicyFilter(Sujetos subjects, EvaluadorPolitica policy, Denegaciones denials, ObjectMapper json) {
        this.subjects = subjects; this.policy = policy; this.denials = denials; this.json = json;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // CSRF bootstrap is framework infrastructure, not a domain operation or an unannotated controller.
        if ("GET".equals(request.getMethod()) && "/api/auth/csrf".equals(path)) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
            return;
        }
        String action = switch (path) {
            case "/api/auth/login" -> "sesion.iniciar";
            case "/api/auth/logout" -> "sesion.cerrar";
            default -> null;
        };
        if (action != null && "POST".equals(request.getMethod())) {
            request.setAttribute(Denegaciones.ACTION, action);
            boolean allowed;
            try {
                Sujeto subject = subjects.current();
                Object resource = "sesion.iniciar".equals(action) ? AuthorizationService.Global.INSTANCE : ResourceAccess.own(subject.usuarioId());
                allowed = policy.puedeInvocar(subject, action) && policy.autorizar(subject, action, resource);
            } catch (RuntimeException error) {
                log.error("Fallo la evaluacion de {}; se deniega", action, error);
                allowed = false;
            }
            if (!allowed) {
                Denegaciones.marcarRolDenegado(request);
                denials.responder(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
