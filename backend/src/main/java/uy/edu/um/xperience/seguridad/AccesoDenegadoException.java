package uy.edu.um.xperience.seguridad;

/** El rol del consumidor no incluye la acción pedida (403). */
public class AccesoDenegadoException extends RuntimeException {

    private final Accion accion;

    public AccesoDenegadoException(Accion accion) {
        super("Acción no permitida para el rol: " + accion);
        this.accion = accion;
    }

    public Accion getAccion() {
        return accion;
    }
}
