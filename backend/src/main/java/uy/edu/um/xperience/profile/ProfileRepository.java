package uy.edu.um.xperience.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<TalentProfile> mapper;

    public ProfileRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = (rs, row) -> new TalentProfile(
            rs.getObject("usuario_id", UUID.class),
            rs.getString("correo"),
            rs.getString("nombre"),
            rs.getString("apellido"),
            rs.getString("telefono"),
            readSpecializations(rs.getString("especializaciones")),
            rs.getBoolean("notificar_novedades_cursos"),
            rs.getBoolean("notificar_ofertas"));
    }

    public void createEmpty(UUID usuarioId) {
        jdbc.update("INSERT INTO perfil_talento(usuario_id) VALUES (?)", usuarioId);
    }

    public Optional<TalentProfile> byUsuarioId(UUID usuarioId) {
        return jdbc.query("""
            SELECT u.id AS usuario_id, u.correo, u.nombre, u.apellido,
                   p.telefono, p.especializaciones,
                   p.notificar_novedades_cursos, p.notificar_ofertas
            FROM usuario u
            INNER JOIN perfil_talento p ON p.usuario_id = u.id
            WHERE u.id = ?
            """, mapper, usuarioId).stream().findFirst();
    }

    public void update(UUID usuarioId, String nombre, String apellido, String correo,
                       String telefono, List<String> especializaciones,
                       boolean notificarNovedadesCursos, boolean notificarOfertas) {
        jdbc.update("UPDATE usuario SET nombre = ?, apellido = ?, correo = ? WHERE id = ?",
            nombre, apellido, correo, usuarioId);
        jdbc.update("""
            UPDATE perfil_talento
            SET telefono = ?, especializaciones = ?,
                notificar_novedades_cursos = ?, notificar_ofertas = ?
            WHERE usuario_id = ?
            """, telefono, writeSpecializations(especializaciones),
            notificarNovedadesCursos, notificarOfertas, usuarioId);
    }

    public void updatePassword(UUID usuarioId, String passwordHash) {
        jdbc.update("UPDATE usuario SET password_hash = ? WHERE id = ?", passwordHash, usuarioId);
    }

    public boolean emailTakenByOther(String correo, UUID usuarioId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM usuario WHERE correo = ? AND id <> ?",
            Integer.class, correo, usuarioId);
        return count != null && count > 0;
    }

    private List<String> readSpecializations(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return List.copyOf(json.readValue(raw, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }

    private String writeSpecializations(List<String> values) {
        try {
            return json.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudieron guardar las especializaciones", error);
        }
    }
}
