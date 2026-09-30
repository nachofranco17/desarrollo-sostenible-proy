package uy.edu.um.xperience.security;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration
public class RegistroAccesosConfiguration {
    // R18 storage is separate. All enforcement points already invoke this interface.
    @Bean @ConditionalOnMissingBean(RegistroAccesos.class)
    RegistroAccesos registroAccesos() { return (sujeto, accion, recurso) -> {}; }
}
