package uy.edu.um.xperience;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * RS19 — lee el almacén de registros de seguridad con las credenciales de auditoría, el único
 * destino de los eventos de RS18. Las pruebas lo usan en lugar de capturar un logger.
 */
final class SecurityLogAuditor {
    private final String url;
    private final String user;
    private final String password;

    SecurityLogAuditor(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    long lastId() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COALESCE(MAX(ID), 0) FROM EVENTO_SEGURIDAD")) {
            r.next();
            return r.getLong(1);
        }
    }

    List<String> eventsAfter(long id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement s = c.prepareStatement("SELECT EVENTO FROM EVENTO_SEGURIDAD WHERE ID > ? ORDER BY ID")) {
            s.setLong(1, id);
            var events = new ArrayList<String>();
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) events.add(r.getString(1));
            }
            return events;
        }
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url + ";IFEXISTS=TRUE", user, password);
    }
}
