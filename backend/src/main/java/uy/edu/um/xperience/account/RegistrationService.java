package uy.edu.um.xperience.account;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.security.*;

@Service
public class RegistrationService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final EvaluadorPolitica policy;
    public RegistrationService(AccountRepository accounts, PasswordEncoder passwords, EvaluadorPolitica policy) {
        this.accounts = accounts; this.passwords = passwords; this.policy = policy;
    }
    @Transactional
    public void register(Sujeto subject, RegisterRequest input) {
        if (!policy.camposEscribibles(subject, "cuenta.registrar", AuthorizationService.Global.INSTANCE)
                .containsAll(Set.of("correo", "nombre", "apellido", "password"))) {
            throw new AccessDeniedException("Acceso denegado");
        }
        String hash = passwords.encode(input.password());
        if (accounts.byEmail(input.correo()).isEmpty()) {
            accounts.create(input.correo(), input.nombre(), input.apellido(), hash, "TALENTO");
        }
    }
}