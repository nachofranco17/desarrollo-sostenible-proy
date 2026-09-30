package uy.edu.um.xperience.provision;

import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.account.*;
import uy.edu.um.xperience.persistence.*;

@Service
public class CompanyProvisioner {
    private final Empresas companies;
    private final Membresias memberships;
    private final AltasEmpresa audit;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Validator validator;
    public CompanyProvisioner(Empresas companies, Membresias memberships, AltasEmpresa audit,
            AccountRepository accounts, PasswordEncoder passwords, Validator validator) {
        this.companies = companies; this.memberships = memberships; this.audit = audit;
        this.accounts = accounts; this.passwords = passwords; this.validator = validator;
    }
    @Transactional
    public UUID create(String companyName, RegisterRequest admin, String actor) {
        if (companyName == null || companyName.isBlank() || companyName.strip().length() > 160
                || actor == null || actor.isBlank() || actor.length() > 160 || admin == null
                || !validator.validate(admin).isEmpty()) {
            throw new IllegalArgumentException("Datos de alta inválidos; contraseña de 12 a 128 caracteres.");
        }
        if (accounts.byEmail(admin.correo()).isPresent()) throw new IllegalArgumentException("No se puede realizar el alta con esos datos.");
        var company = new Empresa(); company.id = UUID.randomUUID(); company.nombre = companyName.strip();
        companies.saveAndFlush(company);
        UUID adminId = accounts.create(admin.correo(), admin.nombre(), admin.apellido(), passwords.encode(admin.password()), "STAFF");
        var membership = new Membresia();
        membership.usuarioId = adminId; membership.empresaId = company.id; membership.rol = "ADMIN"; membership.estado = "ACTIVA";
        memberships.saveAndFlush(membership);
        var event = new AltaEmpresa();
        event.id = UUID.randomUUID(); event.empresaId = company.id; event.administradorId = adminId; event.ejecutor = actor;
        audit.saveAndFlush(event);
        return company.id;
    }
}