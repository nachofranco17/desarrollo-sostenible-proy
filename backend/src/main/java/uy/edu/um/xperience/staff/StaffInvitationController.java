package uy.edu.um.xperience.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.account.RegisterRequest;
import uy.edu.um.xperience.security.*;

@RestController
@RequestMapping("/api")
public class StaffInvitationController {
    private final StaffInvitationService invitations;
    private final Sujetos subjects;
    public StaffInvitationController(StaffInvitationService invitations, Sujetos subjects) {
        this.invitations = invitations; this.subjects = subjects;
    }
    public record InviteRequest(@NotBlank @Email @Size(max = 254) String correo) {
        public InviteRequest { correo = RegisterRequest.normalizeEmail(correo); }
    }
    public record AcceptRequest(@NotBlank @Size(max = 80) String nombre,
            @NotBlank @Size(max = 80) String apellido,
            @NotBlank @Size(min = 12, max = 128) String password) {
        public AcceptRequest {
            nombre = nombre == null ? null : nombre.strip();
            apellido = apellido == null ? null : apellido.strip();
        }
        @Override public String toString() { return "AcceptRequest[redacted]"; }
    }
    @GetMapping("/staff") @RequiereAccion("staff.ver")
    public List<StaffInvitationService.MemberView> list() { return invitations.list(subjects.current()); }

    @PostMapping("/staff/invitations") @RequiereAccion("staff.invitar") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> invite(@Valid @RequestBody InviteRequest input) {
        try { invitations.invite(subjects.current(), input.correo()); }
        catch (DataIntegrityViolationException error) {
            // Concurrent registration/invitation must not disclose an existing email.
            if (!(error.getMostSpecificCause() instanceof java.sql.SQLException sql) || !"23505".equals(sql.getSQLState())) throw error;
        }
        return Map.of("message", "Si el correo puede recibir una invitación, se enviará un enlace para aceptarla.");
    }
    @PostMapping("/invitations/accept") @RequiereAccion("invitacion.aceptar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestHeader(InvitationTokens.HEADER) String token, @Valid @RequestBody AcceptRequest input) {
        invitations.accept(token, input);
    }
}
