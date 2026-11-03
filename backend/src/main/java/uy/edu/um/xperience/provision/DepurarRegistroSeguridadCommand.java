package uy.edu.um.xperience.provision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uy.edu.um.xperience.security.AlmacenRegistroSeguridad;

/**
 * RS19 — aplica la política de retención del almacén de registros de seguridad. Las credenciales de
 * administración llegan sólo a este comando (scripts/depurar-registro-seguridad), nunca a la aplicación.
 */
@Component
@Profile("depurar-registro")
public class DepurarRegistroSeguridadCommand implements ApplicationRunner {
    private final ConfigurableApplicationContext context;
    private final String url;
    private final int dias;

    public DepurarRegistroSeguridadCommand(ConfigurableApplicationContext context,
                                           @Value("${app.security-log.url}") String url,
                                           @Value("${app.security-log.retention-days}") int dias) {
        this.context = context;
        this.url = url;
        this.dias = dias;
    }

    @Override
    public void run(ApplicationArguments args) {
        int result = 0;
        try {
            int borrados = AlmacenRegistroSeguridad.depurar(url, env("SECURITY_LOG_ADMIN_USER"),
                env("SECURITY_LOG_ADMIN_PASSWORD"), dias);
            System.out.println("Se borraron " + borrados + " eventos con más de " + dias + " días.");
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            result = 1;
        } catch (Exception error) {
            // Do not print SQL errors or credentials.
            System.err.println("No se pudo depurar el almacén. Revisá que las credenciales sean las del administrador "
                + "y que el backend esté detenido si no usa AUTO_SERVER.");
            result = 1;
        }
        final int exitCode = result;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private static String env(String key) { return System.getenv(key); }
}
