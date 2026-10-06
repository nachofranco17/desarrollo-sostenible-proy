package uy.edu.um.xperience.offer;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.ResourceAccess;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

/** RS11/RS15: la empresa siempre sale de la sesión; recurso ajeno e inexistente son indistinguibles. */
@Service
public class OfferService {
    private final OfferRepository offers;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public OfferService(OfferRepository offers, Sujetos subjects, EvaluadorPolitica policy) {
        this.offers = offers;
        this.subjects = subjects;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<OfferView.Summary> listCompany() {
        Sujeto subject = companySubject();
        require(subject, "oferta.gestionar", ResourceAccess.company(subject.empresaId()));
        return offers.list(subject.empresaId());
    }

    @Transactional(readOnly = true)
    public OfferView.Detail companyDetail(UUID id) {
        Sujeto subject = companySubject();
        return detail(owned(subject, id, false));
    }

    @Transactional
    public OfferView.Detail create(OfferInput.Data input) {
        Sujeto subject = companySubject();
        require(subject, "oferta.gestionar", ResourceAccess.company(subject.empresaId()));
        validateRequirements(input.cursosRequeridos());
        UUID id = offers.create(subject.empresaId(), subject.usuarioId(), input);
        offers.replaceRequirements(id, subject.empresaId(), input.cursosRequeridos());
        return detail(owned(subject, id, false));
    }

    @Transactional
    public OfferView.Detail update(UUID id, OfferInput.Data input) {
        Sujeto subject = companySubject();
        requireDraft(owned(subject, id, true));
        // Validate all references before changing any data; the transaction also protects the replacement.
        validateRequirements(input.cursosRequeridos());
        offers.update(id, subject.empresaId(), input);
        offers.replaceRequirements(id, subject.empresaId(), input.cursosRequeridos());
        return detail(owned(subject, id, false));
    }

    @Transactional
    public OfferView.Detail publish(UUID id) {
        Sujeto subject = companySubject();
        Offer offer = owned(subject, id, true);
        requireDraft(offer);
        // A course can have been taken down since creating the draft. Recheck under locks.
        validateRequirements(offers.requirements(id, subject.empresaId()));
        offers.publish(id, subject.empresaId());
        return detail(owned(subject, id, false));
    }

    @Transactional(readOnly = true)
    public List<OfferView.Summary> listPublished() {
        require(subjects.current(), "oferta.ver", ResourceAccess.publication(true));
        return offers.listPublished();
    }

    @Transactional(readOnly = true)
    public OfferView.Detail publishedDetail(UUID id) {
        Offer offer = offers.findPublished(id).orElseThrow(OfferService::denied);
        require(subjects.current(), "oferta.ver", offer);
        return detail(offer);
    }

    private Offer owned(Sujeto subject, UUID id, boolean forUpdate) {
        Offer offer = (forUpdate ? offers.findForUpdate(id, subject.empresaId())
            : offers.find(id, subject.empresaId())).orElseThrow(OfferService::denied);
        require(subject, "oferta.gestionar", offer);
        return offer;
    }

    private OfferView.Detail detail(Offer offer) {
        return OfferView.Detail.of(offer, offers.requirements(offer.id(), offer.empresaId()));
    }

    private void validateRequirements(List<UUID> requirements) {
        if (new HashSet<>(requirements).size() != requirements.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los requisitos no pueden repetirse.");
        }
        // Consistent lock order avoids deadlocks when offers share multiple requirements.
        for (UUID courseId : requirements.stream().sorted().toList()) {
            if (!offers.isPublishedRequirementForUpdate(courseId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Todos los requisitos deben ser cursos o proyectos publicados.");
            }
        }
    }

    private Sujeto companySubject() {
        Sujeto subject = subjects.current();
        if (subject.usuarioId() == null || subject.empresaId() == null) {
            throw denied();
        }
        return subject;
    }

    private void require(Sujeto subject, String action, Object resource) {
        if (!policy.autorizar(subject, action, resource)) {
            throw denied();
        }
    }

    private static void requireDraft(Offer offer) {
        if (!offer.isBorrador()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede modificar o publicar un borrador.");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
