package uy.edu.um.xperience.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

/**
 * Inscripción y seguimiento de avance del Talento (RF4).
 * Acciones: inscripcion.crear (PUBLICADO) e inscripcion.ver (PROPIO).
 */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    private final EnrollmentService enrollments;

    public EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @RequiereAccion("inscripcion.crear")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentView.Item create(@RequestBody JsonNode body) {
        return enrollments.enroll(EnrollmentCreateParser.parse(body));
    }

    @RequiereAccion("inscripcion.ver")
    @GetMapping("/me")
    public EnrollmentView.Page listMine() {
        return enrollments.listMine();
    }

    @RequiereAccion("inscripcion.ver")
    @GetMapping("/{id}")
    public EnrollmentView.Item getMine(@PathVariable UUID id) {
        return enrollments.getMine(id);
    }

    @RequiereAccion("inscripcion.ver")
    @PatchMapping(value = "/{id}/progress", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EnrollmentView.Item updateProgress(@PathVariable UUID id, @RequestBody JsonNode body) {
        return enrollments.updateProgress(id, ProgressUpdateParser.parse(body));
    }
}
