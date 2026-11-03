package uy.edu.um.xperience;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uy.edu.um.xperience.security.AlmacenRegistroSeguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlmacenRegistroSeguridadProvisionTest {

    @Test
    void bindsThePasswordInsteadOfAddingItToSql() throws Exception {
        String url = "jdbc:h2:mem:rs19-" + UUID.randomUUID();
        String writerPassword = "segura'; DROP TABLE EVENTO_SEGURIDAD; --";

        AlmacenRegistroSeguridad.provisionar(
            url, "auditor_seguro", "clave-del-auditor", "writer_safe", writerPassword);

        try (Connection writer = DriverManager.getConnection(url + ";IFEXISTS=TRUE", "writer_safe", writerPassword);
             PreparedStatement insert = writer.prepareStatement(
                 "INSERT INTO EVENTO_SEGURIDAD (EVENTO) VALUES (?)")) {
            insert.setString(1, "evento intacto");
            assertThat(insert.executeUpdate()).isOne();
        }

        try (Connection auditor = DriverManager.getConnection(
                url + ";IFEXISTS=TRUE", "auditor_seguro", "clave-del-auditor");
             Statement sql = auditor.createStatement();
             ResultSet result = sql.executeQuery("SELECT COUNT(*) FROM EVENTO_SEGURIDAD")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isOne();
        }
    }

    @Test
    void rejectsAUserNameContainingSqlBeforeOpeningTheDatabase() {
        assertThatThrownBy(() -> AlmacenRegistroSeguridad.provisionar(
            "jdbc:h2:mem:rs19-injection-" + UUID.randomUUID(),
            "auditor_seguro", "clave-del-auditor", "writer; DROP TABLE EVENTO_SEGURIDAD", "clave-writer"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
