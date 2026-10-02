package uy.edu.um.xperience.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.persistence.*;

/** Capability used only for invitacion.aceptar; never grants a session or a role. */
@Component
public class InvitationTokens {
    public static final String HEADER = "X-Invitation-Token";
    private static final Logger log = LoggerFactory.getLogger(InvitationTokens.class);
    private final Membresias memberships;
    private final SecureRandom random = new SecureRandom();
    public InvitationTokens(Membresias memberships) { this.memberships = memberships; }
    public String create() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public Specification<Membresia> filter(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return (root, query, cb) -> cb.disjunction();
        String digest = hash(token);
        Instant now = Instant.now();
        return (root, query, cb) -> cb.and(cb.equal(root.get("invitacionHash"), digest),
            cb.greaterThan(root.get("invitacionExpira"), now), cb.equal(root.get("estado"), "PENDIENTE"),
            cb.isNull(root.get("rol")), cb.isTrue(root.get("usuario").get("activo")),
            cb.equal(root.get("usuario").get("tipoCuenta"), "STAFF"));
    }
    @Transactional(readOnly = true)
    public boolean valid(String token) {
        try { return memberships.exists(filter(token)); }
        catch (RuntimeException error) {
            // Never log the token itself.
            log.error("Fallo la validacion de un token de invitacion; se deniega", error);
            return false;
        }
    }
}
