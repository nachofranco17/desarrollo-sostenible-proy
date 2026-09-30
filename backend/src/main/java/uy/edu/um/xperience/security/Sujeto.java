package uy.edu.um.xperience.security;
import java.util.UUID;
import uy.edu.um.xperience.account.Account;
public record Sujeto(UUID usuarioId, TipoCuenta tipoCuenta, UUID empresaId, Rol rol) {
    public enum TipoCuenta { VISITANTE, TALENTO, STAFF }
    public enum Rol { VISITANTE, TALENTO, ADMIN, RECLUTADOR, EDITOR }
    public static Sujeto visitante() { return new Sujeto(null, TipoCuenta.VISITANTE, null, Rol.VISITANTE); }
    public static Sujeto from(Account a) {
        return new Sujeto(a.id(), TipoCuenta.valueOf(a.tipoCuenta()), a.empresaId(),
            a.effectiveRole() == null ? null : Rol.valueOf(a.effectiveRole()));
    }
}
