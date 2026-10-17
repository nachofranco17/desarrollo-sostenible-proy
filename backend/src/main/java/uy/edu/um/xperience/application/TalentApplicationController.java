package uy.edu.um.xperience.application;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

@RestController
@RequestMapping("/api")
public class TalentApplicationController {
    private final ApplicationService applications;
    public TalentApplicationController(ApplicationService applications) { this.applications = applications; }

    @RequiereAccion("postulacion.crear")
    @PostMapping("/offers/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView.Own apply(@PathVariable UUID id, @RequestBody(required = false) ApplicationInput input,
                                     HttpServletRequest request) {
        ApplicationQuery.requireEmpty(request.getParameterMap());
        return applications.apply(id);
    }

    @RequiereAccion("postulacion.ver")
    @GetMapping("/applications/me")
    public List<ApplicationView.Own> own(HttpServletRequest request) {
        ApplicationQuery.requireEmpty(request.getParameterMap());
        return applications.ownList();
    }
}
