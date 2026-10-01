package uy.edu.um.xperience;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Reuses the full real-HTTP suite on PostgreSQL when Docker is available. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresAuthIntegrationTest extends AuthIntegrationTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }
}
