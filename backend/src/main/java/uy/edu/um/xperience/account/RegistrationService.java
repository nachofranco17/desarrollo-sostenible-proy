package uy.edu.um.xperience.account;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public RegistrationService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public void register(RegisterRequest input) {
        // Hash on both paths; duplicate email must not skip the expensive operation.
        String hash = passwords.encode(input.password());
        if (accounts.byEmail(input.correo()).isEmpty()) {
            accounts.create(input.correo(), input.nombre(), input.apellido(), hash, "TALENTO");
        }
    }
}
