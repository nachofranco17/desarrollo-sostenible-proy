package uy.edu.um.xperience.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import uy.edu.um.xperience.account.*;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfiguration {
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) {
        return email -> accounts.byEmail(RegisterRequest.normalizeEmail(email))
            .map(a -> User.withUsername(a.id().toString()).password(a.passwordHash())
                .authorities("SESSION").disabled(!a.canSignIn()).build())
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, UserDetailsService users, PasswordEncoder passwords,
            PolicyEnforcementPoint pep, Sujetos subjects, EvaluadorPolitica policy, Denegaciones denials,
            ObjectMapper json, SessionLifetime lifetime) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        http.authenticationProvider(provider);
        http.addFilterBefore(new SessionPolicyFilter(subjects, policy, denials, json, lifetime), LogoutFilter.class);
        // CSRF stays enabled, including registration, login, reauthentication and logout.
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/api/**").access(pep)
            .anyRequest().denyAll());
        http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
            .usernameParameter("correo").passwordParameter("password")
            .successHandler((request, response, authentication) -> {
                lifetime.apply(request.getSession(), subjects.resolve(authentication));
                response.setStatus(204);
            })
            .failureHandler((request, response, error) -> {
                denials.registrar(request);
                Denegaciones.json(response, 401, "Correo o contraseña incorrectos.");
            }));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
            .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SESSION")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, error) -> denials.responder(request, response, 401))
            .accessDeniedHandler((request, response, error) -> denials.responder(request, response, 403)));
        http.requestCache(cache -> cache.disable());
        return http.build();
    }
}
