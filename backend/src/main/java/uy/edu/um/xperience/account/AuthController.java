package uy.edu.um.xperience.account;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    public static final String REGISTRATION_MESSAGE =
        "Solicitud recibida. Si los datos permiten crear una cuenta, podrás iniciar sesión con las credenciales indicadas.";
    private final RegistrationService registration;
    private final AccountRepository accounts;

    public AuthController(RegistrationService registration, AccountRepository accounts) {
        this.registration = registration;
        this.accounts = accounts;
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest input) {
        try {
            registration.register(input);
        } catch (DuplicateKeyException ignored) {
            // Concurrent registrations have the same response as a normal duplicate.
            // The service transaction has already rolled back before reaching this catch.
        }
        return Map.of("message", REGISTRATION_MESSAGE);
    }

    @GetMapping("/account")
    public AccountView current(Principal principal) {
        return accounts.byId(UUID.fromString(principal.getName()))
            .filter(Account::canSignIn).map(AccountView::from)
            .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
    }
}
