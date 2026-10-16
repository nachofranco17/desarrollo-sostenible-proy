package uy.edu.um.xperience.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Adapts mutable credentials to Spring's existing user/account checks.
 * DaoAuthenticationProvider cannot be used here: its private timing path and its
 * success path call credentials.toString(), even with a CharSequence encoder.
 */
public final class CharArrayAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {
    private final UserDetailsService users;
    private final PasswordEncoder passwords;
    private final String userNotFoundHash;

    public CharArrayAuthenticationProvider(UserDetailsService users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        char[] dummy = new char[32];
        try { userNotFoundHash = passwords.encode(Passwords.view(dummy)); }
        finally { Passwords.clear(dummy); }
    }

    @Override protected UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken token) {
        try {
            UserDetails user = users.loadUserByUsername(username);
            if (user == null) throw new InternalAuthenticationServiceException("User lookup returned no result");
            return user;
        } catch (UsernameNotFoundException missing) {
            // Preserve the expensive hash check for absent users, as in DaoAuthenticationProvider.
            passwords.matches(Passwords.view(credentials(token)), userNotFoundHash);
            throw missing; // The Spring base class hides this as BadCredentialsException by default.
        } catch (InternalAuthenticationServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new InternalAuthenticationServiceException("User lookup failed", error);
        }
    }

    @Override protected void additionalAuthenticationChecks(UserDetails user, UsernamePasswordAuthenticationToken token) {
        if (!passwords.matches(Passwords.view(credentials(token)), user.getPassword())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
    }

    private static char[] credentials(UsernamePasswordAuthenticationToken token) {
        if (token.getCredentials() instanceof char[] password) return password;
        throw new BadCredentialsException("Credenciales inválidas");
    }
}
