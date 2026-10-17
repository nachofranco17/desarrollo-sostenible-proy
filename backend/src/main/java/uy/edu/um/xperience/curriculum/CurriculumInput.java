package uy.edu.um.xperience.curriculum;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.xperience.security.Denegaciones;

/** Una subida admite un único archivo; las partes o parámetros adicionales no se ignoran. */
final class CurriculumInput {
    private CurriculumInput() {}

    static MultipartFile file(MultipartHttpServletRequest request) {
        requireNoParameters(request);
        for (String field : request.getMultiFileMap().keySet()) {
            if (!"archivo".equals(field)) {
                throw forbiddenField(field);
            }
        }
        var files = request.getFiles("archivo");
        if (files.size() > 1) {
            throw forbiddenField("archivo");
        }
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enviá un archivo PDF en el campo archivo.");
        }
        return files.get(0);
    }

    static void requireNoParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw forbiddenField(request.getParameterMap().keySet().iterator().next());
        }
    }

    private static ResponseStatusException forbiddenField(String field) {
        Denegaciones.marcarCampoNoAutorizado(field);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Datos inválidos. Enviá únicamente el campo archivo.");
    }
}
