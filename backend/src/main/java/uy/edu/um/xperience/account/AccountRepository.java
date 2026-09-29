package uy.edu.um.xperience.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
        SELECT u.*, m.empresa_id, m.rol, m.estado AS membresia_estado, e.nombre AS empresa_nombre
        FROM usuario u LEFT JOIN membresia m ON m.usuario_id = u.id
        LEFT JOIN empresa e ON e.id = m.empresa_id
        """;
    private static final RowMapper<Account> MAPPER = (rs, row) -> new Account(
        rs.getObject("id", UUID.class), rs.getString("correo"), rs.getString("nombre"),
        rs.getString("apellido"), rs.getString("password_hash"), rs.getString("tipo_cuenta"),
        rs.getBoolean("activo"), rs.getObject("empresa_id", UUID.class),
        rs.getString("empresa_nombre"), rs.getString("rol"), rs.getString("membresia_estado"));

    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Account> byEmail(String email) {
        return jdbc.query(SELECT + " WHERE u.correo = ?", MAPPER, email).stream().findFirst();
    }

    public Optional<Account> byId(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id = ?", MAPPER, id).stream().findFirst();
    }

    public boolean allowed(Account account, String action) {
        return account.canSignIn() && Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM permiso WHERE rol = ? AND accion = ?",
            Boolean.class, account.effectiveRole(), action));
    }

    public UUID create(String email, String name, String surname, String hash, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id, correo, nombre, apellido, password_hash, tipo_cuenta) VALUES (?, ?, ?, ?, ?, ?)",
            id, email, name, surname, hash, type);
        return id;
    }
}
