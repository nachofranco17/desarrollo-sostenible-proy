package uy.edu.um.xperience.security;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
@Service
public class Reautenticacion {
    public static final String KEY = Reautenticacion.class.getName();
    private final Duration validity;
    public Reautenticacion(@Value("${security.reauthentication-validity:5m}") Duration validity) { this.validity = validity; }
    public record Proof(UUID usuarioId, UUID empresaId, Sujeto.Rol rol, Instant time) implements Serializable {}
    private HttpSession session() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes a ? a.getRequest().getSession(false) : null;
    }
    public void clear() { var session = session(); if (session != null) session.removeAttribute(KEY); }
    public void confirm(Sujeto s) {
        var session = session();
        if (session == null) throw new org.springframework.security.access.AccessDeniedException("Sin sesión");
        session.setAttribute(KEY, new Proof(s.usuarioId(), s.empresaId(), s.rol(), Instant.now()));
    }
    public boolean valid(Sujeto s) {
        var session = session();
        if (session == null || !(session.getAttribute(KEY) instanceof Proof p)) return false;
        Instant now = Instant.now();
        return p.usuarioId().equals(s.usuarioId()) && java.util.Objects.equals(p.empresaId(), s.empresaId())
            && p.rol() == s.rol() && !p.time().isAfter(now) && now.isBefore(p.time().plus(validity));
    }
}
