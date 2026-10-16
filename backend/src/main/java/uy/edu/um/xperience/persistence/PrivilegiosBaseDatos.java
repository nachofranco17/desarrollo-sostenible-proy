package uy.edu.um.xperience.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

/**
 * Mínimo privilegio en la base principal. Flyway migra con el usuario dueño del esquema; al terminar,
 * se deja al usuario con el que la aplicación atiende solicitudes sólo con las operaciones de datos
 * que necesita. No puede crear, alterar ni borrar tablas, administrar usuarios, leer archivos del
 * servidor ni ejecutar código en la base. Es idempotente: en cada arranque vuelve a revocar cualquier
 * privilegio extra que alguien le hubiera dado.
 */
@Component
public class PrivilegiosBaseDatos implements FlywayMigrationStrategy {
    private static final Logger log = LoggerFactory.getLogger(PrivilegiosBaseDatos.class);
    private static final String NOMBRE_USUARIO = "[A-Za-z][A-Za-z0-9_]{0,63}";
    static final String DATOS = "SELECT, INSERT, UPDATE, DELETE";
    /** Tablas que reciben menos que {@link #DATOS}; el resto de las tablas del esquema recibe DATOS. */
    static final Map<String, String> EXCEPCIONES = Map.of(
        // El catálogo de permisos sólo cambia por migraciones: la aplicación no puede darse acciones.
        "permiso", "SELECT",
        // Constancia del alta de empresas: se agrega, nunca se corrige ni se borra.
        "alta_empresa", "SELECT, INSERT",
        // Sólo le interesa a Flyway.
        "flyway_schema_history", "");

    private final DataSource aplicacion;
    private final String usuario;
    private final String password;

    public PrivilegiosBaseDatos(DataSource aplicacion,
                                @Value("${spring.datasource.username:}") String usuario,
                                @Value("${spring.datasource.password:}") String password) {
        this.aplicacion = aplicacion;
        this.usuario = usuario;
        this.password = password;
    }

    @Override public void migrate(Flyway flyway) {
        // La base H2 en archivo no puede quedar sin conexiones entre la migración y el arranque: al
        // cerrarse la compacta, y H2 2.3.232 descarta así lo que acaban de aplicar migraciones como V9
        // (y el usuario). La conexión del dueño la sostiene hasta que el pool de la aplicación está
        // abierto; antes lo hacía el pool mismo, porque Flyway lo usaba como sa.
        try (Connection dueno = flyway.getConfiguration().getDataSource().getConnection()) {
            flyway.migrate();
            provisionar(dueno, usuario, password);
            // También comprueba que el usuario limitado puede entrar, antes de que lo necesite JPA.
            try (Connection app = aplicacion.getConnection()) {
                log.debug("Conexión de la aplicación como {}", app.getMetaData().getUserName());
            }
        } catch (SQLException | IllegalArgumentException error) {
            // Sin privilegios acotados la aplicación no arranca: nunca sigue con un usuario sin ajustar.
            throw new IllegalStateException("No se pudieron ajustar los privilegios del usuario de la aplicación", error);
        }
    }

    /** {@code dueno} es una conexión del dueño del esquema, con las mismas credenciales que usa Flyway. */
    public static void provisionar(Connection dueno, String usuario, String password) throws SQLException {
        String duenoNombre = dueno.getMetaData().getUserName();
        if (usuario == null || !usuario.matches(NOMBRE_USUARIO) || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Falta el usuario o la contraseña de la aplicación (DB_USER, DB_PASSWORD)");
        }
        if (usuario.equalsIgnoreCase(duenoNombre)) {
            throw new IllegalArgumentException("La aplicación no puede usar el mismo usuario que migra el esquema");
        }
        // H2 pasa a mayúsculas el usuario con el que se conecta; se crea igual para que coincidan.
        String quien = '"' + usuario.toUpperCase(Locale.ROOT) + '"';
        try (Statement sql = dueno.createStatement()) {
            sql.execute("CREATE USER IF NOT EXISTS " + quien + " PASSWORD " + literal(password));
            sql.execute("ALTER USER " + quien + " SET PASSWORD " + literal(password));
            sql.execute("ALTER USER " + quien + " ADMIN FALSE");
            for (String tabla : tablas(sql)) {
                String tablaSql = '"' + tabla.replace("\"", "\"\"") + '"';
                sql.execute("REVOKE ALL ON " + tablaSql + " FROM " + quien);
                String privilegios = EXCEPCIONES.getOrDefault(tabla.toLowerCase(Locale.ROOT), DATOS);
                if (!privilegios.isEmpty()) sql.execute("GRANT " + privilegios + " ON " + tablaSql + " TO " + quien);
            }
        }
        if (!dueno.getAutoCommit()) dueno.commit();
        log.info("Usuario de la aplicación {} limitado a operaciones de datos", usuario);
    }

    private static ArrayList<String> tablas(Statement sql) throws SQLException {
        var tablas = new ArrayList<String>();
        try (ResultSet rows = sql.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND TABLE_TYPE = 'BASE TABLE'")) {
            while (rows.next()) tablas.add(rows.getString(1));
        }
        return tablas;
    }

    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
}
