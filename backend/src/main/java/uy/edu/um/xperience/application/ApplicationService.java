package uy.edu.um.xperience.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.course.FileStorage;
import uy.edu.um.xperience.curriculum.Curriculum;
import uy.edu.um.xperience.curriculum.CurriculumRepository;
import uy.edu.um.xperience.curriculum.CurriculumService;
import uy.edu.um.xperience.offer.OfferRepository;
import uy.edu.um.xperience.profile.ApplicantProfileView;
import uy.edu.um.xperience.profile.ProfileRepository;
import uy.edu.um.xperience.security.*;

@Service
public class ApplicationService {
    private static final Set<String> PROFILE_FIELDS = Set.of("nombre", "apellido", "correo", "especializaciones");
    private final ApplicationRepository applications;
    private final OfferRepository offers;
    private final CurriculumRepository curriculums;
    private final ProfileRepository profiles;
    private final FileStorage storage;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public ApplicationService(ApplicationRepository applications, OfferRepository offers, CurriculumRepository curriculums,
                              ProfileRepository profiles, FileStorage storage, Sujetos subjects, EvaluadorPolitica policy) {
        this.applications = applications; this.offers = offers; this.curriculums = curriculums; this.profiles = profiles;
        this.storage = storage; this.subjects = subjects; this.policy = policy;
    }

    @Transactional
    public ApplicationView.Own apply(UUID offerId) {
        Sujeto subject = subjects.current();
        var offer = offers.findPublishedForUpdate(offerId).orElseThrow(ApplicationService::denied);
        require(subject, "postulacion.crear", offer);
        // Mismo bloqueo que usa reemplazar CV: el snapshot corresponde a una versión completa y consistente.
        if (!curriculums.lockProfile(subject.usuarioId())) throw denied();
        Curriculum curriculum = curriculums.current(subject.usuarioId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.CONFLICT, "Subí tu currículum antes de postularte."));
        require(subject, "curriculum.descargar", curriculum);
        if (applications.exists(offerId, subject.usuarioId())) throw duplicate();
        UUID id;
        try { id = applications.create(subject.usuarioId(), offer, curriculum.id()); }
        catch (DuplicateKeyException error) { throw duplicate(); }
        var saved = applications.own(id, subject.usuarioId()).orElseThrow(ApplicationService::denied);
        require(subject, "postulacion.ver", saved);
        return applications.ownView(id, subject.usuarioId()).orElseThrow(ApplicationService::denied);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView.Own> ownList() {
        Sujeto subject = subjects.current();
        require(subject, "postulacion.ver", ResourceAccess.own(subject.usuarioId()));
        return applications.ownList(subject.usuarioId());
    }

    @Transactional(readOnly = true)
    public List<ApplicationView.Applicant> companyList(UUID offerId, ApplicationCriteria criteria) {
        Sujeto subject = subjects.current();
        requireCompanyOffer(subject, offerId, "postulacion.ver_listado");
        var result = new ArrayList<ApplicationView.Applicant>();
        for (var application : applications.companyList(offerId, subject.empresaId(), criteria)) {
            require(subject, "postulacion.ver_listado", application);
            ApplicantProfileView profile = profile(subject, application);
            // JSON de especializaciones se compara por pertenencia exacta, nunca con LIKE sobre texto JSON.
            if (criteria.especializacion() == null || profile.especializaciones().contains(criteria.especializacion())) {
                result.add(new ApplicationView.Applicant(application.id(), application.estado(), application.fecha(), profile));
            }
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public ApplicantProfileView companyProfile(UUID offerId, UUID applicationId) {
        Sujeto subject = subjects.current();
        return profile(subject, companyApplication(subject, offerId, applicationId, "perfil.ver_postulante"));
    }

    @Transactional(readOnly = true)
    public CurriculumService.Download companyCurriculum(UUID offerId, UUID applicationId) {
        Sujeto subject = subjects.current();
        var application = companyApplication(subject, offerId, applicationId, "curriculum.descargar");
        require(subject, "curriculum.descargar", new ApplicantAccess(application));
        var curriculum = curriculums.find(application.curriculumId(), application.talentoId())
            .orElseThrow(ApplicationService::denied);
        return new CurriculumService.Download(curriculum, storage.open(curriculum.claveAlmacen()));
    }

    private JobApplication companyApplication(Sujeto subject, UUID offerId, UUID applicationId, String action) {
        // No basta conocer el id hijo ni tener otra oferta autorizada de la misma empresa.
        if (subject.empresaId() == null) throw denied();
        var application = applications.companyApplication(applicationId, offerId, subject.empresaId())
            .orElseThrow(ApplicationService::denied);
        require(subject, action, new ApplicantAccess(application));
        return application;
    }

    private ApplicantProfileView profile(Sujeto subject, JobApplication application) {
        var access = new ApplicantAccess(application);
        require(subject, "perfil.ver_postulante", access);
        if (!policy.camposLegibles(subject, "perfil.ver_postulante", access).containsAll(PROFILE_FIELDS)) throw denied();
        return ApplicantProfileView.from(profiles.byUsuarioId(application.talentoId()).orElseThrow(ApplicationService::denied));
    }

    private void requireCompanyOffer(Sujeto subject, UUID offerId, String action) {
        if (subject.empresaId() == null) throw denied();
        var offer = offers.find(offerId, subject.empresaId()).orElseThrow(ApplicationService::denied);
        require(subject, action, offer);
    }

    private void require(Sujeto subject, String action, Object resource) {
        if (!policy.autorizar(subject, action, resource)) throw denied();
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya te postulaste a esta oferta.");
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Acceso denegado"); }
}
