package uy.edu.um.xperience;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.provision.CompanyProvisioner;
import uy.edu.um.xperience.security.Sujeto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RS19 — sin almacén (no aprovisionado o caído), la denegación se mantiene, el escritor no crea una
 * base propia y la falla queda en el log del servidor sin credenciales.
 */
@SpringBootTest(properties = {
    "app.security-log.provision=false",
    "app.security-log.url=jdbc:h2:mem:registros-no-aprovisionados",
    "app.security-log.writer-password=clave-del-escritor-rs19"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class Rs19StoreUnavailableTest {
    @Autowired MockMvc mvc;
    @Autowired CompanyProvisioner provisioner;
    @Autowired AccountRepository accounts;

    @Test
    void denialStillHappensWhenTheStoreIsUnavailable(CapturedOutput output) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        provisioner.create("RS19 sin almacén",
            new RegisterRequest(email, "Admin", "Test", "una frase suficientemente larga"), "tests");
        Sujeto subject = Sujeto.from(accounts.byEmail(email).orElseThrow());

        mvc.perform(get("/api/profile/" + UUID.randomUUID()).with(user(subject.usuarioId().toString())))
            .andExpect(status().isForbidden());

        assertThat(output.getOut() + output.getErr())
            .contains("No se pudo agregar el evento al almacén de registros de seguridad")
            .doesNotContain("clave-del-escritor-rs19");
    }
}
