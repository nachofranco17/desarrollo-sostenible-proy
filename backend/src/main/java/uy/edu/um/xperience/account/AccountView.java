package uy.edu.um.xperience.account;

import java.util.UUID;

public record AccountView(UUID id, String correo, String nombre, String apellido,
                          String tipoCuenta, String rol, UUID empresaId, String empresaNombre) {
    public static AccountView from(Account a) {
        return new AccountView(a.id(), a.correo(), a.nombre(), a.apellido(),
            a.tipoCuenta(), a.effectiveRole(), a.empresaId(), a.empresaNombre());
    }
}
