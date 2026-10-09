package uy.edu.um.xperience.staff;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.persistence.*;
import uy.edu.um.xperience.security.*;

@Service
public class StaffRoleService {
    private final Membresias memberships;
    private final EvaluadorPolitica policy;
    public StaffRoleService(Membresias memberships, EvaluadorPolitica policy) {
        this.memberships = memberships; this.policy = policy;
    }
    @Transactional
    public void deactivate(Sujeto subject, UUID memberId) {
        try {
            var permitted = policy.filtrar(subject, "staff.dar_baja", Membresia.class);
            var member = memberships.findOne(permitted.and((root, query, cb) -> cb.equal(root.get("usuarioId"), memberId)))
                .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
            if (!policy.autorizar(subject, "staff.dar_baja", member)) throw new AccessDeniedException("Acceso denegado");
            member.estado = "BAJA";
            memberships.saveAndFlush(member);
        } catch (DataAccessException error) { throw new AccessDeniedException("Acceso denegado", error); }
    }
    @Transactional
    public void assign(Sujeto subject, UUID memberId, StaffRole role) {
        if (role == null || memberId == null) throw new AccessDeniedException("Acceso denegado");
        try {
            var permitted = policy.filtrar(subject, "staff.cambiar_rol", Membresia.class);
            var member = memberships.findOne(permitted.and((root, query, cb) -> cb.equal(root.get("usuarioId"), memberId)))
                .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
            if (!policy.camposEscribibles(subject, "staff.cambiar_rol", member).contains("rol")) {
                throw new AccessDeniedException("Acceso denegado");
            }
            member.rol = role.name();
            memberships.saveAndFlush(member);
        } catch (DataAccessException error) { throw new AccessDeniedException("Acceso denegado", error); }
    }
}
