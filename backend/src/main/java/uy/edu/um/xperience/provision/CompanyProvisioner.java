package uy.edu.um.xperience.provision;

import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.account.*;

@Service
public class CompanyProvisioner {
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Validator validator;

    public CompanyProvisioner(JdbcTemplate jdbc, AccountRepository accounts, PasswordEncoder passwords, Validator validator) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.passwords = passwords;
        this.validator = validator;
    }

    @Transactional
    public UUID create(String companyName, RegisterRequest admin, String actor) {
        if (companyName == null || companyName.isBlank() || companyName.strip().length() > 160
                || actor == null || actor.isBlank() || actor.length() > 160
                || !validator.validate(admin).isEmpty()) {
            throw new IllegalArgumentException("Datos de alta inválidos; contraseña de 12 a 128 caracteres.");
        }
        if (accounts.byEmail(admin.correo()).isPresent()) {
            throw new IllegalArgumentException("No se puede realizar el alta con esos datos.");
        }
        UUID company = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa (id, nombre) VALUES (?, ?)", company, companyName.strip());
        UUID adminId = accounts.create(admin.correo(), admin.nombre(), admin.apellido(),
            passwords.encode(admin.password()), "STAFF");
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, 'ADMIN', 'ACTIVA')",
            adminId, company);
        jdbc.update("INSERT INTO alta_empresa(id, empresa_id, administrador_id, ejecutor) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), company, adminId, actor);
        return company;
    }
}
