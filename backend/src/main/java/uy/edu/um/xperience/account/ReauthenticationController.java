package uy.edu.um.xperience.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.*;

@RestController
public class ReauthenticationController {
    private final Sujetos subjects;
    private final ProtectedAccounts accounts;
    private final EvaluadorPolitica policy;
    private final PasswordEncoder passwords;
    private final Reautenticacion reauthentication;
    public ReauthenticationController(Sujetos subjects, ProtectedAccounts accounts, EvaluadorPolitica policy,
                                      PasswordEncoder passwords, Reautenticacion reauthentication) {
        this.subjects = subjects; this.accounts = accounts; this.policy = policy;
        this.passwords = passwords; this.reauthentication = reauthentication;
    }
    public record Input(@NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "ReauthenticationInput[redacted]"; }
    }
    @PostMapping("/api/auth/reauthenticate")
    @RequiereAccion("sesion.reautenticar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody Input input) {
        reauthentication.clear();
        var subject = subjects.current();
        var user = accounts.read(subject, "sesion.reautenticar");
        if (!policy.camposEscribibles(subject, "sesion.reautenticar", user).contains("password")) {
            throw new AccessDeniedException("Acceso denegado");
        }
        if (!passwords.matches(input.password(), user.passwordHash)) throw new BadCredentialsException("Credenciales inválidas");
        reauthentication.confirm(subject);
    }
}
