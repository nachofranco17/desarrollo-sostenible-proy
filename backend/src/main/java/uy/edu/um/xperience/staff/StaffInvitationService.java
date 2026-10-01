package uy.edu.um.xperience.staff;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.xperience.account.AccountRepository;
import uy.edu.um.xperience.persistence.*;
import uy.edu.um.xperience.security.*;

@Service
public class StaffInvitationService {
    private final AccountRepository accounts;
    private final Membresias memberships;
    private final EvaluadorPolitica policy;
    private final InvitationTokens tokens;
    private final InvitationDelivery delivery;
    private final PasswordEncoder passwords;
    public StaffInvitationService(AccountRepository accounts, Membresias memberships, EvaluadorPolitica policy,
            InvitationTokens tokens, InvitationDelivery delivery, PasswordEncoder passwords) {
        this.accounts = accounts; this.memberships = memberships; this.policy = policy;
        this.tokens = tokens; this.delivery = delivery; this.passwords = passwords;
    }
    @Transactional
    public void invite(Sujeto subject, String email) {
        if (!policy.camposEscribibles(subject, "staff.invitar", ResourceAccess.company(subject.empresaId())).contains("correo")) {
            throw denied();
        }
        // Identity lookup is internal: existing accounts are never reassigned to a company.
        var existing = accounts.byEmail(email);
        Membresia member;
        if (existing.isPresent()) {
            member = memberships.findOne(policy.filtrar(subject, "staff.ver", Membresia.class)
                .and((root, query, cb) -> cb.and(cb.equal(root.get("usuarioId"), existing.get().id()),
                    cb.equal(root.get("estado"), "PENDIENTE"), cb.isNull(root.get("rol")))))
                .orElse(null);
            if (member == null) return;
        } else {
            UUID id = accounts.create(email, "", "", passwords.encode(tokens.create()), "STAFF");
            member = new Membresia(); member.usuarioId = id; member.empresaId = subject.empresaId();
            member.estado = "PENDIENTE";
        }
        String token = tokens.create();
        member.invitacionHash = InvitationTokens.hash(token);
        member.invitacionExpira = Instant.now().plus(48, ChronoUnit.HOURS);
        memberships.saveAndFlush(member);
        delivery.send(email, token);
    }
    @Transactional
    public void accept(String token, StaffInvitationController.AcceptRequest input) {
        // Lock and recheck: the PEP check alone cannot prevent simultaneous token reuse.
        var member = memberships.findOne(tokens.filter(token)).orElseThrow(StaffInvitationService::denied);
        var user = member.usuario;
        user.nombre = input.nombre(); user.apellido = input.apellido();
        user.passwordHash = passwords.encode(input.password());
        member.estado = "ACTIVA";
        member.rol = null;
        member.invitacionHash = null; member.invitacionExpira = null;
        memberships.flush();
    }
    public record MemberView(UUID usuarioId, UUID empresaId, String correo, String nombre, String apellido, String rol, String estado) {}
    @Transactional(readOnly = true)
    public List<MemberView> list(Sujeto subject) {
        return memberships.findAll(policy.filtrar(subject, "staff.ver", Membresia.class)).stream().map(member -> {
            if (!policy.camposLegibles(subject, "staff.ver", member)
                    .containsAll(Set.of("usuarioId", "empresaId", "correo", "nombre", "apellido", "rol", "estado"))) throw denied();
            return new MemberView(member.usuarioId, member.empresaId, member.usuario.correo,
                member.usuario.nombre, member.usuario.apellido, member.rol, member.estado);
        }).toList();
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Acceso denegado"); }
}
