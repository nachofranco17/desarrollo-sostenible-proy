package uy.edu.um.xperience.course;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.Principal;
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
import uy.edu.um.xperience.account.Account;
import uy.edu.um.xperience.account.AccountRepository;

/**
 * Gestión de cursos y proyectos por la empresa (RF5). El permiso de la acción ya lo verificó
 * SecurityConfiguration (R9); acá se resuelve la instancia: todo se busca dentro de la empresa de
 * la cuenta de la sesión, releída en cada solicitud, y lo ajeno se trata igual que lo inexistente.
 */
@Service
public class CourseService {
    static final int MAX_ELEMENTOS = 100;
    static final int MAX_MATERIALES = 20;
    private static final int MAX_NOMBRE_ARCHIVO = 255;

    private final AccountRepository accounts;
    private final CourseRepository courses;
    private final PasswordEncoder passwords;
    private final FileStorage storage;

    public CourseService(AccountRepository accounts, CourseRepository courses, PasswordEncoder passwords,
                         FileStorage storage) {
        this.accounts = accounts;
        this.courses = courses;
        this.passwords = passwords;
        this.storage = storage;
    }

    public record Download(CourseRepository.StoredMaterial material, Resource file) {}

    private record Staff(UUID usuarioId, UUID empresaId) {}

    @Transactional(readOnly = true)
    public List<CourseView.Summary> list(Principal principal) {
        return courses.list(staff(principal).empresaId());
    }

    @Transactional(readOnly = true)
    public CourseView.Detail detail(Principal principal, UUID id) {
        Staff staff = staff(principal);
        return detail(owned(staff, id, false), staff);
    }

    @Transactional
    public CourseView.Detail create(Principal principal, CourseInput.Create input) {
        Staff staff = staff(principal);
        UUID id = courses.create(staff.empresaId(), staff.usuarioId(), input.tipo(), input.data());
        return detail(owned(staff, id, false), staff);
    }

    @Transactional
    public CourseView.Detail update(Principal principal, UUID id, CourseInput.Data input) {
        Staff staff = staff(principal);
        requireModifiable(owned(staff, id, true));
        courses.update(id, staff.empresaId(), input);
        return reload(staff, id);
    }

    @Transactional
    public CourseView.Detail publish(Principal principal, UUID id) {
        Staff staff = staff(principal);
        Course course = owned(staff, id, true);
        if (course.isPublicado()) {
            throw conflict("El contenido ya está publicado.");
        }
        if (course.isCurso() && courses.countLessons(id) == 0) {
            throw conflict("El curso necesita al menos una lección para publicarse.");
        }
        if (!course.isCurso() && courses.countDeliverables(id) == 0) {
            throw conflict("El proyecto necesita al menos un entregable para publicarse.");
        }
        courses.changeState(id, staff.empresaId(), Course.PUBLICADO);
        return reload(staff, id);
    }

    /** Deja de admitir inscripciones; quienes ya estaban inscriptos conservan el acceso. */
    @Transactional
    public CourseView.Detail unpublish(Principal principal, UUID id) {
        Staff staff = staff(principal);
        if (!owned(staff, id, true).isPublicado()) {
            throw conflict("Solo se puede dar de baja un contenido publicado.");
        }
        courses.changeState(id, staff.empresaId(), Course.BAJADO);
        return reload(staff, id);
    }

    /** Solo borradores: un contenido que se publicó puede tener inscriptos. */
    @Transactional
    public void delete(Principal principal, UUID id) {
        Staff staff = staff(principal);
        if (!owned(staff, id, true).isBorrador()) {
            throw conflict("Solo se puede eliminar un borrador. Si ya se publicó, hay que darlo de baja.");
        }
        List<String> keys = courses.materials(id, staff.empresaId()).stream()
            .map(CourseRepository.StoredMaterial::claveAlmacen).toList();
        courses.delete(id, staff.empresaId());
        afterCommit(() -> keys.forEach(storage::delete));
    }

    // ---- Lecciones (sólo cursos) ----

    @Transactional
    public CourseView.Detail addLesson(Principal principal, UUID id, CourseInput.Lesson input) {
        Staff staff = staff(principal);
        Course course = owned(staff, id, true);
        if (!course.isCurso()) {
            throw conflict("Solo los cursos tienen lecciones.");
        }
        requireModifiable(course);
        if (courses.countLessons(id) >= MAX_ELEMENTOS) {
            throw conflict("Se alcanzó el máximo de lecciones.");
        }
        courses.addLesson(id, staff.empresaId(), input);
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    @Transactional
    public CourseView.Detail updateLesson(Principal principal, UUID id, UUID lessonId, CourseInput.Lesson input) {
        Staff staff = staff(principal);
        requireModifiable(owned(staff, id, true));
        if (!courses.updateLesson(lessonId, id, staff.empresaId(), input)) {
            throw denied();
        }
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    /** Solo en borrador: una vez publicado puede haber Talentos con esa lección completada. */
    @Transactional
    public CourseView.Detail deleteLesson(Principal principal, UUID id, UUID lessonId) {
        Staff staff = staff(principal);
        if (!owned(staff, id, true).isBorrador()) {
            throw conflict("Solo se pueden eliminar lecciones de un curso en borrador.");
        }
        if (!courses.deleteLesson(lessonId, id, staff.empresaId())) {
            throw denied();
        }
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    // ---- Entregables (sólo proyectos) ----

    @Transactional
    public CourseView.Detail addDeliverable(Principal principal, UUID id, CourseInput.NewDeliverable input) {
        Staff staff = staff(principal);
        Course course = owned(staff, id, true);
        if (course.isCurso()) {
            throw conflict("Solo los proyectos tienen entregables.");
        }
        requireModifiable(course);
        if (courses.countDeliverables(id) >= MAX_ELEMENTOS) {
            throw conflict("Se alcanzó el máximo de entregables.");
        }
        UUID deliverable = courses.addDeliverable(id, staff.empresaId(), input.titulo(), input.consigna(), input.pista());
        courses.replaceAnswers(deliverable, hashAnswers(input.respuestasAceptadas()));
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    @Transactional
    public CourseView.Detail updateDeliverable(Principal principal, UUID id, UUID deliverableId,
                                               CourseInput.DeliverableChange input) {
        Staff staff = staff(principal);
        requireModifiable(owned(staff, id, true));
        if (!courses.updateDeliverable(deliverableId, id, staff.empresaId(), input.titulo(), input.consigna(), input.pista())) {
            throw denied();
        }
        if (input.respuestasAceptadas() != null) {
            courses.replaceAnswers(deliverableId, hashAnswers(input.respuestasAceptadas()));
        }
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    @Transactional
    public CourseView.Detail deleteDeliverable(Principal principal, UUID id, UUID deliverableId) {
        Staff staff = staff(principal);
        if (!owned(staff, id, true).isBorrador()) {
            throw conflict("Solo se pueden eliminar entregables de un proyecto en borrador.");
        }
        if (!courses.deleteDeliverable(deliverableId, id, staff.empresaId())) {
            throw denied();
        }
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    // ---- Material ----

    @Transactional
    public CourseView.Detail uploadMaterial(Principal principal, UUID id, MultipartFile file) {
        Staff staff = staff(principal);
        requireModifiable(owned(staff, id, true));
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
        courses.addMaterial(id, staff.empresaId(), cleanFileName(file.getOriginalFilename()), type.mime(),
            file.getSize(), key, staff.usuarioId());
        courses.touch(id, staff.empresaId());
        return reload(staff, id);
    }

    @Transactional(readOnly = true)
    public Download download(Principal principal, UUID id, UUID materialId) {
        Staff staff = staff(principal);
        owned(staff, id, false);
        var material = courses.material(materialId, id, staff.empresaId()).orElseThrow(CourseService::denied);
        return new Download(material, storage.open(material.claveAlmacen()));
    }

    @Transactional
    public CourseView.Detail deleteMaterial(Principal principal, UUID id, UUID materialId) {
        Staff staff = staff(principal);
        requireModifiable(owned(staff, id, true));
        var material = courses.material(materialId, id, staff.empresaId()).orElseThrow(CourseService::denied);
        courses.deleteMaterial(materialId, id, staff.empresaId());
        courses.touch(id, staff.empresaId());
        afterCommit(() -> storage.delete(material.claveAlmacen()));
        return reload(staff, id);
    }

    // ---- Apoyo ----

    private Staff staff(Principal principal) {
        Account account = accounts.byId(UUID.fromString(principal.getName()))
            .filter(Account::canSignIn)
            .filter(a -> a.empresaId() != null)
            .orElseThrow(CourseService::denied);
        return new Staff(account.id(), account.empresaId());
    }

    private Course owned(Staff staff, UUID id, boolean forUpdate) {
        var course = forUpdate ? courses.findForUpdate(id, staff.empresaId()) : courses.find(id, staff.empresaId());
        return course.orElseThrow(CourseService::denied);
    }

    private CourseView.Detail reload(Staff staff, UUID id) {
        return detail(owned(staff, id, false), staff);
    }

    private CourseView.Detail detail(Course course, Staff staff) {
        return CourseView.Detail.of(course,
            courses.lessons(course.id(), staff.empresaId()),
            courses.deliverables(course.id(), staff.empresaId()),
            courses.materials(course.id(), staff.empresaId()).stream().map(CourseRepository.StoredMaterial::view).toList());
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
