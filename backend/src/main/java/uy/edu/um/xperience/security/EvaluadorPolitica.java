package uy.edu.um.xperience.security;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
public interface EvaluadorPolitica {
    boolean puedeInvocar(Sujeto sujeto, String accion);
    boolean autorizar(Sujeto sujeto, String accion, Object recurso);
    <T> Specification<T> filtrar(Sujeto sujeto, String accion, Class<T> tipo);
    Set<String> camposLegibles(Sujeto sujeto, String accion, Object recurso);
    Set<String> camposEscribibles(Sujeto sujeto, String accion, Object recurso);
}
