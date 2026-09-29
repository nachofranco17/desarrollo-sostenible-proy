package uy.edu.um.xperience.account;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 80) String apellido,
        @NotBlank @Size(min = 12, max = 128) String password) {
    public RegisterRequest {
        correo = normalizeEmail(correo);
        nombre = nombre == null ? null : nombre.strip();
        apellido = apellido == null ? null : apellido.strip();
    }

    public static String normalizeEmail(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
