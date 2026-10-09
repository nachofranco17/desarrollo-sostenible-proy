package uy.edu.um.xperience;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.RegistroAccesos;
import uy.edu.um.xperience.security.Sujeto;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RS18 — si el registro de denegaciones falla, el acceso sigue denegado (nunca fail-open).
 */
@SpringBootTest
@AutoConfigureMockMvc
class Rs18LoggerFailureTest {
    @Autowired MockMvc mvc;
    @Autowired CompanyProvisioner provisioner;
    @Autowired AccountRepository accounts;
    @MockitoBean RegistroAccesos registry;

    @Test
    void loggingFailureDoesNotGrantAccess() throws Exception {
        doThrow(new RuntimeException("audit unavailable"))
            .when(registry).denegado(any(), anyString(), anyString());

        String email = UUID.randomUUID() + "@example.test";
        provisioner.create("RS18 logger fail",
            new RegisterRequest(email, "Admin", "Test", "una frase suficientemente larga"), "tests");
        Sujeto subject = Sujeto.from(accounts.byEmail(email).orElseThrow());

        // Ruta sin handler de negocio: el PEP deniega. El registro explota y aun así debe quedar 403.
        mvc.perform(get("/api/profile/" + UUID.randomUUID()).with(user(subject.usuarioId().toString())))
            .andExpect(status().isForbidden());
    }
}
