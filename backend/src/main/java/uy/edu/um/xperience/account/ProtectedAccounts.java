package uy.edu.um.xperience.account;

import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.persistence.*;
import uy.edu.um.xperience.security.*;

@Service
@Transactional(readOnly = true)
public class ProtectedAccounts {
    private final Usuarios users;
    private final Empresas companies;
    private final EvaluadorPolitica policy;
    public ProtectedAccounts(Usuarios users, Empresas companies, EvaluadorPolitica policy) {
        this.users = users; this.companies = companies; this.policy = policy;
    }
    public Usuario read(Sujeto subject, String action) {
        try {
            return users.findOne(policy.filtrar(subject, action, Usuario.class))
                .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
        } catch (DataAccessException error) { throw new AccessDeniedException("Acceso denegado", error); }
    }
    public AccountView view(Sujeto subject) {
        Usuario user = read(subject, "cuenta.gestionar");
        if (!policy.camposLegibles(subject, "cuenta.gestionar", user).containsAll(
                Set.of("id", "correo", "nombre", "apellido", "tipoCuenta", "rol", "empresaId", "empresaNombre"))) {
            throw new AccessDeniedException("Acceso denegado");
        }
        try {
            String companyName = subject.empresaId() == null ? null
                : companies.findOne(policy.filtrar(subject, "cuenta.gestionar", Empresa.class))
                    .orElseThrow(() -> new AccessDeniedException("Acceso denegado")).nombre;
            return new AccountView(user.id, user.correo, user.nombre, user.apellido, user.tipoCuenta,
                subject.rol().name(), subject.empresaId(), companyName);
        } catch (DataAccessException error) { throw new AccessDeniedException("Acceso denegado", error); }
    }
    @Transactional
    public void delete(Sujeto subject) {
        var user = read(subject, "cuenta.gestionar");
        if (!policy.autorizar(subject, "cuenta.gestionar", user)) {
            throw new AccessDeniedException("Acceso denegado");
        }
        // Logical deletion preserves references and revokes every session on its next request.
        user.activo = false;
        users.saveAndFlush(user);
    }
}
