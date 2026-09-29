package uy.edu.um.xperience.seguridad;

public class NoAutenticadoException extends RuntimeException {

    public NoAutenticadoException() {
        super("Se requiere una sesión iniciada");
    }
}
