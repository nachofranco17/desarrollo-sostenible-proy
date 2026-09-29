package uy.edu.um.xperience.comun;

public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String recurso) {
        super(recurso + " no encontrado");
    }
}
