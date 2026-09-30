package uy.edu.um.xperience.profile;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProfileValidationException extends ResponseStatusException {
    public ProfileValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
