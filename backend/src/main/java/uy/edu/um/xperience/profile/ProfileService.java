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

@Service
public class ProfileService {
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwords;

    public ProfileService(AccountRepository accounts, ProfileRepository profiles, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public ProfileView getOwn(UUID usuarioId) {
        Account account = requireActiveTalent(usuarioId);
        TalentProfile profile = profiles.byUsuarioId(account.id())
            .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
        return ProfileView.from(profile, derivedCourseProgress(), derivedCompletedProjects(), derivedAchievements());
    }

    @Transactional
    public ProfileView updateOwn(UUID usuarioId, ProfileUpdate input) {
        Account account = requireActiveTalent(usuarioId);
        TalentProfile current = profiles.byUsuarioId(account.id())
            .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));

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
        return getOwn(account.id());
    }

    private Account requireActiveTalent(UUID usuarioId) {
        Account account = accounts.byId(usuarioId)
            .filter(Account::canSignIn)
            .orElseThrow(() -> new AccessDeniedException("Acceso denegado"));
        if (!"TALENTO".equals(account.tipoCuenta())) {
            throw new AccessDeniedException("Acceso denegado");
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
}
