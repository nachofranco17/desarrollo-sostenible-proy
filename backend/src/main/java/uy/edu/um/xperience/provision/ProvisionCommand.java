package uy.edu.um.xperience.provision;

import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uy.edu.um.xperience.account.RegisterRequest;

@Component
@Profile("provision")
public class ProvisionCommand implements ApplicationRunner {
    private final CompanyProvisioner provisioner;
    private final ConfigurableApplicationContext context;

    public ProvisionCommand(CompanyProvisioner provisioner, ConfigurableApplicationContext context) {
        this.provisioner = provisioner;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int result = 0;
        try {
            var admin = new RegisterRequest(env("ADMIN_EMAIL"), env("ADMIN_NAME"),
                env("ADMIN_SURNAME"), env("ADMIN_PASSWORD"));
            var id = provisioner.create(env("COMPANY_NAME"), admin, env("PROVISION_ACTOR"));
            System.out.println("Empresa y Administrador creados. Empresa: " + id);
        } catch (RuntimeException error) {
            // Do not print SQL errors, credentials or personal data.
            System.err.println("Alta rechazada. Revisá los datos, la conexión y que el correo esté disponible. No se aplicaron cambios parciales.");
            result = 1;
        }
        final int exitCode = result;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private static String env(String key) { return System.getenv(key); }
}
