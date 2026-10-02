package uy.edu.um.xperience.security;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.persistence.*;

@Service
public class AuthorizationService implements EvaluadorPolitica {
    public enum Global { INSTANCE }
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private final AccountRepository accounts;
    private final Permisos permissions;
    private final Reautenticacion reauthentication;
    public AuthorizationService(AccountRepository accounts, Permisos permissions, Reautenticacion reauthentication) {
        this.accounts = accounts; this.permissions = permissions; this.reauthentication = reauthentication;
    }
    public boolean vigente(Sujeto s) {
        try {
            if (s == null) return false;
            if (s.equals(Sujeto.visitante())) return true;
            return s.usuarioId() != null && accounts.byId(s.usuarioId())
                .filter(Account::canSignIn).map(a -> Sujeto.from(a).equals(s)).orElse(false);
        } catch (RuntimeException error) { falla("vigente", null, error); return false; }
    }
    // Every evaluation error denies (R6); the client only sees a generic denial, so the cause stays in the server log.
    private static void falla(String operacion, String accion, RuntimeException error) {
        log.error("Fallo la evaluacion de la politica ({} de {}); se deniega", operacion, accion, error);
    }
    private List<Permiso> permisos(Sujeto s, String action) {
        if (action == null || !vigente(s) || s.rol() == null) return List.of();
        return permissions.findByRolAndAccion(s.rol().name(), action);
    }
    @Override public boolean puedeInvocar(Sujeto s, String action) {
        try { return !permisos(s, action).isEmpty(); }
        catch (RuntimeException error) { falla("puedeInvocar", action, error); return false; }
    }
    private boolean sensitive(String action) {
        return "staff.cambiar_rol".equals(action) || "staff.dar_baja".equals(action);
    }
    private ResourceAccess attributes(Object resource) {
        if (resource instanceof ResourceAccess a) return a;
        if (resource instanceof Usuario u) return ResourceAccess.own(u.id);
        if (resource instanceof Membresia m) return new ResourceAccess(m.usuarioId, m.empresaId, false);
        if (resource instanceof TransicionPostulacion p) return ResourceAccess.company(p.empresaId());
        return null;
    }
    @Override public boolean autorizar(Sujeto s, String action, Object resource) {
        try {
            var grants = permisos(s, action);
            if (grants.isEmpty()) return false;
            var a = attributes(resource);
            if (sensitive(action) && (a == null || a.ownerId() == null || a.ownerId().equals(s.usuarioId())
                    || !reauthentication.valid(s))) return false;
            if ("postulacion.cambiar_estado".equals(action)
                    && (!(resource instanceof TransicionPostulacion p) || !p.permitida())) return false;
            return grants.stream().anyMatch(p -> switch (p.alcance) {
                case "GLOBAL" -> resource == Global.INSTANCE && s.equals(Sujeto.visitante());
                case "PROPIO" -> a != null && s.usuarioId() != null && s.usuarioId().equals(a.ownerId());
                case "PUBLICADO" -> a != null && a.published();
                case "ORG" -> a != null && s.empresaId() != null && s.empresaId().equals(a.companyId());
                // No enrollment/application resources exist yet. They must never be authorized by a supplied flag.
                case "INSCRIPTO", "POSTULANTE" -> false;
                default -> false;
            });
        } catch (RuntimeException error) { falla("autorizar", action, error); return false; }
    }
    @Override public <T> Specification<T> filtrar(Sujeto s, String action, Class<T> type) {
        try {
            var grants = permisos(s, action);
            if (grants.isEmpty() || (sensitive(action) && !reauthentication.valid(s))) return deny();
            if (type == Membresia.class && "staff.ver".equals(action) && s.empresaId() != null
                    && grants.stream().anyMatch(p -> "ORG".equals(p.alcance))) {
                return (root, query, cb) -> cb.equal(root.get("empresaId"), s.empresaId());
            }
            if (type == Usuario.class && Set.of("cuenta.gestionar", "sesion.reautenticar").contains(action)
                    && grants.stream().anyMatch(p -> "PROPIO".equals(p.alcance))) {
                return (root, query, cb) -> cb.and(cb.equal(root.get("id"), s.usuarioId()), cb.isTrue(root.get("activo")));
            }
            if (type == Empresa.class && "cuenta.gestionar".equals(action) && s.empresaId() != null
                    && grants.stream().anyMatch(p -> "PROPIO".equals(p.alcance))) {
                return (root, query, cb) -> cb.equal(root.get("id"), s.empresaId());
            }
            if (type == Membresia.class && sensitive(action) && s.empresaId() != null
                    && grants.stream().anyMatch(p -> "ORG".equals(p.alcance))) {
                return (root, query, cb) -> cb.and(cb.equal(root.get("empresaId"), s.empresaId()),
                    cb.notEqual(root.get("usuarioId"), s.usuarioId()), cb.equal(root.get("estado"), "ACTIVA"));
            }
            return deny();
        } catch (RuntimeException error) { falla("filtrar", action, error); return deny(); }
    }
    private <T> Specification<T> deny() { return (root, query, cb) -> cb.disjunction(); }

    @Override public Set<String> camposLegibles(Sujeto s, String action, Object resource) {
        if (!autorizar(s, action, resource)) return Set.of();
        if ("staff.ver".equals(action)) return Set.of("usuarioId", "empresaId", "correo", "nombre", "apellido", "rol", "estado");
        if ("cuenta.gestionar".equals(action)) return Set.of("id", "correo", "nombre", "apellido", "tipoCuenta", "rol", "empresaId", "empresaNombre");
        return Set.of();
    }
    @Override public Set<String> camposEscribibles(Sujeto s, String action, Object resource) {
        if (!autorizar(s, action, resource)) return Set.of();
        return switch (action) {
            case "cuenta.registrar" -> Set.of("correo", "nombre", "apellido", "password");
            case "sesion.iniciar" -> Set.of("correo", "password");
            case "sesion.reautenticar" -> Set.of("password");
            case "staff.cambiar_rol" -> Set.of("rol");
            case "staff.invitar" -> Set.of("correo");
            default -> Set.of();
        };
    }
    /** Convenience for domain policy tests; API callers use a server-resolved Sujeto. */
    public boolean allowed(UUID subjectId, String action, ResourceAccess resource) {
        try {
            Sujeto s = subjectId == null ? Sujeto.visitante() : accounts.byId(subjectId).map(Sujeto::from).orElse(null);
            return autorizar(s, action, resource);
        } catch (RuntimeException error) { falla("allowed", action, error); return false; }
    }
}
