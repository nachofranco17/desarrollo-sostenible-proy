package uy.edu.um.xperience;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class PolicyMigrationTest {
    @Test void upgradingThePreviousSchemaPreservesAccountsAndMemberships() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:upgrade-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).target("3").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        UUID company = UUID.randomUUID(); UUID admin = UUID.randomUUID(); UUID member = UUID.randomUUID();
        jdbc.update("INSERT INTO empresa(id, nombre) VALUES (?, 'Existing company')", company);
        for (UUID id : new UUID[]{admin, member}) {
            jdbc.update("INSERT INTO usuario(id, correo, nombre, apellido, password_hash, tipo_cuenta) VALUES (?, ?, 'Existing', 'User', 'existing-hash', 'STAFF')",
                id, id + "@example.test");
        }
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, rol, estado) VALUES (?, ?, 'ADMIN', 'ACTIVA')", admin, company);
        jdbc.update("INSERT INTO membresia(usuario_id, empresa_id, estado) VALUES (?, ?, 'ACTIVA')", member, company);
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, admin)).isEqualTo("existing-hash");
        assertThat(jdbc.queryForObject("SELECT rol FROM membresia WHERE usuario_id = ?", String.class, admin)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT rol FROM membresia WHERE usuario_id = ?", String.class, member)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permiso", Integer.class)).isEqualTo(54);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permiso WHERE rol = 'AUTENTICADO' OR accion = 'cuenta.ver'", Integer.class)).isZero();
    }
}
