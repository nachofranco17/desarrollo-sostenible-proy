package uy.edu.um.xperience.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.*;

@RestController
@RequestMapping("/api/staff")
public class StaffRoleController {
    private final StaffRoleService roles;
    private final Sujetos subjects;
    public StaffRoleController(StaffRoleService roles, Sujetos subjects) { this.roles = roles; this.subjects = subjects; }
    public record AssignRoleRequest(@NotNull StaffRole rol) {}
    @PatchMapping("/{memberId}/rol")
    @RequiereAccion("staff.cambiar_rol")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID memberId, @Valid @RequestBody AssignRoleRequest input) {
        roles.assign(subjects.current(), memberId, input.rol());
    }
}