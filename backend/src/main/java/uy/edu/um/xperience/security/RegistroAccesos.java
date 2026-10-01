package uy.edu.um.xperience.security;
public interface RegistroAccesos {
    void denegado(Sujeto sujeto, String accion, String recurso);
}
