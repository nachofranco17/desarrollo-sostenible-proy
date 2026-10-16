package uy.edu.um.xperience.account;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uy.edu.um.xperience.security.NotBlankPassword;
import uy.edu.um.xperience.security.PasswordDeserializer;
import uy.edu.um.xperience.security.Passwords;

/** Single-use input. The JSON password is never requested as a String. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String correo,
        @NotBlankPassword @Size(max = 128)
        @JsonDeserialize(using = PasswordDeserializer.class) char[] password) implements AutoCloseable {
    @Override public void close() { Passwords.clear(password); }
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
