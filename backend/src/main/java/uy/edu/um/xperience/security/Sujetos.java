package uy.edu.um.xperience.security;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uy.edu.um.xperience.account.AccountRepository;
@Service
public class Sujetos {
    // Session authentication stores identity only. Never cache a role or membership across requests (RS16).
    private final AccountRepository accounts;
    public Sujetos(AccountRepository accounts) { this.accounts = accounts; }
    public Sujeto current() { return resolve(SecurityContextHolder.getContext().getAuthentication()); }
    public Sujeto resolve(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) return Sujeto.visitante();
        return byId(UUID.fromString(auth.getName()));
    }
    public Sujeto byId(UUID id) {
        return accounts.byId(id).map(Sujeto::from).orElse(new Sujeto(id, null, null, null));
    }
}
