package uy.edu.um.xperience.provision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uy.edu.um.xperience.security.AlmacenRegistroSeguridad;

/**
 * RS19 — crea el almacén de registros de seguridad fuera de dev. Las credenciales de administración
 * llegan sólo a este comando (scripts/crear-registro-seguridad), nunca a la aplicación en ejecución.
 */
@Component
@Profile("provision-registro")
public class ProvisionRegistroSeguridadCommand implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    private final String url;
    private final String writerUser;

    public ProvisionRegistroSeguridadCommand(ConfigurableApplicationContext context,
                                             @Value("${app.security-log.url}") String url,
                                             @Value("${app.security-log.writer-user}") String writerUser) {
        this.context = context;
        this.url = url;
        this.writerUser = writerUser;
    }

    @Override
    public void run(ApplicationArguments args) {
        int result = 0;
        try {
            AlmacenRegistroSeguridad.provisionar(url, env("SECURITY_LOG_ADMIN_USER"), env("SECURITY_LOG_ADMIN_PASSWORD"),
                writerUser, env("SECURITY_LOG_WRITER_PASSWORD"));
            System.out.println("Almacén de registros de seguridad listo. El escritor sólo puede agregar eventos.");
        } catch (Exception error) {
            // Do not print SQL errors or credentials.
            System.err.println("No se pudo preparar el almacén. Revisá que las credenciales sean distintas, "
                + "que el administrador coincida con el que creó la base y que el backend esté detenido si no usa AUTO_SERVER.");
            result = 1;
        }
        final int exitCode = result;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private static String env(String key) { return System.getenv(key); }
}
