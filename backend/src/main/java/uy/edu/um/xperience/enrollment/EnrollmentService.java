package uy.edu.um.xperience.enrollment;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.account.Account;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.course.Course;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

/**
 * Inscripción y avance del Talento (RF4). El PEP ya comprobó inscripcion.crear / inscripcion.ver
 * a nivel de función; acá se resuelve la instancia (RS11): talento y curso salen de la sesión /
 * de consultas acotadas, nunca de un userId del body.
 */
@Service
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final AccountRepository accounts;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public EnrollmentService(EnrollmentRepository enrollments, AccountRepository accounts,
                             Sujetos subjects, EvaluadorPolitica policy) {
        this.enrollments = enrollments;
        this.accounts = accounts;
        this.subjects = subjects;
        this.policy = policy;
    }

    @Transactional
    public EnrollmentView.Item enroll(UUID cursoId) {
        Sujeto subject = subjects.current();
        Account talent = requireActiveTalent(subject);
        Course course = enrollments.findPublishedCourse(cursoId).orElseThrow(EnrollmentService::denied);
        if (!policy.autorizar(subject, "inscripcion.crear", course)) {
            throw denied();
        }
        if (enrollments.exists(talent.id(), cursoId)) {
            throw duplicate();
        }
        try {
            Enrollment created = enrollments.insert(talent.id(), cursoId);
            return enrollments.findOwnedItem(created.id(), talent.id()).orElseThrow(EnrollmentService::denied);
        } catch (DataIntegrityViolationException ex) {
            // Condición de carrera: el UNIQUE (talento_id, curso_id) es la garantía real.
            throw duplicate();
        }
    }

    @Transactional(readOnly = true)
    public EnrollmentView.Page listMine() {
        Sujeto subject = subjects.current();
        Account talent = requireActiveTalent(subject);
        // Listado acotado al sujeto: no hay filtro por userId de request.
        return new EnrollmentView.Page(enrollments.listOwned(talent.id()));
    }

    @Transactional(readOnly = true)
    public EnrollmentView.Item getMine(UUID id) {
        Sujeto subject = subjects.current();
        Account talent = requireActiveTalent(subject);
        Enrollment enrollment = requireOwnedEnrollment(subject, id, talent.id());
        return enrollments.findOwnedItem(enrollment.id(), talent.id()).orElseThrow(EnrollmentService::denied);
    }

    @Transactional
    public EnrollmentView.Item updateProgress(UUID id, int porcentajeAvance) {
        Sujeto subject = subjects.current();
        Account talent = requireActiveTalent(subject);
        Enrollment locked = enrollments.findOwnedForUpdate(id, talent.id()).orElse(null);
        if (locked == null) {
            // Misma denegación si no existe o es de otro Talento (R11).
            enrollments.findById(id).ifPresent(foreign -> {
                if (!policy.autorizar(subject, "inscripcion.ver", foreign)) {
                    throw denied();
                }
            });
            throw denied();
        }
        if (!policy.autorizar(subject, "inscripcion.ver", locked)) {
            throw denied();
        }
        enrollments.updateProgress(locked.id(), talent.id(), porcentajeAvance);
        return enrollments.findOwnedItem(locked.id(), talent.id()).orElseThrow(EnrollmentService::denied);
    }

    private Enrollment requireOwnedEnrollment(Sujeto subject, UUID id, UUID talentoId) {
        Enrollment owned = enrollments.findOwned(id, talentoId).orElse(null);
        if (owned == null) {
            enrollments.findById(id).ifPresent(foreign -> {
                if (!policy.autorizar(subject, "inscripcion.ver", foreign)) {
                    throw denied();
                }
            });
            throw denied();
        }
        if (!policy.autorizar(subject, "inscripcion.ver", owned)) {
            throw denied();
        }
        return owned;
    }

    private Account requireActiveTalent(Sujeto subject) {
        if (subject.usuarioId() == null) {
            throw denied();
        }
        Account account = accounts.byId(subject.usuarioId())
            .filter(Account::canSignIn)
            .orElseThrow(EnrollmentService::denied);
        if (!"TALENTO".equals(account.tipoCuenta())) {
            throw denied();
        }
        return account;
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Ya estás inscripto en este curso o proyecto.");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
