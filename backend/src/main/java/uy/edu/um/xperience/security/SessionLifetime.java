package uy.edu.um.xperience.security;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionLifetime {
    private final int adminSeconds;
    private final int commonSeconds;

    public SessionLifetime(@Value("${security.admin-session-timeout:10m}") Duration admin,
                           @Value("${spring.session.timeout:30m}") Duration common) {
        if (admin.toSeconds() < 1 || admin.compareTo(common) >= 0) {
            throw new IllegalArgumentException("La sesión administrativa debe ser más corta que la común");
        }
        adminSeconds = Math.toIntExact(admin.toSeconds());
        commonSeconds = Math.toIntExact(common.toSeconds());
    }

    public void apply(HttpSession session, Sujeto subject) {
        if (session != null && subject.usuarioId() != null) {
            session.setMaxInactiveInterval(subject.rol() == Sujeto.Rol.ADMIN ? adminSeconds : commonSeconds);
        }
    }

    public void restrict(HttpSession session) {
        if (session != null) session.setMaxInactiveInterval(adminSeconds);
    }
}
