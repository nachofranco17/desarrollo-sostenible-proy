package uy.edu.um.xperience.comun;

/** Datos de entrada que no superan una validación de negocio (400). El mensaje se muestra al usuario. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
