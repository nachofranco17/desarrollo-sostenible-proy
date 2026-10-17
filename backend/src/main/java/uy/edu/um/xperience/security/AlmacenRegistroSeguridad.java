package uy.edu.um.xperience.security;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * RS19 — almacén de solo agregado de los eventos de seguridad. Es una base separada de la de la
 * aplicación, y el proceso se conecta con un usuario que sólo tiene INSERT sobre la tabla de eventos:
 * no puede leerlos, modificarlos ni borrarlos, y ningún usuario de la aplicación tiene un camino para
 * hacerlo. Las credenciales de administración (auditoría) sólo se usan para aprovisionar el almacén.
 */
@Component
public class AlmacenRegistroSeguridad {
    static final String TABLA = "EVENTO_SEGURIDAD";
    private static final String NOMBRE_USUARIO = "[A-Za-z][A-Za-z0-9_]{0,63}";
    private static final Logger log = LoggerFactory.getLogger(AlmacenRegistroSeguridad.class);

    private final JdbcTemplate escritor;

    public AlmacenRegistroSeguridad(@Value("${app.security-log.url}") String url,
                                    @Value("${app.security-log.writer-user}") String writerUser,
                                    @Value("${app.security-log.writer-password:}") String writerPassword,
                                    @Value("${app.security-log.provision:false}") boolean provision,
                                    @Value("${app.security-log.admin-user:}") String adminUser,
                                    @Value("${app.security-log.admin-password:}") String adminPassword) {
        // Sólo dev y pruebas aprovisionan al iniciar; en otro entorno lo hace el script, fuera de la aplicación.
        if (provision) {
            try {
                provisionar(url, adminUser, adminPassword, writerUser, writerPassword);
            } catch (SQLException | RuntimeException error) {
                log.error("No se pudo preparar el almacén de registros de seguridad", error);
            }
        }
        // IFEXISTS: el escritor nunca crea la base, porque quien la crea queda como su administrador.
        this.escritor = new JdbcTemplate(new DriverManagerDataSource(url + ";IFEXISTS=TRUE", writerUser, writerPassword));
    }

    /** La única operación disponible para la aplicación. */
    public void agregar(String evento) {
        escritor.update("INSERT INTO " + TABLA + " (EVENTO) VALUES (?)", evento);
    }

    /**
     * Crea la tabla y deja al escritor con INSERT como único privilegio. Es idempotente y vuelve a
     * revocar cualquier otro privilegio que el escritor hubiera recibido.
     */
    public static void provisionar(String url, String adminUser, String adminPassword, String writerUser,
                                   String writerPassword) throws SQLException {
        if (isBlank(adminUser) || isBlank(adminPassword) || isBlank(writerPassword)
                || writerUser == null || !writerUser.matches(NOMBRE_USUARIO) || !adminUser.matches(NOMBRE_USUARIO)
                || adminUser.equalsIgnoreCase(writerUser) || adminPassword.equals(writerPassword)) {
            throw new IllegalArgumentException("Credenciales del almacén de registros incompletas o repetidas");
        }
        try (Connection connection = DriverManager.getConnection(url, adminUser, adminPassword);
             Statement sql = connection.createStatement()) {
            sql.execute("SET DB_CLOSE_DELAY -1");
            sql.execute("CREATE TABLE IF NOT EXISTS " + TABLA + " ("
                + "ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "REGISTRADO_EN TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                + "EVENTO VARCHAR(8000) NOT NULL)");
            sql.execute("CREATE USER IF NOT EXISTS " + writerUser + " PASSWORD " + literal(writerPassword));
            sql.execute("ALTER USER " + writerUser + " SET PASSWORD " + literal(writerPassword));
            sql.execute("ALTER USER " + writerUser + " ADMIN FALSE");
            sql.execute("REVOKE ALL ON " + TABLA + " FROM " + writerUser);
            sql.execute("GRANT INSERT ON " + TABLA + " TO " + writerUser);
        }
    }

    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
