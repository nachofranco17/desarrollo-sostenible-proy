package uy.edu.um.xperience;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import uy.edu.um.xperience.persistence.PrivilegiosBaseDatos;
import static org.assertj.core.api.Assertions.*;

/**
 * Mínimo privilegio en la base principal (Práctica 16, sección 5.4). La aplicación atiende las
 * solicitudes con un usuario que sólo manipula datos. Aunque alguien lograra ejecutar SQL con sus
 * credenciales (por ejemplo, con una inyección), no podría cambiar el esquema, administrar usuarios,
 * leer archivos del servidor, ejecutar código ni darse permisos escribiendo en la tabla permiso.
 */
@SpringBootTest
class DatabaseLeastPrivilegeTest {
    private static final Set<String> DATOS = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

    @Autowired DataSource application;
    @Value("${spring.flyway.url}") String ownerUrl;
    @Value("${spring.flyway.user}") String ownerUser;
    @Value("${spring.flyway.password}") String ownerPassword;
    @Value("${spring.datasource.username}") String appUser;
    @Value("${spring.datasource.password}") String appPassword;

    @Test
    void theApplicationConnectsWithItsOwnUserNotTheSchemaOwner() throws SQLException {
        try (Connection app = application.getConnection()) {
            assertThat(app.getMetaData().getUserName()).isEqualToIgnoringCase(appUser).isNotEqualToIgnoringCase(ownerUser);
        }
        try (Connection owner = owner(); Statement sql = owner.createStatement();
             ResultSet row = sql.executeQuery("SELECT IS_ADMIN FROM INFORMATION_SCHEMA.USERS WHERE UPPER(USER_NAME) = '"
                 + appUser.toUpperCase(Locale.ROOT) + "'")) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBoolean(1)).as("el usuario de la aplicación no es administrador").isFalse();
        }
    }

    @Test
    void theApplicationCannotChangeTheSchemaOrAdministerTheDatabase() throws SQLException {
        assertDeniedForApplication(
            "DROP TABLE usuario",
            "ALTER TABLE usuario ADD COLUMN es_admin BOOLEAN",
            "CREATE TABLE copia AS SELECT * FROM usuario",
            "CREATE INDEX ix_robado ON usuario(password_hash)",
            "CREATE USER otro PASSWORD 'otro-usuario'",
            "ALTER USER " + appUser + " ADMIN TRUE",
            "GRANT ALL ON permiso TO " + appUser,
            // Sin estos dos, una inyección SQL alcanzaría el sistema de archivos y la JVM del servidor.
            "CALL FILE_READ('pom.xml')",
            "CREATE ALIAS propiedad FOR 'java.lang.System.getProperty'",
            "SCRIPT TO 'copia.sql'");
    }

    @Test
    void thePermissionCatalogIsReadOnlySoNoOneCanGrantThemselvesActions() throws SQLException {
        long before = count("permiso");
        assertThat(before).isPositive();
        assertDeniedForApplication(
            "INSERT INTO permiso (rol, accion, alcance) VALUES ('TALENTO', 'staff.cambiar_rol', 'ORG')",
            "UPDATE permiso SET rol = 'TALENTO' WHERE rol = 'ADMIN'",
            "DELETE FROM permiso",
            // H2 trata TRUNCATE como un DELETE de todas las filas: requiere DELETE, que acá no hay.
            "TRUNCATE TABLE permiso");
        assertThat(count("permiso")).isEqualTo(before);
    }

    @Test
    void companyCreationRecordsAreAppendOnly() throws SQLException {
        count("alta_empresa");
        assertDeniedForApplication(
            "UPDATE alta_empresa SET ejecutor = 'otra persona'",
            "DELETE FROM alta_empresa");
    }

    @Test
    void migrationHistoryIsOutOfReach() throws SQLException {
        assertDeniedForApplication(
            "SELECT * FROM flyway_schema_history",
            "DELETE FROM flyway_schema_history");
    }

    @Test
    void everyTableHasExactlyThePrivilegesTheApplicationNeeds() throws SQLException {
        var expected = new TreeMap<String, Set<String>>();
        var actual = new TreeMap<String, Set<String>>();
        try (Connection owner = owner(); Statement sql = owner.createStatement()) {
            try (ResultSet rows = sql.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND TABLE_TYPE = 'BASE TABLE'")) {
                while (rows.next()) {
                    String table = rows.getString(1).toLowerCase(Locale.ROOT);
                    expected.put(table, switch (table) {
                        case "permiso" -> Set.of("SELECT");
                        case "alta_empresa" -> Set.of("SELECT", "INSERT");
                        case "flyway_schema_history" -> Set.of();
                        default -> DATOS;
                    });
                    actual.put(table, new TreeSet<>());
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT TABLE_NAME, PRIVILEGE_TYPE FROM INFORMATION_SCHEMA.TABLE_PRIVILEGES "
                    + "WHERE UPPER(GRANTEE) = '" + appUser.toUpperCase(Locale.ROOT) + "' AND TABLE_SCHEMA = CURRENT_SCHEMA")) {
                while (rows.next()) actual.get(rows.getString(1).toLowerCase(Locale.ROOT)).add(rows.getString(2));
            }
        }
        assertThat(expected).containsKeys("usuario", "membresia", "permiso", "postulacion", "spring_session");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void provisioningRevokesPrivilegesGrantedByHand() throws SQLException {
        try (Connection owner = owner(); Statement sql = owner.createStatement()) {
            sql.execute("GRANT INSERT, DELETE ON permiso TO " + appUser);
            sql.execute("ALTER USER " + appUser + " ADMIN TRUE");
            PrivilegiosBaseDatos.provisionar(owner, appUser, appPassword);
        }
        assertDeniedForApplication(
            "INSERT INTO permiso (rol, accion, alcance) VALUES ('TALENTO', 'staff.cambiar_rol', 'ORG')",
            "CREATE USER otro PASSWORD 'otro-usuario'");
    }

    @Test
    void provisioningRejectsRunningTheApplicationAsTheOwnerOrWithoutPassword() throws SQLException {
        try (Connection owner = owner()) {
            assertThatThrownBy(() -> PrivilegiosBaseDatos.provisionar(owner, ownerUser, "una-clave"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PrivilegiosBaseDatos.provisionar(owner, appUser, " "))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PrivilegiosBaseDatos.provisionar(owner, "x; DROP TABLE usuario", "una-clave"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---- Apoyo ----

    private void assertDeniedForApplication(String... attempts) throws SQLException {
        try (Connection app = application.getConnection(); Statement sql = app.createStatement()) {
            for (String attempt : attempts) {
                // 90096 NOT_ENOUGH_RIGHTS / 90040 ADMIN_RIGHTS_REQUIRED: falla por permisos, no por sintaxis
                // ni por un objeto inexistente.
                assertThatThrownBy(() -> sql.execute(attempt)).as(attempt).isInstanceOfSatisfying(SQLException.class,
                    error -> assertThat(error.getErrorCode()).as(attempt + ": " + error.getMessage()).isIn(90096, 90040));
            }
        }
    }

    private long count(String table) throws SQLException {
        try (Connection app = application.getConnection(); Statement sql = app.createStatement();
             ResultSet row = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
            row.next();
            return row.getLong(1);
        }
    }

    private Connection owner() throws SQLException { return DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword); }
}
