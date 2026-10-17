package uy.edu.um.xperience.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Wire compatible with Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().
 * Uses the JCA primitive, never converts a plaintext CharSequence to String.
 */
public final class CharArrayPbkdf2PasswordEncoder implements PasswordEncoder {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 310_000;
    private final SecureRandom random = new SecureRandom();

    @Override public String encode(CharSequence rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt);
        byte[] encoded = new byte[SALT_BYTES + HASH_BYTES];
        try {
            System.arraycopy(salt, 0, encoded, 0, SALT_BYTES);
            System.arraycopy(hash, 0, encoded, SALT_BYTES, HASH_BYTES);
            return HexFormat.of().formatHex(encoded);
        } finally {
            Arrays.fill(hash, (byte) 0);
            Arrays.fill(encoded, (byte) 0);
        }
    }

    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null
                || encodedPassword.length() != 2 * (SALT_BYTES + HASH_BYTES)) return false;
        byte[] encoded;
        try { encoded = HexFormat.of().parseHex(encodedPassword); }
        catch (IllegalArgumentException invalidHash) { return false; }
        byte[] salt = Arrays.copyOf(encoded, SALT_BYTES);
        byte[] expected = Arrays.copyOfRange(encoded, SALT_BYTES, encoded.length);
        byte[] actual = null;
        try {
            actual = derive(rawPassword, salt);
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(expected, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] derive(CharSequence rawPassword, byte[] salt) {
        Objects.requireNonNull(rawPassword, "Password is required");
        char[] copy = new char[rawPassword.length()];
        PBEKeySpec spec = null;
        SecretKey key = null;
        try {
            for (int i = 0; i < copy.length; i++) copy[i] = rawPassword.charAt(i);
            spec = new PBEKeySpec(copy, salt, ITERATIONS, HASH_BYTES * 8);
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec);
            return key.getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Could not create password hash", error);
        } finally {
            Passwords.clear(copy);
            if (spec != null) spec.clearPassword();
            // Some providers implement Destroyable; others cannot erase their internal copies.
            if (key != null) {
                try { key.destroy(); }
                catch (javax.security.auth.DestroyFailedException unsupported) { /* See security documentation. */ }
            }
        }
    }
}
