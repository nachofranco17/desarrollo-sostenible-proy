package uy.edu.um.xperience.course;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.ResourceAccess;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

/**
 * Gestión de cursos y proyectos por la empresa (RF5). El PEP ya comprobó la acción a nivel de
 * función; acá se resuelve la instancia (RS11): toda consulta lleva la empresa del sujeto de la
 * sesión y el evaluador confirma el alcance ORG. Lo ajeno se trata igual que lo inexistente.
 * Lecciones, entregables y materiales se buscan también por curso, así que conocer su id no
 * alcanza para llegar a ellos desde otro curso.
 */
@Service
public class CourseService {
    static final int MAX_ELEMENTOS = 100;
    static final int MAX_MATERIALES = 20;
    private static final int MAX_NOMBRE_ARCHIVO = 255;

    private final CourseRepository courses;
    private final PasswordEncoder passwords;
    private final FileStorage storage;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public CourseService(CourseRepository courses, PasswordEncoder passwords, FileStorage storage,
                         Sujetos subjects, EvaluadorPolitica policy) {
        this.courses = courses;
        this.passwords = passwords;
        this.storage = storage;
        this.subjects = subjects;
        this.policy = policy;
    }

    public record Download(CourseRepository.StoredMaterial material, Resource file) {}

    @Transactional(readOnly = true)
    public List<CourseView.Summary> list() {
        Sujeto subject = staffSubject();
        requireInvoke(subject, "curso.ver_borrador", ResourceAccess.company(subject.empresaId()));
        return courses.list(subject.empresaId());
    }

    @Transactional(readOnly = true)
    public CourseView.Detail detail(UUID id) {
        Sujeto subject = staffSubject();
        return detail(owned(subject, "curso.ver_borrador", id, false), subject);
    }

    @Transactional
    public CourseView.Detail create(CourseInput.Create input) {
        Sujeto subject = staffSubject();
        requireInvoke(subject, "curso.gestionar", ResourceAccess.company(subject.empresaId()));
        UUID id = courses.create(subject.empresaId(), subject.usuarioId(), input.tipo(), input.data());
        return detail(owned(subject, "curso.gestionar", id, false), subject);
    }

    @Transactional
    public CourseView.Detail update(UUID id, CourseInput.Data input) {
        Sujeto subject = staffSubject();
        requireModifiable(owned(subject, "curso.gestionar", id, true));
        courses.update(id, subject.empresaId(), input);
        return reload(subject, id);
    }

    @Transactional
    public CourseView.Detail publish(UUID id) {
        Sujeto subject = staffSubject();
        Course course = owned(subject, "curso.gestionar", id, true);
        if (course.isPublicado()) {
            throw conflict("El contenido ya está publicado.");
        }
        if (course.isCurso() && courses.countLessons(id) == 0) {
            throw conflict("El curso necesita al menos una lección para publicarse.");
        }
        if (!course.isCurso() && courses.countDeliverables(id) == 0) {
            throw conflict("El proyecto necesita al menos un entregable para publicarse.");
        }
        courses.changeState(id, subject.empresaId(), Course.PUBLICADO);
        return reload(subject, id);
    }

    /** Deja de admitir inscripciones; quienes ya estaban inscriptos conservan el acceso. */
    @Transactional
    public CourseView.Detail unpublish(UUID id) {
        Sujeto subject = staffSubject();
        if (!owned(subject, "curso.gestionar", id, true).isPublicado()) {
            throw conflict("Solo se puede dar de baja un contenido publicado.");
        }
        courses.changeState(id, subject.empresaId(), Course.BAJADO);
        return reload(subject, id);
    }

    /** Solo borradores: un contenido que se publicó puede tener inscriptos. */
    @Transactional
    public void delete(UUID id) {
        Sujeto subject = staffSubject();
        if (!owned(subject, "curso.gestionar", id, true).isBorrador()) {
            throw conflict("Solo se puede eliminar un borrador. Si ya se publicó, hay que darlo de baja.");
        }
        List<String> keys = courses.materials(id, subject.empresaId()).stream()
            .map(CourseRepository.StoredMaterial::claveAlmacen).toList();
        courses.delete(id, subject.empresaId());
        afterCommit(() -> keys.forEach(storage::delete));
    }

    // ---- Lecciones (sólo cursos) ----

    @Transactional
    public CourseView.Detail addLesson(UUID id, CourseInput.Lesson input) {
        Sujeto subject = staffSubject();
        Course course = owned(subject, "curso.gestionar", id, true);
        if (!course.isCurso()) {
            throw conflict("Solo los cursos tienen lecciones.");
        }
        requireModifiable(course);
        if (courses.countLessons(id) >= MAX_ELEMENTOS) {
            throw conflict("Se alcanzó el máximo de lecciones.");
        }
        courses.addLesson(id, subject.empresaId(), input);
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    @Transactional
    public CourseView.Detail updateLesson(UUID id, UUID lessonId, CourseInput.Lesson input) {
        Sujeto subject = staffSubject();
        requireModifiable(owned(subject, "curso.gestionar", id, true));
        // Nested resource: lesson must belong to this course and company (RS11 indirect path).
        if (!courses.updateLesson(lessonId, id, subject.empresaId(), input)) {
            throw denied();
        }
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    /** Solo en borrador: una vez publicado puede haber Talentos con esa lección completada. */
    @Transactional
    public CourseView.Detail deleteLesson(UUID id, UUID lessonId) {
        Sujeto subject = staffSubject();
        if (!owned(subject, "curso.gestionar", id, true).isBorrador()) {
            throw conflict("Solo se pueden eliminar lecciones de un curso en borrador.");
        }
        if (!courses.deleteLesson(lessonId, id, subject.empresaId())) {
            throw denied();
        }
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    // ---- Entregables (sólo proyectos) ----

    @Transactional
    public CourseView.Detail addDeliverable(UUID id, CourseInput.NewDeliverable input) {
        Sujeto subject = staffSubject();
        Course course = owned(subject, "curso.gestionar", id, true);
        if (course.isCurso()) {
            throw conflict("Solo los proyectos tienen entregables.");
        }
        requireModifiable(course);
        if (courses.countDeliverables(id) >= MAX_ELEMENTOS) {
            throw conflict("Se alcanzó el máximo de entregables.");
        }
        UUID deliverable = courses.addDeliverable(id, subject.empresaId(), input.titulo(), input.consigna(), input.pista());
        courses.replaceAnswers(deliverable, hashAnswers(input.respuestasAceptadas()));
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    @Transactional
    public CourseView.Detail updateDeliverable(UUID id, UUID deliverableId, CourseInput.DeliverableChange input) {
        Sujeto subject = staffSubject();
        requireModifiable(owned(subject, "curso.gestionar", id, true));
        // Nested resource: deliverable must belong to this project and company (RS11).
        if (!courses.updateDeliverable(deliverableId, id, subject.empresaId(), input.titulo(), input.consigna(), input.pista())) {
            throw denied();
        }
        if (input.respuestasAceptadas() != null) {
            courses.replaceAnswers(deliverableId, hashAnswers(input.respuestasAceptadas()));
        }
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    @Transactional
    public CourseView.Detail deleteDeliverable(UUID id, UUID deliverableId) {
        Sujeto subject = staffSubject();
        if (!owned(subject, "curso.gestionar", id, true).isBorrador()) {
            throw conflict("Solo se pueden eliminar entregables de un proyecto en borrador.");
        }
        if (!courses.deleteDeliverable(deliverableId, id, subject.empresaId())) {
            throw denied();
        }
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    // ---- Material ----

    @Transactional
    public CourseView.Detail uploadMaterial(UUID id, MultipartFile file) {
        Sujeto subject = staffSubject();
        requireModifiable(owned(subject, "material.subir", id, true));
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío.");
        }
        if (courses.countMaterials(id) >= MAX_MATERIALES) {
            throw conflict("Se alcanzó el máximo de archivos para este contenido.");
        }
        String key;
        FileType type;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(FileType.HEADER_BYTES);
            byte[] header = in.readNBytes(FileType.HEADER_BYTES);
            in.reset();
            type = FileType.detect(header).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tipo de archivo no admitido. Se aceptan PDF, PNG, JPG, ZIP y MP4."));
            key = storage.save(in);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", error);
        }
        // Si la transacción no confirma, el archivo ya escrito se borra.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
        courses.addMaterial(id, subject.empresaId(), cleanFileName(file.getOriginalFilename()), type.mime(),
            file.getSize(), key, subject.usuarioId());
        courses.touch(id, subject.empresaId());
        return reload(subject, id);
    }

    @Transactional(readOnly = true)
    public Download download(UUID id, UUID materialId) {
        Sujeto subject = staffSubject();
        owned(subject, "material.descargar", id, false);
        // Nested material lookup scoped to this course + company (RS11).
        var material = courses.material(materialId, id, subject.empresaId()).orElseThrow(CourseService::denied);
        return new Download(material, storage.open(material.claveAlmacen()));
    }

    @Transactional
    public CourseView.Detail deleteMaterial(UUID id, UUID materialId) {
        Sujeto subject = staffSubject();
        requireModifiable(owned(subject, "curso.gestionar", id, true));
        var material = courses.material(materialId, id, subject.empresaId()).orElseThrow(CourseService::denied);
        courses.deleteMaterial(materialId, id, subject.empresaId());
        courses.touch(id, subject.empresaId());
        afterCommit(() -> storage.delete(material.claveAlmacen()));
        return reload(subject, id);
    }

    // ---- Apoyo ----

    private Sujeto staffSubject() {
        Sujeto subject = subjects.current();
        if (subject.usuarioId() == null || subject.empresaId() == null) {
            throw denied();
        }
        return subject;
    }

    private Course owned(Sujeto subject, String action, UUID id, boolean forUpdate) {
        // Query restricted by session company from the start (RS11 / R15).
        var course = forUpdate ? courses.findForUpdate(id, subject.empresaId()) : courses.find(id, subject.empresaId());
        Course found = course.orElseThrow(CourseService::denied);
        if (!policy.autorizar(subject, action, found)) {
            throw denied();
        }
        return found;
    }

    private void requireInvoke(Sujeto subject, String action, ResourceAccess resource) {
        if (!policy.autorizar(subject, action, resource)) {
            throw denied();
        }
    }

    private CourseView.Detail reload(Sujeto subject, UUID id) {
        return detail(owned(subject, "curso.ver_borrador", id, false), subject);
    }

    private CourseView.Detail detail(Course course, Sujeto subject) {
        return CourseView.Detail.of(course,
            courses.lessons(course.id(), subject.empresaId()),
            courses.deliverables(course.id(), subject.empresaId()),
            courses.materials(course.id(), subject.empresaId()).stream().map(CourseRepository.StoredMaterial::view).toList());
    }

    /** Mismas respuestas normalizadas cuentan una sola vez; cada una se guarda con su propia sal. */
    private List<String> hashAnswers(List<String> answers) {
        return new LinkedHashSet<>(answers.stream().map(AnswerNormalizer::normalize).toList()).stream()
            .map(passwords::encode).toList();
    }

    private static void requireModifiable(Course course) {
        if (course.isBajado()) {
            throw conflict("Un contenido dado de baja no se puede modificar.");
        }
    }

    /** Sólo el último segmento, sin caracteres de control ni comillas, acotado. */
    static String cleanFileName(String original) {
        if (original == null) {
            return "archivo";
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "archivo";
        }
        return name.length() <= MAX_NOMBRE_ARCHIVO ? name : name.substring(name.length() - MAX_NOMBRE_ARCHIVO);
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
