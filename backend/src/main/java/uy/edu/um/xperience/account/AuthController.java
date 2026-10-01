package uy.edu.um.xperience.account;

import jakarta.validation.Valid;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    public static final String REGISTRATION_MESSAGE =
        "Solicitud recibida. Si los datos permiten crear una cuenta, podrás iniciar sesión con las credenciales indicadas.";
    private final RegistrationService registration;
    private final ProtectedAccounts accounts;
    private final Sujetos subjects;
    public AuthController(RegistrationService registration, ProtectedAccounts accounts, Sujetos subjects) {
        this.registration = registration; this.accounts = accounts; this.subjects = subjects;
    }
    @PostMapping("/auth/register")
    @RequiereAccion("cuenta.registrar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest input) {
        try { registration.register(subjects.current(), input); }
        catch (DataIntegrityViolationException error) {
            // The service transaction has rolled back. Only a unique-key collision is a generic duplicate.
            boolean duplicate = false;
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) duplicate = true;
            }
            if (!duplicate) throw error;
        }
        return Map.of("message", REGISTRATION_MESSAGE);
    }
    @GetMapping("/account")
    @RequiereAccion("cuenta.gestionar")
    public AccountView current() { return accounts.view(subjects.current()); }
}