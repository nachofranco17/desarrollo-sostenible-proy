package uy.edu.um.xperience.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JsonLoginSecurityTest {
    private static final String FIXTURE = "  clave ñ con espacios 🔐  ";
    private static final String LEGACY_HASH = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(FIXTURE);
    private final ObjectMapper json = new ObjectMapper();
    private final CharArrayPbkdf2PasswordEncoder passwords = spy(new CharArrayPbkdf2PasswordEncoder());

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void jsonLoginUsesMutableCredentialsAndSpringLifecycleWithAnExistingHash() throws Exception {
        var provider = new CharArrayAuthenticationProvider(email -> enabledUser(), passwords);
        var manager = new ProviderManager(provider);
        AtomicReference<char[]> raw = new AtomicReference<>();
        AtomicReference<Authentication> attempt = new AtomicReference<>();
        var strategy = mock(SessionAuthenticationStrategy.class);
        AuthenticationManager observedManager = token -> {
            raw.set((char[]) token.getCredentials());
            attempt.set(token);
            return manager.authenticate(token);
        };
        var request = request(json.writeValueAsString(Map.of("correo", "ana@example.test", "password", FIXTURE)));
        var response = run(request, observedManager, strategy);
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(raw.get()).containsOnly('\0');
        assertThat(attempt.get().getCredentials()).isNull();
        var context = (SecurityContext) request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().isAuthenticated()).isTrue();
        assertThat(context.getAuthentication().getCredentials()).isNull();
        verify(strategy).onAuthentication(any(), same(request), same(response));
        verify(passwords).matches(any(CharSequence.class), eq(LEGACY_HASH));
    }

    @ParameterizedTest @ValueSource(strings = {"wrong", "missing", "disabled", "backend-error"})
    void failedAuthenticationAlsoErasesArraysAndTokenReferences(String mode) throws Exception {
        UserDetailsService users = email -> {
            if (mode.equals("missing")) throw new UsernameNotFoundException("Missing");
            if (mode.equals("backend-error")) throw new IllegalStateException("Database unavailable");
            if (mode.equals("disabled")) return User.withUsername("user-id").password(LEGACY_HASH).authorities("SESSION").disabled(true).build();
            return enabledUser();
        };
        var manager = new ProviderManager(new CharArrayAuthenticationProvider(users, passwords));
        AtomicReference<char[]> raw = new AtomicReference<>();
        AtomicReference<Authentication> token = new AtomicReference<>();
        var strategy = mock(SessionAuthenticationStrategy.class);
        var request = request(json.writeValueAsString(Map.of("correo", "ana@example.test", "password", "incorrecta")));
        var response = run(request, input -> {
            raw.set((char[]) input.getCredentials());
            token.set(input);
            return manager.authenticate(input);
        }, strategy);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(raw.get()).containsOnly('\0');
        assertThat(token.get().getCredentials()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(strategy);
        if (mode.equals("missing")) {
            // A genuine PBKDF2 check runs for absent accounts instead of a quick early return.
            verify(passwords).matches(any(CharSequence.class), argThat(hash -> hash != null && hash.length() == 96));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"correo\":\"ana@example.test\",\"password\":null}",
        "{\"correo\":\"ana@example.test\",\"password\":[\"x\"]}", "{\"correo\":\"ana@example.test\",\"password\":123}",
        "{\"correo\":\"ana@example.test\",\"password\":\"    \"}",
        "{\"correo\":\"ana@example.test\",\"password\":\"a valid credential\",\"rol\":\"ADMIN\"}",
        "{\"correo\":\"ana@example.test\",\"password\":\"a valid credential\"} {}", "{\"password\":\"a valid credential\","})
    void malformedAndUnexpectedInputsNeverReachTheAuthenticationManager(String body) throws Exception {
        var manager = mock(AuthenticationManager.class);
        var response = run(request(body), manager, mock(SessionAuthenticationStrategy.class));
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(manager);
    }

    @Test void legacyFormContentIsRejectedWithoutInvokingTheServletParameterParser() throws Exception {
        var request = request("correo=ana%40example.test&password=example");
        request.setContentType("application/x-www-form-urlencoded");
        var manager = mock(AuthenticationManager.class);
        assertThat(run(request, manager, mock(SessionAuthenticationStrategy.class)).getStatus()).isEqualTo(401);
        verifyNoInteractions(manager);
    }

    @Test void springStillChecksAccountAndCredentialStatus() {
        for (String state : new String[] {"locked", "expired", "credentials-expired"}) {
            UserDetails user = User.withUsername("user-id").password(LEGACY_HASH).authorities("SESSION")
                .accountLocked(state.equals("locked")).accountExpired(state.equals("expired"))
                .credentialsExpired(state.equals("credentials-expired")).build();
            var manager = new ProviderManager(new CharArrayAuthenticationProvider(email -> user, passwords));
            char[] value = FIXTURE.toCharArray();
            try {
                var token = UsernamePasswordAuthenticationToken.unauthenticated("ana@example.test", value);
                assertThatThrownBy(() -> manager.authenticate(token)).isInstanceOf(AccountStatusException.class);
            } finally { Passwords.clear(value); }
        }
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AuthenticationManager manager,
            SessionAuthenticationStrategy strategy) throws Exception {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var filter = new JsonLoginAuthenticationFilter(json, validation.getValidator());
            filter.setAuthenticationManager(manager);
            filter.setSessionAuthenticationStrategy(strategy);
            filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
            filter.setAuthenticationSuccessHandler((req, res, auth) -> res.setStatus(204));
            filter.setAuthenticationFailureHandler((req, res, failure) -> res.setStatus(401));
            var response = new MockHttpServletResponse();
            new PasswordCleanupFilter().doFilter(request, response, (req, res) ->
                filter.doFilter(req, res, (ignoredRequest, ignoredResponse) -> { throw new AssertionError("Login was not handled"); }));
            return response;
        }
    }

    private static MockHttpServletRequest request(String body) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login") {
            @Override public String getParameter(String name) { throw new AssertionError("Servlet parameter parsing is forbidden here"); }
            @Override public Map<String, String[]> getParameterMap() { throw new AssertionError("Servlet parameter parsing is forbidden here"); }
        };
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static UserDetails enabledUser() {
        return User.withUsername("user-id").password(LEGACY_HASH).authorities("SESSION").build();
    }
}
