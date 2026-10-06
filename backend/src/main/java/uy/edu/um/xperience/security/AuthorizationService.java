package uy.edu.um.xperience.security;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.application.JobApplication;
import uy.edu.um.xperience.course.Course;
import uy.edu.um.xperience.curriculum.Curriculum;
import uy.edu.um.xperience.offer.Offer;
import uy.edu.um.xperience.persistence.*;
import uy.edu.um.xperience.profile.TalentProfile;

@Service
public class AuthorizationService implements EvaluadorPolitica {
    public enum Global { INSTANCE }
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    private final AccountRepository accounts;
    private final Permisos permissions;
    private final Reautenticacion reauthentication;
    private final JdbcTemplate jdbc;
    public AuthorizationService(AccountRepository accounts, Permisos permissions, Reautenticacion reauthentication,
                                JdbcTemplate jdbc) {
        this.accounts = accounts; this.permissions = permissions; this.reauthentication = reauthentication;
        this.jdbc = jdbc;
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
        if (resource instanceof Course c) {
            return new ResourceAccess(null, c.empresaId(), c.isPublicado());
        }
        if (resource instanceof TalentProfile p) {
            return ResourceAccess.own(p.usuarioId());
        }
        if (resource instanceof Offer o) {
            return new ResourceAccess(null, o.empresaId(), o.isPublicado());
        }
        if (resource instanceof Curriculum c) {
            return ResourceAccess.own(c.usuarioId());
        }
        if (resource instanceof JobApplication p) {
            return new ResourceAccess(p.talentoId(), p.empresaId(), false);
        }
        return null;
    }
    /** POSTULANTE is a persisted relation, never a caller-provided attribute or ownership flag. */
    private boolean postulante(Sujeto s, String action, Object resource) {
        if (!(resource instanceof ApplicantAccess access) || access.application() == null
                || !Set.of("perfil.ver_postulante", "curriculum.descargar").contains(action)) return false;
        var application = access.application();
        if (s.empresaId() == null || !s.empresaId().equals(application.empresaId())
                || !"ENVIADA".equals(application.estado())) return false;
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM postulacion p
            JOIN oferta o ON o.id = p.oferta_id AND o.empresa_id = p.empresa_id
            JOIN curriculum c ON c.id = p.curriculum_id AND c.usuario_id = p.talento_id
            WHERE p.id = ? AND p.oferta_id = ? AND p.empresa_id = ? AND p.talento_id = ?
              AND p.curriculum_id = ? AND p.estado = 'ENVIADA'
            """, Integer.class, application.id(), application.ofertaId(), s.empresaId(),
            application.talentoId(), application.curriculumId());
        return count != null && count == 1;
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
                case "POSTULANTE" -> postulante(s, action, resource);
                // RF4 is not implemented; an enrollment flag never grants access.
                case "INSCRIPTO" -> false;
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
        if ("oferta.ver".equals(action) || "oferta.gestionar".equals(action)) {
            return Set.of("id", "titulo", "descripcion", "estado", "creadoEn", "actualizadoEn", "cursosRequeridos");
        }
        if ("perfil.ver_postulante".equals(action)) {
            // ENVIADA is the only persisted application state in RF6 stage 1.
            return Set.of("nombre", "apellido", "correo", "especializaciones");
        }
        if ("curriculum.descargar".equals(action) || "curriculum.subir".equals(action)) {
            return Set.of("id", "nombreOriginal", "tipoMime", "tamanioBytes", "creadoEn");
        }
        if ("postulacion.ver".equals(action)) {
            return Set.of("id", "ofertaId", "ofertaTitulo", "empresaNombre", "estado", "fecha");
        }
        if ("postulacion.ver_listado".equals(action)) {
            return Set.of("id", "estado", "fecha", "perfil");
        }
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
            case "oferta.gestionar" -> (resource instanceof Offer o && "BORRADOR".equals(o.estado()))
                || resource instanceof ResourceAccess ? Set.of("titulo", "descripcion", "cursosRequeridos") : Set.of();
            case "curriculum.subir" -> Set.of("archivo");
            case "postulacion.crear" -> Set.of();
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
