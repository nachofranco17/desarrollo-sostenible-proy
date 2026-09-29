package uy.edu.um.xperience.comun;

/** La operación está autorizada pero el estado actual del objeto no la admite (409). */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
