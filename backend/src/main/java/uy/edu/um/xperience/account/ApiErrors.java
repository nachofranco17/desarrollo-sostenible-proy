package uy.edu.um.xperience.account;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("message",
            "Datos inválidos. Revisá los campos enviados e incluí únicamente los permitidos."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        String message = error.getReason() == null ? "No se pudo completar la solicitud." : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
    }
}
