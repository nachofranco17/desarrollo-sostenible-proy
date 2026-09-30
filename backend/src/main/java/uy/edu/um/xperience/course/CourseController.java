package uy.edu.um.xperience.course;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import uy.edu.um.xperience.security.RequiereAccion;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Portal de gestión de cursos y proyectos de la empresa (RF5). La acción de cada ruta se exige en
 * el punto central de autorizacion: curso.ver_borrador, curso.gestionar, material.subir y material.descargar.
 */
@RestController
@RequestMapping("/api/company/courses")
public class CourseController {
    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @RequiereAccion("curso.ver_borrador")
    @GetMapping
    public List<CourseView.Summary> list(Principal principal) {
        return courses.list(principal);
    }

    @RequiereAccion("curso.gestionar")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CourseView.Detail create(@Valid @RequestBody CourseInput.Create input, Principal principal) {
        return courses.create(principal, input);
    }

    @RequiereAccion("curso.ver_borrador")
    @GetMapping("/{id}")
    public CourseView.Detail detail(@PathVariable UUID id, Principal principal) {
        return courses.detail(principal, id);
    }

    @RequiereAccion("curso.gestionar")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseView.Detail update(@PathVariable UUID id, @Valid @RequestBody CourseInput.Data input, Principal principal) {
        return courses.update(principal, id, input);
    }

    @RequiereAccion("curso.gestionar")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Principal principal) {
        courses.delete(principal, id);
    }

    @RequiereAccion("curso.gestionar")
    @PostMapping("/{id}/publish")
    public CourseView.Detail publish(@PathVariable UUID id, Principal principal) {
        return courses.publish(principal, id);
    }

    @RequiereAccion("curso.gestionar")
    @PostMapping("/{id}/unpublish")
    public CourseView.Detail unpublish(@PathVariable UUID id, Principal principal) {
        return courses.unpublish(principal, id);
    }

    @RequiereAccion("curso.gestionar")
    @PostMapping(value = "/{id}/lessons", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseView.Detail addLesson(@PathVariable UUID id, @Valid @RequestBody CourseInput.Lesson input,
                                       Principal principal) {
        return courses.addLesson(principal, id, input);
    }

    @RequiereAccion("curso.gestionar")
    @PutMapping(value = "/{id}/lessons/{lessonId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseView.Detail updateLesson(@PathVariable UUID id, @PathVariable UUID lessonId,
                                          @Valid @RequestBody CourseInput.Lesson input, Principal principal) {
        return courses.updateLesson(principal, id, lessonId, input);
    }

    @RequiereAccion("curso.gestionar")
    @DeleteMapping("/{id}/lessons/{lessonId}")
    public CourseView.Detail deleteLesson(@PathVariable UUID id, @PathVariable UUID lessonId, Principal principal) {
        return courses.deleteLesson(principal, id, lessonId);
    }

    @RequiereAccion("curso.gestionar")
    @PostMapping(value = "/{id}/deliverables", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseView.Detail addDeliverable(@PathVariable UUID id, @Valid @RequestBody CourseInput.NewDeliverable input,
                                            Principal principal) {
        return courses.addDeliverable(principal, id, input);
    }

    @RequiereAccion("curso.gestionar")
    @PutMapping(value = "/{id}/deliverables/{deliverableId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseView.Detail updateDeliverable(@PathVariable UUID id, @PathVariable UUID deliverableId,
                                               @Valid @RequestBody CourseInput.DeliverableChange input,
                                               Principal principal) {
        return courses.updateDeliverable(principal, id, deliverableId, input);
    }

    @RequiereAccion("curso.gestionar")
    @DeleteMapping("/{id}/deliverables/{deliverableId}")
    public CourseView.Detail deleteDeliverable(@PathVariable UUID id, @PathVariable UUID deliverableId,
                                               Principal principal) {
        return courses.deleteDeliverable(principal, id, deliverableId);
    }

    @RequiereAccion("material.subir")
    @PostMapping(value = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseView.Detail uploadMaterial(@PathVariable UUID id, @RequestPart("archivo") MultipartFile file,
                                            Principal principal) {
        return courses.uploadMaterial(principal, id, file);
    }

    /** El material sale sólo por acá, nunca desde una carpeta pública ni con enlaces firmados. */
    @RequiereAccion("material.descargar")
    @GetMapping("/{id}/materials/{materialId}")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable UUID id, @PathVariable UUID materialId,
                                                     Principal principal) {
        var download = courses.download(principal, id, materialId);
        var material = download.material();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(material.tipoMime()))
            .contentLength(material.tamanioBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(material.nombre(), StandardCharsets.UTF_8).build().toString())
            .cacheControl(CacheControl.noStore())
            .body(download.file());
    }

    @RequiereAccion("curso.gestionar")
    @DeleteMapping("/{id}/materials/{materialId}")
    public CourseView.Detail deleteMaterial(@PathVariable UUID id, @PathVariable UUID materialId, Principal principal) {
        return courses.deleteMaterial(principal, id, materialId);
    }
}
