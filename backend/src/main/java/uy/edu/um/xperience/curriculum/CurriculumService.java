package uy.edu.um.xperience.curriculum;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.account.Account;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.course.FileStorage;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.ResourceAccess;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

@Service
public class CurriculumService {
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private final CurriculumRepository curriculums;
    private final AccountRepository accounts;
    private final FileStorage storage;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public CurriculumService(CurriculumRepository curriculums, AccountRepository accounts,
                             FileStorage storage, Sujetos subjects, EvaluadorPolitica policy) {
        this.curriculums = curriculums;
        this.accounts = accounts;
        this.storage = storage;
        this.subjects = subjects;
        this.policy = policy;
    }

    public record Download(Curriculum curriculum, Resource file) {}

    @Transactional
    public CurriculumView uploadOwn(MultipartFile file) {
        Sujeto subject = ownTalent("curriculum.subir");
        if (file == null || file.isEmpty()) {
            throw invalid("El archivo está vacío.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("El currículum no puede superar los 5 MiB.");
        }
        if (!curriculums.lockProfile(subject.usuarioId())) {
            throw denied();
        }

        String key;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(PDF_SIGNATURE.length);
            if (!Arrays.equals(in.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE)) {
                throw invalid("Tipo de archivo no admitido. El currículum debe ser PDF.");
            }
            in.reset();
            key = storage.save(in);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", error);
        }

        // Solo se limpia la versión recién creada si falla la transacción; las anteriores se conservan.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
        Curriculum curriculum = new Curriculum(UUID.randomUUID(), subject.usuarioId(),
            safeFileName(file.getOriginalFilename()), "application/pdf", file.getSize(), key,
            OffsetDateTime.now(ZoneOffset.UTC));
        curriculums.insert(curriculum);
        curriculums.setCurrent(subject.usuarioId(), curriculum.id());
        return CurriculumView.from(curriculum);
    }

    @Transactional(readOnly = true)
    public Download downloadOwn() {
        Sujeto subject = ownTalent("curriculum.descargar");
        Curriculum curriculum = curriculums.current(subject.usuarioId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "No tenés un currículum actual."));
        if (!policy.autorizar(subject, "curriculum.descargar", curriculum)) {
            throw denied();
        }
        return new Download(curriculum, storage.open(curriculum.claveAlmacen()));
    }

    private Sujeto ownTalent(String action) {
        Sujeto subject = subjects.current();
        if (subject.usuarioId() == null || subject.tipoCuenta() != Sujeto.TipoCuenta.TALENTO) {
            throw denied();
        }
        accounts.byId(subject.usuarioId()).filter(Account::canSignIn)
            .filter(account -> "TALENTO".equals(account.tipoCuenta())).orElseThrow(CurriculumService::denied);
        if (!policy.autorizar(subject, action, ResourceAccess.own(subject.usuarioId()))) {
            throw denied();
        }
        return subject;
    }

    /** El nombre no interviene en la ruta y se sirve codificado como attachment. */
    static String safeFileName(String original) {
        if (original == null) {
            return "curriculum.pdf";
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1)
            .replaceAll("[^\\p{L}\\p{N} ._-]", "").strip().replaceFirst("^\\.+", "");
        if (name.isBlank()) {
            return "curriculum.pdf";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            name += ".pdf";
        }
        return name.length() <= 255 ? name : name.substring(0, 251) + ".pdf";
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
