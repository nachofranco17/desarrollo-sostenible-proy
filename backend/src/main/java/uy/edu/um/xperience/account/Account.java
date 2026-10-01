package uy.edu.um.xperience.account;

import java.util.UUID;

// Internal model: never return the password hash from the API.
public record Account(UUID id, String correo, String nombre, String apellido,
                      String passwordHash, String tipoCuenta, boolean activo,
                      UUID empresaId, String empresaNombre, String rol, String membresiaEstado) {
    public boolean canSignIn() {
        return activo && ("TALENTO".equals(tipoCuenta)
                || ("STAFF".equals(tipoCuenta) && empresaId != null && rol != null && "ACTIVA".equals(membresiaEstado)));
    }

    public String effectiveRole() {
        return "TALENTO".equals(tipoCuenta) ? "TALENTO" : rol;
    }

    @Override
    public String toString() { return "Account[id=" + id + "]"; }
}
