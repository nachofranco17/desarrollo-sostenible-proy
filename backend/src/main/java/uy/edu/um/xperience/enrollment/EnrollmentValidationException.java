package uy.edu.um.xperience.enrollment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EnrollmentValidationException extends ResponseStatusException {
    public EnrollmentValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
