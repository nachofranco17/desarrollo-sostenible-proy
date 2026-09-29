package uy.edu.um.xperience.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import uy.edu.um.xperience.account.*;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfiguration {
    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return email -> accounts.byEmail(RegisterRequest.normalizeEmail(email))
            .map(a -> User.withUsername(a.id().toString()).password(a.passwordHash())
                .authorities("SESSION").disabled(!a.canSignIn()).build())
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService users,
                                 PasswordEncoder passwords, AccountRepository accounts) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        http.authenticationProvider(provider);
        // CSRF remains enabled, including registration, login and logout.
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").anonymous()
            .requestMatchers(HttpMethod.GET, "/api/account").access((authentication, context) -> {
                var current = authentication.get();
                if (current == null || !current.isAuthenticated() || "anonymousUser".equals(current.getName())) {
                    return new AuthorizationDecision(false);
                }
                // Session contains identity only: account, membership and permission are re-read.
                var account = accounts.byId(UUID.fromString(current.getName()));
                boolean allowed = account.map(a -> accounts.allowed(a, "cuenta.ver")).orElse(false);
                if (!allowed && context.getRequest().getSession(false) != null) {
                    context.getRequest().getSession(false).invalidate();
                }
                return new AuthorizationDecision(allowed);
            })
            .anyRequest().denyAll());
        http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
            .usernameParameter("correo").passwordParameter("password")
            .successHandler((request, response, authentication) -> response.setStatus(204))
            .failureHandler((request, response, error) -> jsonError(response, 401, "Correo o contraseña incorrectos.")));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
            .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SESSION")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, error) -> jsonError(response, 401, "Iniciá sesión para continuar."))
            .accessDeniedHandler((request, response, error) -> jsonError(response, 403, "Acceso denegado.")));
        http.requestCache(cache -> cache.disable());
        return http.build();
    }

    private static void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
