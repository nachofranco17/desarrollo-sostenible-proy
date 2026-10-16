package uy.edu.um.xperience.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.*;
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
            ObjectMapper json, SessionLifetime lifetime, Validator validator, ApplicationContext application) throws Exception {
        var manager = new ProviderManager(new CharArrayAuthenticationProvider(users, passwords));
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(application));
        http.authenticationManager(manager);
        var contexts = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
        http.securityContext(context -> context.securityContextRepository(contexts));
        http.addFilterBefore(new SessionPolicyFilter(subjects, policy, denials, json, lifetime), LogoutFilter.class);
        // CSRF stays enabled, including registration, login, reauthentication and logout.
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/api/**").access(pep)
            .anyRequest().denyAll());
        var login = new JsonLoginAuthenticationFilter(json, validator);
        login.setSecurityContextRepository(contexts);
        login.setApplicationEventPublisher(application);
        login.setAuthenticationSuccessHandler((request, response, authentication) -> {
                lifetime.apply(request.getSession(), subjects.resolve(authentication));
                response.setStatus(204);
            });
        login.setAuthenticationFailureHandler((request, response, error) -> {
                denials.registrar(request);
                Denegaciones.json(response, 401, "Correo o contraseña incorrectos.");
            });
        http.with(new LoginConfigurer(login), configurer -> {});
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
            .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SESSION")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, error) -> denials.responder(request, response, 401))
            .accessDeniedHandler((request, response, error) -> denials.responder(request, response, 403)));
        http.requestCache(cache -> cache.disable());
        return http.build();
    }

    /** Configure after Spring has built its shared manager and composite session strategy. */
    private static final class LoginConfigurer extends AbstractHttpConfigurer<LoginConfigurer, HttpSecurity> {
        private final JsonLoginAuthenticationFilter login;
        private LoginConfigurer(JsonLoginAuthenticationFilter login) { this.login = login; }
        @Override public void configure(HttpSecurity http) {
            login.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
            var sessions = http.getSharedObject(SessionAuthenticationStrategy.class);
            if (sessions == null) throw new IllegalStateException("Missing Spring session authentication strategy");
            login.setSessionAuthenticationStrategy(sessions);
            http.addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        }
    }
}
