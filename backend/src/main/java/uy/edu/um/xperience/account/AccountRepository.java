package uy.edu.um.xperience.account;

import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.persistence.*;

/** Internal identity bootstrap for authentication/policy and offline provisioning, not API reads. */
@Repository
@Transactional(readOnly = true)
public class AccountRepository {
    private final Usuarios users;
    private final Membresias memberships;
    private final Empresas companies;
    public AccountRepository(Usuarios users, Membresias memberships, Empresas companies) {
        this.users = users; this.memberships = memberships; this.companies = companies;
    }
    public Optional<Account> byEmail(String email) { return users.findByCorreo(email).map(this::snapshot); }
    public Optional<Account> byId(UUID id) { return users.findById(id).map(this::snapshot); }
    public Account snapshot(Usuario user) {
        var m = memberships.findById(user.id).orElse(null);
        String companyName = m == null ? null : companies.findById(m.empresaId).map(e -> e.nombre).orElse(null);
        return new Account(user.id, user.correo, user.nombre, user.apellido, user.passwordHash, user.tipoCuenta,
            user.activo, m == null ? null : m.empresaId, companyName, m == null ? null : m.rol, m == null ? null : m.estado);
    }
    @Transactional
    public UUID create(String email, String name, String surname, String hash, String type) {
        var user = new Usuario();
        user.id = UUID.randomUUID(); user.correo = email; user.nombre = name; user.apellido = surname;
        user.passwordHash = hash; user.tipoCuenta = type;
        users.saveAndFlush(user);
        return user.id;
    }
}