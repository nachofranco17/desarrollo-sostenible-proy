package uy.edu.um.xperience.application;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.profile.ApplicantProfileView;
import uy.edu.um.xperience.security.RequiereAccion;

@RestController
@RequestMapping("/api/company/offers/{id}/applications")
public class CompanyApplicationController {
    private final ApplicationService applications;
    public CompanyApplicationController(ApplicationService applications) { this.applications = applications; }

    @RequiereAccion("postulacion.ver_listado")
    @GetMapping
    public List<ApplicationView.Applicant> list(@PathVariable UUID id, HttpServletRequest request) {
        return applications.companyList(id, ApplicationQuery.parse(request.getParameterMap()));
    }

    @RequiereAccion("perfil.ver_postulante")
    @GetMapping("/{applicationId}/profile")
    public ApplicantProfileView profile(@PathVariable UUID id, @PathVariable UUID applicationId,
                                        HttpServletRequest request) {
        ApplicationQuery.requireEmpty(request.getParameterMap());
        return applications.companyProfile(id, applicationId);
    }

    @RequiereAccion("curriculum.descargar")
    @GetMapping("/{applicationId}/curriculum")
    public ResponseEntity<Resource> curriculum(@PathVariable UUID id, @PathVariable UUID applicationId,
                                               HttpServletRequest request) {
        ApplicationQuery.requireEmpty(request.getParameterMap());
        var download = applications.companyCurriculum(id, applicationId);
        var cv = download.curriculum();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(cv.tamanioBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(cv.nombreOriginal(), StandardCharsets.UTF_8).build().toString())
            .cacheControl(CacheControl.noStore()).body(download.file());
    }
}
