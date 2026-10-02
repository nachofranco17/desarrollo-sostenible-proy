package uy.edu.um.xperience.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.account.Account;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.security.EvaluadorPolitica;
import uy.edu.um.xperience.security.Sujeto;
import uy.edu.um.xperience.security.Sujetos;

/**
 * Perfil del Talento (RF2). El PEP ya comprobó perfil.gestionar a nivel de función; acá se
 * resuelve la instancia (RS11): solo el perfil del sujeto de la sesión, revalidado con el
 * evaluador. No hay búsqueda por id ajeno ni ownership tomado del body.
 */
@Service
public class ProfileService {
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final Sujetos subjects;
    private final EvaluadorPolitica policy;

    public ProfileService(AccountRepository accounts, ProfileRepository profiles, PasswordEncoder passwords,
                          Sujetos subjects, EvaluadorPolitica policy) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.passwords = passwords;
        this.subjects = subjects;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public ProfileView getOwn() {
        return ProfileView.from(requireOwnProfile(), derivedCourseProgress(), derivedCompletedProjects(),
            derivedAchievements());
    }

    @Transactional
    public ProfileView updateOwn(ProfileUpdate input) {
        Sujeto subject = subjects.current();
        TalentProfile current = requireOwnProfile(subject);
        Account account = requireActiveTalent(subject.usuarioId());

        if (input.wantsPasswordChange() && !input.hasCompletePasswordChange()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Para cambiar la contraseña enviá la actual y la nueva (12 a 128 caracteres).");
        }

        String nombre = input.nombre().orElse(current.nombre());
        String apellido = input.apellido().orElse(current.apellido());
        String correo = input.correo().orElse(current.correo());
        String telefono = input.telefono().orElse(current.telefono());
        List<String> especializaciones = input.especializaciones().orElse(current.especializaciones());
        boolean notificarNovedades = input.notificarNovedadesCursos().orElse(current.notificarNovedadesCursos());
        boolean notificarOfertas = input.notificarOfertas().orElse(current.notificarOfertas());

        if (especializaciones == null) {
            especializaciones = List.of();
        }
        // Revalidar catálogo también sobre valores ya persistidos si se reenvían vía merge.
        if (input.especializaciones().isPresent()) {
            SpecializationCatalog.requireAllValid(especializaciones);
        }

        if (input.correo().isPresent() && profiles.emailTakenByOther(correo, account.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se pudo guardar el perfil con esos datos.");
        }

        if (input.hasCompletePasswordChange()
                && input.passwordActual() instanceof FieldChange.SetValue<String> actual
                && input.passwordNueva() instanceof FieldChange.SetValue<String> nueva) {
            if (!passwords.matches(actual.value(), account.passwordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña actual no es correcta.");
            }
            profiles.updatePassword(account.id(), passwords.encode(nueva.value()));
        }

        try {
            profiles.update(account.id(), nombre, apellido, correo, telefono, especializaciones,
                notificarNovedades, notificarOfertas);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se pudo guardar el perfil con esos datos.");
        }
        return getOwn();
    }

    private TalentProfile requireOwnProfile() {
        return requireOwnProfile(subjects.current());
    }

    private TalentProfile requireOwnProfile(Sujeto subject) {
        if (subject.usuarioId() == null) {
            throw denied();
        }
        requireActiveTalent(subject.usuarioId());
        // Consulta acotada al usuario de la sesión: no se busca un perfil por id de request (RS11).
        TalentProfile profile = profiles.byUsuarioId(subject.usuarioId())
            .orElseThrow(ProfileService::denied);
        if (!policy.autorizar(subject, "perfil.gestionar", profile)) {
            throw denied();
        }
        return profile;
    }

    private Account requireActiveTalent(UUID usuarioId) {
        Account account = accounts.byId(usuarioId)
            .filter(Account::canSignIn)
            .orElseThrow(ProfileService::denied);
        if (!"TALENTO".equals(account.tipoCuenta())) {
            throw denied();
        }
        return account;
    }

    private List<ProfileView.CourseProgressItem> derivedCourseProgress() {
        return List.of();
    }

    private List<ProfileView.CompletedProjectItem> derivedCompletedProjects() {
        return List.of();
    }

    private List<ProfileView.AchievementItem> derivedAchievements() {
        return List.of();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Acceso denegado");
    }
}
