package uy.edu.um.xperience.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistroAccesosConfiguration {
    // RS18 emits structured denial events via RegistroAccesosEstructurado.
    // This fallback only applies if that component is absent (e.g. replaced in a test).
    @Bean
    @ConditionalOnMissingBean(RegistroAccesos.class)
    RegistroAccesos registroAccesos() {
        return (sujeto, accion, recurso) -> {};
    }
}
