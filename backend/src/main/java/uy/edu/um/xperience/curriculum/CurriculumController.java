package uy.edu.um.xperience.curriculum;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import uy.edu.um.xperience.security.RequiereAccion;

@RestController
@RequestMapping("/api/profile/me/curriculum")
public class CurriculumController {
    private final CurriculumService curriculums;

    public CurriculumController(CurriculumService curriculums) {
        this.curriculums = curriculums;
    }

    @RequiereAccion("curriculum.subir")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CurriculumView upload(MultipartHttpServletRequest request) {
        return curriculums.uploadOwn(CurriculumInput.file(request));
    }

    @RequiereAccion("curriculum.descargar")
    @GetMapping
    public ResponseEntity<Resource> download(HttpServletRequest request) {
        CurriculumInput.requireNoParameters(request);
        var download = curriculums.downloadOwn();
        var curriculum = download.curriculum();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(curriculum.tamanioBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(curriculum.nombreOriginal(), StandardCharsets.UTF_8).build().toString())
            .cacheControl(CacheControl.noStore())
            .body(download.file());
    }
}
