package uy.edu.um.xperience.provision;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.account.RegisterRequest;

/**
 * Datos locales de catálogo para probar RF3/RF4 sin pasar por la UI de gestión.
 * Solo corre con el perfil {@code dev}. Es idempotente: no duplica si ya existen.
 */
@Component
@Profile("dev")
public class DevCatalogSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevCatalogSeed.class);
    private static final String MARKER = "Prueba";
    private static final String ADMIN_EMAIL = "admin-prueba@example.test";
    private static final String ADMIN_PASSWORD = "una frase de prueba segura";

    private final JdbcTemplate jdbc;
    private final CompanyProvisioner provisioner;

    public DevCatalogSeed(JdbcTemplate jdbc, CompanyProvisioner provisioner) {
        this.jdbc = jdbc;
        this.provisioner = provisioner;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM curso WHERE titulo LIKE ?", Integer.class, "%" + MARKER + "%");
        if (existing != null && existing > 0) {
            log.info("Seed de catálogo omitido: ya hay {} curso(s)/proyecto(s) con '{}'.", existing, MARKER);
            return;
        }

        UUID empresaId = firstCompanyId().orElseGet(this::createDemoCompany);
        UUID autorId = firstStaffOf(empresaId).orElseThrow();

        UUID cursoId = insertPublished(empresaId, autorId, "CURSO",
            "Curso Prueba Java", "Curso de prueba para inscripción y avance.", "Java", "INICIAL", 10, "0");
        jdbc.update("""
            INSERT INTO leccion (id, curso_id, empresa_id, numero, titulo, cuerpo)
            VALUES (?, ?, ?, 1, 'Lección Prueba', 'Contenido de prueba.')
            """, UUID.randomUUID(), cursoId, empresaId);

        UUID proyectoId = insertPublished(empresaId, autorId, "PROYECTO",
            "Proyecto Prueba API", "Proyecto de prueba para inscripción y avance.", "Spring", "INTERMEDIO", 20, "50.00");
        UUID entregableId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO entregable (id, curso_id, empresa_id, numero, titulo, consigna, pista)
            VALUES (?, ?, ?, 1, 'Entregable Prueba', 'Respondé ok', 'Es ok')
            """, entregableId, proyectoId, empresaId);
        jdbc.update("""
            INSERT INTO respuesta_aceptada (id, entregable_id, hash)
            VALUES (?, ?, ?)
            """, UUID.randomUUID(), entregableId, "{noop}placeholder");

        log.info("Seed de catálogo listo: '{}' y '{}' (empresa {}).",
            "Curso Prueba Java", "Proyecto Prueba API", empresaId);
        log.info("Si se creó empresa demo, admin: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private java.util.Optional<UUID> firstCompanyId() {
        List<UUID> ids = jdbc.query("SELECT id FROM empresa ORDER BY nombre LIMIT 1",
            (rs, row) -> rs.getObject(1, UUID.class));
        return ids.stream().findFirst();
    }

    private java.util.Optional<UUID> firstStaffOf(UUID empresaId) {
        List<UUID> ids = jdbc.query("""
            SELECT usuario_id FROM membresia
            WHERE empresa_id = ? AND estado = 'ACTIVA'
            ORDER BY CASE WHEN rol = 'ADMIN' THEN 0 ELSE 1 END
            LIMIT 1
            """, (rs, row) -> rs.getObject(1, UUID.class), empresaId);
        return ids.stream().findFirst();
    }

    private UUID createDemoCompany() {
        log.info("No hay empresas: creando 'Empresa Prueba' con admin {}.", ADMIN_EMAIL);
        return provisioner.create("Empresa Prueba",
            new RegisterRequest(ADMIN_EMAIL, "Admin", "Prueba", ADMIN_PASSWORD), "dev-seed");
    }

    private UUID insertPublished(UUID empresaId, UUID autorId, String tipo, String titulo,
                                 String descripcion, String tecnologia, String nivel,
                                 int horas, String costo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO curso (id, empresa_id, tipo, titulo, descripcion, tecnologia, nivel,
                               duracion_horas, costo, estado, creado_por)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLICADO', ?)
            """, id, empresaId, tipo, titulo, descripcion, tecnologia, nivel, horas,
            new BigDecimal(costo), autorId);
        return id;
    }
}
