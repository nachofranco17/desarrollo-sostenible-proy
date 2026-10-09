package uy.edu.um.xperience.catalog;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.ResourceAccess;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

/**
 * Catálogo público de cursos y proyectos publicados (RF3). El PEP ya exige catalogo.ver;
 * acá se confirma el alcance PUBLICADO por instancia.
 */
@Service
public class CatalogService {
    private final CatalogRepository catalog;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public CatalogService(CatalogRepository catalog, Sujetos subjects, EvaluadorPolitica policy) {
        this.catalog = catalog;
        this.subjects = subjects;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public CatalogView.Page list(CatalogCriteria criteria) {
        Sujeto subject = subjects.current();
        requirePublished(subject, ResourceAccess.publication(true));
        return new CatalogView.Page(catalog.search(criteria), catalog.distinctTechnologies());
    }

    @Transactional(readOnly = true)
    public CatalogView.Detail detail(UUID id) {
        Sujeto subject = subjects.current();
        var course = catalog.findPublishedCourse(id).orElseThrow(CatalogService::denied);
        if (!policy.autorizar(subject, "catalogo.ver", course)) {
            throw denied();
        }
        return catalog.findPublishedDetail(id).orElseThrow(CatalogService::denied);
    }

    private void requirePublished(Sujeto subject, ResourceAccess resource) {
        if (!policy.autorizar(subject, "catalogo.ver", resource)) {
            throw denied();
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
